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
package org.sonar.batch.scm;

import com.google.common.base.Joiner;
import org.picocontainer.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.AnalysisMode;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.bootstrap.ProjectReactor;
import org.sonar.api.batch.scm.ScmProvider;
import org.sonar.api.config.Settings;
import org.sonar.batch.phases.Phases;

import javax.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public final class ScmConfiguration implements BatchComponent, Startable {
  private static final Logger LOG = LoggerFactory.getLogger(ScmConfiguration.class);

  private final ProjectReactor projectReactor;
  private final Settings settings;
  private final Map<String, ScmProvider> providerPerKey = new LinkedHashMap<String, ScmProvider>();
  private final Phases phases;
  private final AnalysisMode analysisMode;

  private ScmProvider provider;

  public ScmConfiguration(ProjectReactor projectReactor, AnalysisMode analysisMode, Settings settings, @Nullable Phases phases, ScmProvider... providers) {
    this.projectReactor = projectReactor;
    this.analysisMode = analysisMode;
    this.settings = settings;
    this.phases = phases;
    for (ScmProvider scmProvider : providers) {
      providerPerKey.put(scmProvider.key(), scmProvider);
    }
  }

  // Scan 2
  public ScmConfiguration(ProjectReactor projectReactor, AnalysisMode analysisMode, Settings settings, ScmProvider... providers) {
    this(projectReactor, analysisMode, settings, null, providers);
  }

  public ScmConfiguration(ProjectReactor projectReactor, AnalysisMode analysisMode, Settings settings, Phases phases) {
    this(projectReactor, analysisMode, settings, phases, new ScmProvider[0]);
  }

  // Scan2
  public ScmConfiguration(ProjectReactor projectReactor, AnalysisMode analysisMode, Settings settings) {
    this(projectReactor, analysisMode, settings, null, new ScmProvider[0]);
  }

  @Override
  public void start() {
    if (analysisMode.isPreview() || (phases != null && !phases.isEnabled(Phases.Phase.SENSOR))) {
      return;
    }
    if (isDisabled()) {
      LOG.debug("SCM Step is disabled by configuration");
      return;
    }
    if (settings.hasKey(CoreProperties.SCM_PROVIDER_KEY)) {
      String forcedProviderKey = settings.getString(CoreProperties.SCM_PROVIDER_KEY);
      setProviderIfSupported(forcedProviderKey);
    } else {
      autodetection();
      if (this.provider == null) {
        considerOldScmUrl();
      }
      if (this.provider == null) {
        LOG.warn("SCM provider autodetection failed. No SCM provider claims to support this project. Please use " + CoreProperties.SCM_PROVIDER_KEY
          + " to define SCM of your project.");
      }
    }
  }

  private void setProviderIfSupported(String forcedProviderKey) {
    if (providerPerKey.containsKey(forcedProviderKey)) {
      this.provider = providerPerKey.get(forcedProviderKey);
    } else {
      String supportedProviders = providerPerKey.isEmpty() ? "No SCM provider installed" : "Supported SCM providers are " + Joiner.on(",").join(providerPerKey.keySet());
      throw new IllegalArgumentException("SCM provider was set to \"" + forcedProviderKey + "\" but no SCM provider found for this key. " + supportedProviders);
    }
  }

  private void considerOldScmUrl() {
    if (settings.hasKey(CoreProperties.LINKS_SOURCES_DEV)) {
      String url = settings.getString(CoreProperties.LINKS_SOURCES_DEV);
      if (url.startsWith("scm:")) {
        String[] split = url.split(":");
        if (split.length > 1) {
          setProviderIfSupported(split[1]);
        }
      }
    }

  }

  private void autodetection() {
    for (ScmProvider installedProvider : providerPerKey.values()) {
      if (installedProvider.supports(projectReactor.getRoot().getBaseDir())) {
        if (this.provider == null) {
          this.provider = installedProvider;
        } else {
          throw new IllegalStateException("SCM provider autodetection failed. Both " + this.provider.key() + " and " + installedProvider.key()
            + " claim to support this project. Please use " + CoreProperties.SCM_PROVIDER_KEY + " to define SCM of your project.");
        }
      }
    }
  }

  public ScmProvider provider() {
    return provider;
  }

  public boolean isDisabled() {
    return settings.getBoolean(CoreProperties.SCM_DISABLED_KEY);
  }

  @Override
  public void stop() {
    // Nothing to do
  }

}
