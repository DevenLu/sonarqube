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

package org.sonar.server.source.ws;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.api.web.UserRole;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.server.component.ComponentTesting;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.db.DbClient;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.source.SourceService;
import org.sonar.server.user.MockUserSession;
import org.sonar.server.ws.WsTester;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RawActionTest {

  @Mock
  DbClient dbClient;

  @Mock
  DbSession session;

  @Mock
  ComponentDao componentDao;

  @Mock
  SourceService sourceService;

  WsTester tester;

  ComponentDto project = ComponentTesting.newProjectDto();
  ComponentDto file = ComponentTesting.newFileDto(project);

  @Before
  public void setUp() throws Exception {
    when(dbClient.componentDao()).thenReturn(componentDao);
    when(dbClient.openSession(false)).thenReturn(session);
    tester = new WsTester(new SourcesWs(mock(ShowAction.class), new RawAction(dbClient, sourceService), mock(ScmAction.class), mock(LinesAction.class),
      mock(HashAction.class), mock(IndexAction.class)));
  }

  @Test
  public void get_txt() throws Exception {
    String fileKey = "src/Foo.java";
    MockUserSession.set().addComponentPermission(UserRole.CODEVIEWER, "polop", fileKey);
    when(componentDao.getByKey(session, fileKey)).thenReturn(file);

    when(sourceService.getLinesAsTxt(file.uuid(), null, null)).thenReturn(newArrayList(
      "public class HelloWorld {",
      "}"
      ));

    WsTester.TestRequest request = tester.newGetRequest("api/sources", "raw").setParam("key", fileKey);
    String result = request.execute().outputAsString();
    assertThat(result).isEqualTo("public class HelloWorld {\n}\n");
  }

  @Test(expected = ForbiddenException.class)
  public void requires_code_viewer_permission() throws Exception {
    MockUserSession.set();
    tester.newGetRequest("api/sources", "raw").setParam("key", "any").execute();
  }
}
