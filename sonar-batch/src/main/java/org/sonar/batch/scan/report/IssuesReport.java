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
package org.sonar.batch.scan.report;

import com.google.common.collect.Maps;
import org.sonar.api.issue.Issue;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.RulePriority;
import org.sonar.batch.index.BatchResource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class IssuesReport {

  public static final int TOO_MANY_ISSUES_THRESHOLD = 1000;
  private String title;
  private Date date;
  private final ReportSummary summary = new ReportSummary();
  private final Map<BatchResource, ResourceReport> resourceReportsByResource = Maps.newLinkedHashMap();

  public IssuesReport() {
  }

  public ReportSummary getSummary() {
    return summary;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public Map<BatchResource, ResourceReport> getResourceReportsByResource() {
    return resourceReportsByResource;
  }

  public List<ResourceReport> getResourceReports() {
    return new ArrayList<ResourceReport>(resourceReportsByResource.values());
  }

  public List<BatchResource> getResourcesWithReport() {
    return new ArrayList<BatchResource>(resourceReportsByResource.keySet());
  }

  public void addIssueOnResource(BatchResource resource, Issue issue, Rule rule, RulePriority severity) {
    addResource(resource);
    getSummary().addIssue(issue, rule, severity);
    resourceReportsByResource.get(resource).addIssue(issue, rule, RulePriority.valueOf(issue.severity()));
  }

  public void addResolvedIssueOnResource(BatchResource resource, Issue issue, Rule rule, RulePriority severity) {
    addResource(resource);
    getSummary().addResolvedIssue(issue, rule, severity);
    resourceReportsByResource.get(resource).addResolvedIssue(issue, rule, RulePriority.valueOf(issue.severity()));
  }

  private void addResource(BatchResource resource) {
    if (!resourceReportsByResource.containsKey(resource)) {
      resourceReportsByResource.put(resource, new ResourceReport(resource));
    }
  }

}
