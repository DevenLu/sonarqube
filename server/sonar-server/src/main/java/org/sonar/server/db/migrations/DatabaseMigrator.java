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
package org.sonar.server.db.migrations;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.dbutils.DbUtils;
import org.apache.ibatis.session.SqlSession;
import org.picocontainer.Startable;
import org.slf4j.LoggerFactory;
import org.sonar.api.ServerComponent;
import org.sonar.api.platform.ServerUpgradeStatus;
import org.sonar.core.persistence.DdlUtils;
import org.sonar.server.db.DbClient;
import org.sonar.server.plugins.ServerPluginRepository;

import java.sql.Connection;

/**
 * Restore schema by executing DDL scripts. Only H2 database is supported.
 * Other databases are created by Ruby on Rails migrations.
 *
 * @since 2.12
 */
public class DatabaseMigrator implements ServerComponent, Startable {

  private final DbClient dbClient;
  private final DatabaseMigration[] migrations;
  private final ServerUpgradeStatus serverUpgradeStatus;

  /**
   * ServerPluginRepository is used to ensure H2 schema creation is done only after copy of bundle plugins have been done
   */
  public DatabaseMigrator(DbClient dbClient, DatabaseMigration[] migrations, ServerUpgradeStatus serverUpgradeStatus,
                          ServerPluginRepository serverPluginRepository) {
    this.dbClient = dbClient;
    this.migrations = migrations;
    this.serverUpgradeStatus = serverUpgradeStatus;
  }

  @Override
  public void start() {
    createDatabase();
  }

  @Override
  public void stop() {
    // Nothing to do
  }

  /**
   * @return true if the database has been created, false if this database is not supported or if database has already been created
   */
  @VisibleForTesting
  boolean createDatabase() {
    if (DdlUtils.supportsDialect(dbClient.database().getDialect().getId()) && serverUpgradeStatus.isFreshInstall()) {
      LoggerFactory.getLogger(getClass()).info("Create database");
      SqlSession session = dbClient.openSession(false);
      Connection connection = null;
      try {
        connection = session.getConnection();
        createSchema(connection, dbClient.database().getDialect().getId());
        return true;
      } finally {
        session.close();

        // The connection is probably already closed by session.close()
        // but it's not documented in mybatis javadoc.
        DbUtils.closeQuietly(connection);
      }
    }
    return false;
  }

  public void executeMigration(String className) {
    DatabaseMigration migration = getMigration(className);
    try {
      migration.execute();

    } catch (Exception e) {
      // duplication between log and exception because webapp does not correctly log initial stacktrace
      String msg = "Fail to execute database migration: " + className;
      LoggerFactory.getLogger(getClass()).error(msg, e);
      throw new IllegalStateException(msg, e);
    }
  }

  private DatabaseMigration getMigration(String className) {
    for (DatabaseMigration migration : migrations) {
      if (migration.getClass().getName().equals(className)) {
        return migration;
      }
    }
    throw new IllegalArgumentException("Database migration not found: " + className);
  }

  @VisibleForTesting
  protected void createSchema(Connection connection, String dialectId) {
    DdlUtils.createSchema(connection, dialectId);
  }
}
