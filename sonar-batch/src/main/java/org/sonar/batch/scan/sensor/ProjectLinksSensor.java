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
package org.sonar.batch.scan.sensor;

import org.apache.commons.lang.StringUtils;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.ProjectLink;
import org.sonar.core.i18n.DefaultI18n;

import java.util.Locale;

public class ProjectLinksSensor implements Sensor {

  private Settings settings;
  private DefaultI18n defaultI18n;
  private AnalysisMode analysisMode;

  public ProjectLinksSensor(Settings settings, DefaultI18n defaultI18n, AnalysisMode analysisMode) {
    this.settings = settings;
    this.defaultI18n = defaultI18n;
    this.analysisMode = analysisMode;
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return !analysisMode.isPreview();
  }

  @Override
  public void analyse(Project project, SensorContext context) {
    handleLink(context, CoreProperties.LINKS_HOME_PAGE);
    handleLink(context, CoreProperties.LINKS_CI);
    handleLink(context, CoreProperties.LINKS_ISSUE_TRACKER);
    handleLink(context, CoreProperties.LINKS_SOURCES);
    handleLink(context, CoreProperties.LINKS_SOURCES_DEV);
  }

  private void handleLink(SensorContext context, String linkProperty) {
    String home = settings.getString(linkProperty);
    String linkType = StringUtils.substringAfterLast(linkProperty, ".");
    String name = defaultI18n.message(Locale.getDefault(), "project_links." + linkType, linkProperty);
    updateLink(context, linkType, name, home);
  }

  private void updateLink(SensorContext context, String key, String name, String url) {
    if (StringUtils.isBlank(url)) {
      context.deleteLink(key);
    } else {
      context.saveLink(new ProjectLink(key, name, url));
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
