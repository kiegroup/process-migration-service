/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.processmigration.test.persistence.scripts;

/**
 * Represents various supported database types. Based on supported hibernate dialects.
 */
public enum DatabaseType {

    DB2("db2", "db2"),
    H2("h2", "h2"),
    MYSQL("mysql", "mysql"),
    MARIADB("mariadb", "mariadb"),
    ORACLE("oracle", "oracle"),
    POSTGRESQL("postgresql", "postgresql"),
    SQLSERVER("mssql", "mssql");

    private String scriptDatabasePrefix;
    private String scriptsFolderName;

    /**
     * Constructor.
     *
     * @param scriptsFolderName Name of folder which contains scripts for database type.
     */
    DatabaseType(String scriptDatabasePrefix, String scriptsFolderName) {
        this.scriptDatabasePrefix = scriptDatabasePrefix;
        this.scriptsFolderName = scriptsFolderName;
    }

    /**
     * Gets name of folder which contains scripts for database type.
     *
     * @return Name of folder which contains scripts for database type.
     */
    public String getScriptsFolderName() {
        return scriptsFolderName;
    }

    /**
     * Gets the database prefix name used for the scripts, based on the hibernate dialect.
     *
     * @return Database prefix name used for the script.
     */
    public String getScriptDatabasePrefix()  {
        return scriptDatabasePrefix;
    }
}
