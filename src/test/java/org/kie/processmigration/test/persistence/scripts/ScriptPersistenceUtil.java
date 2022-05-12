/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.processmigration.test.persistence.scripts;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.kie.processmigration.test.persistence.scripts.util.SQLCommandUtil;
import org.kie.processmigration.test.persistence.scripts.util.SQLScriptUtil;
import org.kie.processmigration.test.persistence.scripts.util.ScriptUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.processmigration.test.persistence.scripts.DatabaseType.SQLSERVER;

public class ScriptPersistenceUtil {

    private static final Logger logger = LoggerFactory.getLogger(ScriptPersistenceUtil.class);
    private final DataSource dataSource;
    private final DatabaseType databaseType;
    private final Properties dataSourceProperties;

    public ScriptPersistenceUtil(DataSource dataSource, Properties props) {
        this.dataSource = dataSource;
        this.dataSourceProperties = props;
        this.databaseType = ScriptUtil.getDatabaseType(dataSourceProperties);
    }

    public void executeScriptRunner(String resourcePath, ScriptFilter scriptFilter) throws IOException, SQLException {
        executeScripts(new File(ScriptPersistenceUtil.class.getResource(resourcePath).getFile()), scriptFilter, null);
    }

    public void executeScriptRunner(String resourcePath, ScriptFilter scriptFilter, String defaultSchema) throws IOException, SQLException {
        executeScripts(new File(ScriptPersistenceUtil.class.getResource(resourcePath).getFile()), scriptFilter, defaultSchema);
    }

    /**
     * Executes SQL scripts from specified root SQL scripts folder. Selects appropriate scripts from root folder
     * by using dialect that is defined in datasource.properties file.
     *
     * @param scriptsRootFolder Root folder containing folders with SQL scripts for all supported database systems.
     * @param scriptFilter      Indicates the filter to apply, including create/drop scripts
     * @param defaultSchema     Default database schema to be set prior to running scripts
     * @throws IOException
     */
    private void executeScripts(final File scriptsRootFolder, ScriptFilter scriptFilter, String defaultSchema) throws IOException, SQLException {
        final File[] sqlScripts = ScriptUtil.getDDLScriptFilesByDatabaseType(scriptsRootFolder, databaseType, scriptFilter);
        if (sqlScripts.length == 0 && scriptFilter.hasOption(ScriptFilter.Option.DISALLOW_EMPTY_RESULTS)) {
            throw new RuntimeException("No create sql files found for db type "
                                               + databaseType + " in folder " + scriptsRootFolder.getAbsolutePath());
        }
        final Connection connection = dataSource.getConnection();
        try {
            connection.setAutoCommit(false);
            if (defaultSchema != null && !defaultSchema.isEmpty()) {
                connection.setSchema(defaultSchema);
            }
            for (File script : sqlScripts) {
                logger.info("Executing script {}", script.getName());
                final List<String> scriptCommands = SQLScriptUtil.getCommandsFromScript(script, databaseType);
                for (String command : scriptCommands) {
                    logger.debug("query {} ", command);
                    final PreparedStatement statement = preparedStatement(connection, command);
                    executeStatement(scriptFilter.hasOption(ScriptFilter.Option.THROW_ON_SCRIPT_ERROR), statement);
                    connection.commit();
                }
            }
        } catch (SQLException ex) {
            connection.rollback();
            throw new RuntimeException(ex.getMessage(), ex);
        } finally {
            connection.close();
        }
    }

    private PreparedStatement preparedStatement(final Connection conn, String command) throws SQLException {
        final PreparedStatement statement;
        if (databaseType == SQLSERVER) {
            statement = conn.prepareStatement(SQLCommandUtil.preprocessCommandSqlServer(command, dataSourceProperties));
        } else {
            statement = conn.prepareStatement(command);
        }
        return statement;
    }

    private void executeStatement(boolean createFiles, final PreparedStatement statement) throws SQLException {
        try {
            statement.execute();
            statement.close();
        } catch (SQLException ex) {
            if (createFiles) {
                throw ex;
            } else //Consume exceptions for dropping files
            {
                logger.warn("Dropping statement failed: {} ", ex.getMessage());
            }
        }
    }
}
