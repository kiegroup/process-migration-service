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
package org.kie.processmigration.persistence;

import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTest;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.processmigration.model.Execution;
import org.kie.processmigration.test.persistence.scripts.DistributionType;
import org.kie.processmigration.test.persistence.scripts.ScriptFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.processmigration.model.Execution.ExecutionType.SYNC;

@QuarkusTest
public class UpgradeScriptsTest extends AbstractScriptsBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(UpgradeScriptsTest.class);

    static Stream<DistributionType> distributionTypes() {
        return Stream.of(DistributionType.COMMUNITY, DistributionType.PRODUCT);
    }

    /**
     * Tests that DB schema is upgraded properly using database upgrade scripts.
     */
    @ParameterizedTest
    @MethodSource("distributionTypes")
    @SneakyThrows
    public void testUpgradeScripts(DistributionType distributionType) {
        logger.info("entering testExecutingScripts with type: {} ", distributionType);
        try {
            createInitialSchema();
            scriptRunner.executeScriptRunner(DB_UPGRADE_SCRIPTS_RESOURCE_PATH, ScriptFilter
                    .upgrade()
                    .setDistribution(distributionType));
            Execution execution = new Execution();
            execution.setType(SYNC);
            testSimpleMigration(execution, planService, scheduler, kieService, migrationService);
        } finally {
            dropSchema();
        }
    }

}
