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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import io.quarkus.test.junit.QuarkusTest;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.processmigration.model.Execution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.processmigration.model.Execution.ExecutionType.ASYNC;
import static org.kie.processmigration.model.Execution.ExecutionType.SYNC;

@QuarkusTest
public class DDLScriptsTest extends AbstractScriptsBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(UpgradeScriptsTest.class);

    static Stream<Execution> getExecutionTypes() {
        Execution sync = new Execution();
        sync.setType(SYNC);
        Execution async = new Execution();
        Instant when = Instant.now().plus(2000, ChronoUnit.MILLIS);
        async.setType(ASYNC).setScheduledStartTime(when);
        return Stream.of(sync, async);
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getExecutionTypes")
    void testValidateMigration(Execution execution) {
        logger.info("entering testValidateMigration with execution mode: {} ", execution.getType());
        createSchema();
        testSimpleMigration(execution, planService, scheduler, kieService, migrationService);
        dropSchema();
    }

}
