/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.qualityprofile;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.sonar.api.ServerComponent;
import org.sonar.api.profiles.ProfileDefinition;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleParam;
import org.sonar.api.utils.ValidationMessages;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.core.preview.PreviewCache;
import org.sonar.core.qualityprofile.db.ActiveRuleDto;
import org.sonar.core.qualityprofile.db.QualityProfileKey;
import org.sonar.core.rule.RuleDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.rule.db.RuleDao;
import org.sonar.server.user.UserSession;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;

public class QProfileBackup implements ServerComponent {

  private final MyBatis myBatis;
  private final QProfileOperations qProfileOperations;
  private final QProfileActiveRuleOperations qProfileActiveRuleOperations;
  private final RuleDao ruleDao;
  private final List<ProfileDefinition> definitions;
  private final DefaultProfilesCache defaultProfilesCache;
  private final PreviewCache dryRunCache;

  public QProfileBackup(MyBatis myBatis,
                        QProfileOperations qProfileOperations, QProfileActiveRuleOperations qProfileActiveRuleOperations, RuleDao ruleDao,
                        DefaultProfilesCache defaultProfilesCache, PreviewCache dryRunCache) {
    this(myBatis, qProfileOperations, qProfileActiveRuleOperations, ruleDao,
      Collections.<ProfileDefinition>emptyList(), defaultProfilesCache, dryRunCache);
  }

  public QProfileBackup(MyBatis myBatis,
                        QProfileOperations qProfileOperations, QProfileActiveRuleOperations qProfileActiveRuleOperations, RuleDao ruleDao,
                        List<ProfileDefinition> definitions, DefaultProfilesCache defaultProfilesCache, PreviewCache dryRunCache) {
    this.myBatis = myBatis;
    this.qProfileOperations = qProfileOperations;
    this.qProfileActiveRuleOperations = qProfileActiveRuleOperations;
    this.ruleDao = ruleDao;
    this.definitions = definitions;
    this.defaultProfilesCache = defaultProfilesCache;
    this.dryRunCache = dryRunCache;
  }

  /**
   * Recreate built-in profile for a given language.
   * If a profile with same name than default profile already exists, an exception will be thrown.
   */
  public QProfileResult recreateBuiltInProfilesByLanguage(String language) {
    checkPermission(UserSession.get());
    QProfileResult result = new QProfileResult();

    DbSession session = myBatis.openSession(false);
    try {
      ListMultimap<String, RulesProfile> profilesByName = profilesByName(language, result);
      for (Map.Entry<String, Collection<RulesProfile>> entry : profilesByName.asMap().entrySet()) {
        String name = entry.getKey();
        QProfile profile = qProfileOperations.newProfile(name, language, true, UserSession.get(), session);
        for (RulesProfile currentRulesProfile : entry.getValue()) {
          restoreFromActiveRules(
            QualityProfileKey.of(name, language),
            currentRulesProfile, session);
        }
        //esActiveRule.bulkIndexProfile(profile.id(), session);
      }
      dryRunCache.reportGlobalModification(session);
      session.commit();
    } finally {
      MyBatis.closeQuietly(session);
    }
    return result;
  }

  private ListMultimap<String, RulesProfile> profilesByName(String language, QProfileResult result) {
    ListMultimap<String, RulesProfile> profilesByName = ArrayListMultimap.create();
    for (ProfileDefinition definition : definitions) {
      ValidationMessages validation = ValidationMessages.create();
      RulesProfile profile = definition.createProfile(validation);
      if (language.equals(profile.getLanguage())) {
        processValidationMessages(validation, result);
        profilesByName.put(profile.getName(), profile);
      }
    }
    return profilesByName;
  }

  public void restoreFromActiveRules(QualityProfileKey profileKey, RulesProfile rulesProfile, DbSession session) {
    for (org.sonar.api.rules.ActiveRule activeRule : rulesProfile.getActiveRules()) {
      RuleKey ruleKey = RuleKey.of(activeRule.getRepositoryKey(), activeRule.getRuleKey());
      RuleDto rule = ruleDao.getNullableByKey(session, ruleKey);
      if (rule == null) {
        throw new NotFoundException(String.format("Rule '%s' does not exists.", ruleKey));
      }

      ActiveRuleDto activeRuleDto = qProfileActiveRuleOperations.createActiveRule(profileKey, ruleKey, activeRule.getSeverity().name(), session);
      for (RuleParam param : activeRule.getRule().getParams()) {
        String paramKey = param.getKey();
        String value = activeRule.getParameter(param.getKey());
        if (value != null) {
          qProfileActiveRuleOperations.updateActiveRuleParam(activeRuleDto, paramKey, value, session);
        }
      }
    }
  }

  /**
   * Return the list of default profile names for a given language
   */
  public Collection<String> findDefaultProfileNamesByLanguage(String language) {
    return defaultProfilesCache.byLanguage(language);
  }

  private void processValidationMessages(ValidationMessages messages, QProfileResult result) {
    if (!messages.getErrors().isEmpty()) {
      List<BadRequestException.Message> errors = newArrayList();
      for (String error : messages.getErrors()) {
        errors.add(BadRequestException.Message.of(error));
      }
      throw BadRequestException.of("Fail to restore profile", errors);
    }
    result.addWarnings(messages.getWarnings());
    result.addInfos(messages.getInfos());
  }

  private void checkPermission(UserSession userSession) {
    userSession.checkLoggedIn();
    userSession.checkGlobalPermission(GlobalPermissions.QUALITY_PROFILE_ADMIN);
  }

}