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
package org.sonar.batch.protocol.output;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

public class FileStructureTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void fail_if_dir_does_not_exist() throws Exception {
    File dir = temp.newFolder();
    FileUtils.deleteQuietly(dir);
    try {
      new FileStructure(dir);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageContaining("Directory of analysis report does not exist");
    }
  }

  @Test
  public void fail_if_invalid_dir() throws Exception {
    // not a dir but a file
    File dir = temp.newFile();
    try {
      new FileStructure(dir);
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageContaining("Directory of analysis report does not exist");
    }
  }

  @Test
  public void locate_files() throws Exception {
    File dir = temp.newFolder();
    FileUtils.write(new File(dir, "metadata.pb"), "metadata content");
    FileUtils.write(new File(dir, "issues-3.pb"), "issues of component 3");
    FileUtils.write(new File(dir, "component-42.pb"), "details of component 42");

    FileStructure structure = new FileStructure(dir);
    assertThat(structure.metadataFile()).exists().isFile();
    assertThat(structure.fileFor(FileStructure.Domain.COMPONENT, 42)).exists().isFile();
    assertThat(structure.fileFor(FileStructure.Domain.ISSUES, 3)).exists().isFile();
    assertThat(structure.fileFor(FileStructure.Domain.ISSUES, 42)).doesNotExist();
  }
}
