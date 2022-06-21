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

package org.kie.processmigration.test.persistence.scripts.util;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.kie.processmigration.test.persistence.scripts.DatabaseScript;
import org.kie.processmigration.test.persistence.scripts.DatabaseType;
import org.kie.processmigration.test.persistence.scripts.ScriptFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains util methods that are used for testing SQL scripts.
 */
public final class ScriptUtil {

    private static final Logger logger = LoggerFactory.getLogger(ScriptUtil.class);

    /**
     * Gets SQL scripts for selected database type.
     * @param folderWithDDLs Root folder containing SQL scripts for all database types.
     * @param databaseType Database type.
     * @param scriptFilter Indicates the filter to apply, including springboot or not scripts and create/drop scripts
     * @return Array of SQL script files. If there are no SQL script files found, returns empty array.
     */
    public static File[] getDDLScriptFilesByDatabaseType(final File folderWithDDLs,
                                                         final DatabaseType databaseType,
                                                         final ScriptFilter scriptFilter) {
        final File folderWithScripts = new File(folderWithDDLs.getPath() + File.separator + databaseType.getScriptsFolderName());

        if (!folderWithScripts.exists()) {
            logger.warn("Folder with DDLs doesn't exist {}", folderWithDDLs);
            return new File[0];
        }

        File[] foundFiles = Arrays.asList(folderWithScripts.listFiles()).stream().filter(scriptFilter.build()).toArray(File[]::new);

        foundFiles = Arrays.stream(foundFiles).map(DatabaseScript::new).sorted().map(DatabaseScript::getScript).toArray(File[]::new);

        if (databaseType.equals(DatabaseType.POSTGRESQL)) {
            //Returns first schema sql
            Arrays.sort(foundFiles, Comparator.<File, Boolean>comparing(s -> s.getName().contains("schema")).reversed());
        }

        logger.info("Returned DDL files: {}", Arrays.stream(foundFiles).map(File::getName).collect(Collectors.toList()));
        return foundFiles;
    }

    /**
     * Gets database type based on dialect property specified in data source properties.
     * @param dataSourceProperties Data source properties.
     * @return Database type based on specified dialect property. If no dialect is specified,
     * returns H2 database type.
     */
    public static DatabaseType getDatabaseType(final Properties dataSourceProperties) {
        final String hibernateDialect = dataSourceProperties.getProperty("hibernate.dialect");
        if (!"".equals(hibernateDialect)) {
            return getDatabaseTypeBySQLDialect(hibernateDialect);
        } else {
            return DatabaseType.H2;
        }
    }

    /**
     * Gets database type based on specified SQL dialect.
     * @param sqlDialect SQL dialect.
     * @return Database type based on specified SQL dialect.
     *
     * If specified SQL dialect is not supported, throws IllegalArgumentException.
     */
    public static DatabaseType getDatabaseTypeBySQLDialect(final String sqlDialect) {
        if (containsDialect(sqlDialect, "DB2")) {
            return DatabaseType.DB2;
        } else if (containsDialect(sqlDialect, "H2")) {
            return DatabaseType.H2;
        } else if (containsDialect(sqlDialect, "MySQL")) {
            return DatabaseType.MYSQL;
        } else if (containsDialect(sqlDialect, "MariaDB")) {
            return DatabaseType.MARIADB;
        } else if (containsDialect(sqlDialect, "Oracle")) {
            return DatabaseType.ORACLE;
        } else if (containsDialect(sqlDialect, "Postgre")) {
            return DatabaseType.POSTGRESQL;
        } else if (containsDialect(sqlDialect, "SQLServer")) {
            return DatabaseType.SQLSERVER;
        } else {
            throw new IllegalArgumentException("SQL dialect type " + sqlDialect + " is not supported!");
        }
    }

    private static boolean containsDialect(String dialect, String dbType){
        String regex = "(.*)%s(.*)Dialect";
        Pattern p = Pattern.compile(String.format(regex, dbType));
        Matcher m = p.matcher(dialect);
        return m.matches();
    }

    public static byte[] hexStringToByteArray(final String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    private ScriptUtil() {
        // It makes no sense to create instances of util classes.
    }

}
