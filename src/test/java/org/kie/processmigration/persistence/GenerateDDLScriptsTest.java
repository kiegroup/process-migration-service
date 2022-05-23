/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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

package org.kie.processmigration.persistence;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.persistence.Persistence;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.engine.jdbc.internal.DDLFormatterImpl;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Utility class for generating DDL scripts (create and drop) please ignore it.
 */
@Disabled
public class GenerateDDLScriptsTest {

    @EqualsAndHashCode
    @ToString
    public static class ScriptFile {

        private final String dialect;
        private final String alias;
        private final String prefix;

        public ScriptFile(String dialect, String alias, String prefix) {
            this.dialect = dialect;
            this.alias = alias;
            this.prefix = prefix;
        }

        public Path buildCreateFile(Path basePath) {
            return basePath.resolve(alias).resolve(prefix + "-pim-schema.sql");
        }

        public Path buildDropFile(Path basePath) {
            return basePath.resolve(alias).resolve(prefix + "-pim-drop-schema.sql");

        }

        public String getDialect() {
            return this.dialect;
        }
    }

    static Stream<ScriptFile> getScriptFiles() {
        return Stream.of(
                new ScriptFile("org.hibernate.dialect.DB2Dialect", "db2", "db2"),
                new ScriptFile("org.hibernate.dialect.H2Dialect", "h2", "h2"),
                new ScriptFile("org.hibernate.dialect.MariaDB103Dialect", "mariadb", "mariadb"),
                new ScriptFile("org.hibernate.dialect.MySQL8Dialect", "mysql", "mysql"),
                new ScriptFile("org.hibernate.dialect.Oracle12cDialect", "oracle", "oracle"),
                new ScriptFile("org.hibernate.dialect.PostgreSQL10Dialect", "postgresql", "postgresql"),
                new ScriptFile("org.hibernate.dialect.PostgresPlusDialect", "postgresql-plus", "postgresql-plus"),
                new ScriptFile("org.hibernate.dialect.SQLServer2012Dialect", "mssql", "mssql"));
    }

    @ParameterizedTest
    @MethodSource("getScriptFiles")
    public void generateDDL(ScriptFile scriptFile) throws Exception {

        Path basePath = Paths.get(System.getProperty("user.dir"), "ddl-scripts");

        Path createFilePath = scriptFile.buildCreateFile(basePath);
        Path dropFilePath = scriptFile.buildDropFile(basePath);

        Files.deleteIfExists(createFilePath);
        Files.deleteIfExists(dropFilePath);
        Files.createDirectories(basePath.resolve(scriptFile.alias));

        Map<String, Object> properties = new HashMap<>();
        StringWriter drop = new StringWriter();
        StringWriter create = new StringWriter();

        properties.put("hibernate.dialect", scriptFile.getDialect());
        properties.put("javax.persistence.schema-generation.scripts.action", "drop-and-create");
        properties.put("javax.persistence.schema-generation.scripts.drop-target", drop);
        properties.put("javax.persistence.schema-generation.scripts.create-target", create);
        Persistence.generateSchema("org.kie.test.persistence.generate-ddl-scripts", properties);

        try (FileWriter dropFile = new FileWriter(dropFilePath.toString());
             FileWriter createFile = new FileWriter(createFilePath.toString())) {
            dropFile.write(prettyFormatSQL(drop.toString()));
            createFile.write(prettyFormatSQL(create.toString()));
        }
    }

    private static String prettyFormatSQL(String unformatted){
        BufferedReader reader = new BufferedReader(new StringReader(unformatted));
        DDLFormatterImpl formatter = new DDLFormatterImpl();
        StringBuffer buffer = new StringBuffer();

        reader.lines().forEach(x -> {
            String formatted = formatter.format(x);
            buffer.append(formatted).append("\n");
        });
        return buffer.toString();
    }
}
