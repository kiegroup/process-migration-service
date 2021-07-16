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

package org.kie.processmigration.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.exceptions.InvalidMigrationException;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;


@QuarkusTest
class RecoveryServiceTest {

    @Inject
    RecoveryService recoveryService;

    @InjectMock
    MigrationService migrationService;

    @Test
    void testRecovery() throws InvalidMigrationException {
        List<Migration> pendingMigrations = new ArrayList<>();
        pendingMigrations.add(new Migration());
        pendingMigrations.add(new Migration());
        when(migrationService.findPending()).thenReturn(pendingMigrations);

        recoveryService.resumeMigrations();

        verify(migrationService, times(2)).migrate(any(Migration.class));
    }

}
