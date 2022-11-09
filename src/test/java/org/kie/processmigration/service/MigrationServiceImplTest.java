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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.Execution;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.MigrationDefinition;
import org.kie.processmigration.model.MigrationReport;
import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.ProcessRef;
import org.kie.processmigration.model.exceptions.InvalidMigrationException;
import org.kie.processmigration.model.exceptions.MigrationNotFoundException;
import org.kie.processmigration.model.exceptions.PlanNotFoundException;
import org.kie.processmigration.model.exceptions.ProcessNotFoundException;
import org.kie.server.api.model.admin.MigrationReportInstance;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.admin.ProcessAdminServicesClient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
class MigrationServiceImplTest {

    @Inject
    MigrationService migrationService;

    @InjectMock
    PlanService planService;

    @InjectMock
    KieService kieService;

    @InjectMock
    SchedulerService schedulerService;

    @BeforeEach
    void cleanUp() {
        migrationService.findAll().forEach(migration -> {
            try {
                migrationService.delete(migration.getId());
            } catch (MigrationNotFoundException e) {
                // ignore
            }
        });
    }

    @Test
    void testValidateDefinition() {
        assertThrows(InvalidMigrationException.class, () -> migrationService.submit(null));
        MigrationDefinition definition = new MigrationDefinition();
        assertThrows(InvalidMigrationException.class, () -> migrationService.submit(definition));
        definition.setPlanId(1L);
        assertThrows(InvalidMigrationException.class, () -> migrationService.submit(definition));
        definition.setKieServerId("foo");
        assertThrows(InvalidMigrationException.class, () -> migrationService.submit(definition));
    }

    @Test
    void testValidatePlan() throws InvalidMigrationException, PlanNotFoundException {
        Plan plan = new Plan()
                .setSource(new ProcessRef().setContainerId("source-container").setProcessId("source-process"))
                .setTarget(new ProcessRef().setContainerId("target-container").setProcessId("target-process"))
                .setName("migrationPlan");
        MigrationDefinition definition = new MigrationDefinition();
        definition.setPlanId(11L);
        definition.setKieServerId("foo");
        definition.setExecution(new Execution()
                .setType(Execution.ExecutionType.ASYNC)
                .setScheduledStartTime(Instant.now().plus(1, ChronoUnit.MINUTES)));
        when(kieService.hasKieServer(definition.getKieServerId())).thenReturn(Boolean.TRUE);

        when(planService.get(definition.getPlanId())).thenThrow(new PlanNotFoundException(definition.getPlanId())).thenReturn(plan);
        assertThrows(InvalidMigrationException.class, () -> migrationService.submit(definition));

        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getSource()))
                .thenReturn(Boolean.FALSE);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getTarget()))
                .thenReturn(Boolean.TRUE);
        assertThrows(ProcessNotFoundException.class, () -> migrationService.submit(definition));

        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getSource()))
                .thenReturn(Boolean.TRUE);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getTarget()))
                .thenReturn(Boolean.FALSE);
        assertThrows(ProcessNotFoundException.class, () -> migrationService.submit(definition));

        verifyNoInteractions(schedulerService);
    }

    @Test
    void testScheduleMigration() throws InvalidMigrationException, PlanNotFoundException, MigrationNotFoundException {
        Plan plan = new Plan()
                .setSource(new ProcessRef().setContainerId("source-container").setProcessId("source-process"))
                .setTarget(new ProcessRef().setContainerId("target-container").setProcessId("target-process"))
                .setName("migrationPlan");
        MigrationDefinition definition = new MigrationDefinition();
        definition.setPlanId(11L);
        definition.setKieServerId("foo");
        definition.setExecution(new Execution().setType(Execution.ExecutionType.ASYNC).setScheduledStartTime(Instant.now().plus(1, ChronoUnit.MINUTES)));
        when(kieService.hasKieServer(definition.getKieServerId())).thenReturn(Boolean.TRUE);
        when(planService.get(11L)).thenReturn(plan);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getSource())).thenReturn(Boolean.TRUE);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getTarget())).thenReturn(Boolean.TRUE);

        Migration migration = migrationService.submit(definition);

        verify(schedulerService, times(1)).scheduleMigration(migration);
        migrationService.delete(migration.getId());
    }

    @Test
    void testSubmitSyncMigration() throws InvalidMigrationException, PlanNotFoundException, MigrationNotFoundException {
        // Given
        assertThat(migrationService, notNullValue());
        Plan plan = new Plan()
                .setSource(new ProcessRef().setContainerId("source-container").setProcessId("source-process"))
                .setTarget(new ProcessRef().setContainerId("target-container").setProcessId("target-process"))
                .setName("migrationPlan");
        MigrationDefinition definition = new MigrationDefinition();
        definition.setRequester("requester");
        definition.setKieServerId("kie-server-1");
        definition.setExecution(new Execution().setType(Execution.ExecutionType.SYNC));
        definition.setPlanId(11L);

        when(planService.get(11L)).thenReturn(plan);
        when(kieService.hasKieServer(definition.getKieServerId())).thenReturn(Boolean.TRUE);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getSource())).thenReturn(Boolean.TRUE);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getTarget())).thenReturn(Boolean.TRUE);

        QueryServicesClient mockQueryServicesClient = mock(QueryServicesClient.class);
        when(kieService.getQueryServicesClient(definition.getKieServerId())).thenReturn(mockQueryServicesClient);
        ProcessAdminServicesClient mockAdminServicesClient = mock(ProcessAdminServicesClient.class);
        when(kieService.getProcessAdminServicesClient(definition.getKieServerId())).thenReturn(mockAdminServicesClient);

        List<ProcessInstance> instances = new ArrayList<>();
        ProcessInstance instance = new ProcessInstance();
        instance.setId(2L);
        instance.setContainerId("source-container");
        instance.setProcessId("source-process");
        instances.add(instance);
        when(mockQueryServicesClient.findProcessInstancesByContainerId(eq(plan.getSource().getContainerId()), anyList(), anyInt(), anyInt())).thenReturn(instances);
        when(mockQueryServicesClient.findProcessInstanceById(instance.getId())).thenReturn(instance);

        List.of(createReport(instance.getId(), true), createReport(instance.getId(), false))
                .forEach(r -> {
                    try {
                        when(mockAdminServicesClient.migrateProcessInstance(anyString(), anyLong(), anyString(), anyString(), anyMap())).thenReturn(r);

                        // When
                        migrationService.submit(definition);

                        // Then
                        List<Migration> migrations = migrationService.findAll();

                        assertThat(migrations, notNullValue());
                        assertThat(migrations, hasSize(1));
                        Migration migration = migrations.get(0);
                        if (r.isSuccessful()) {
                            assertThat(migration.getStatus(), is(Execution.ExecutionStatus.COMPLETED));
                        } else {
                            assertThat(migration.getStatus(), is(Execution.ExecutionStatus.FAILED));
                        }
                        assertThat(migration.getCancelledAt(), nullValue());
                        assertThat(migration.getStartedAt(), notNullValue());
                        assertThat(migration.getFinishedAt(), notNullValue());
                        List<MigrationReport> results = migrationService.getResults(migration.getId());
                        assertThat(results, hasSize(1));
                        assertThat(results.get(0).getProcessInstanceId(), is(r.getProcessInstanceId()));
                        assertThat(results.get(0).getSuccessful(), is(r.isSuccessful()));
                        assertThat(results.get(0).getStartDate(), is(r.getStartDate().toInstant()));
                        assertThat(results.get(0).getEndDate(), is(r.getEndDate().toInstant()));
                        assertThat(results.get(0).getMigrationId(), is(migration.getId()));
                        assertThat(results.get(0).getLogs(), containsInAnyOrder(r.getLogs().toArray()));

                        migrationService.delete(migration.getId());
                    } catch (MigrationNotFoundException | InvalidMigrationException e) {
                        fail("Unexpected exception", e);
                    }
                });
        verify(mockAdminServicesClient, times(2)).migrateProcessInstance(anyString(), anyLong(), anyString(), anyString(), anyMap());
    }

    private MigrationReportInstance createReport(Long instanceId, boolean successful) {
        MigrationReportInstance report = new MigrationReportInstance();
        report.setStartDate(new Date());
        report.setEndDate(new Date());
        report.setProcessInstanceId(instanceId);
        if (successful) {
            report.setSuccessful(true);
            report.setLogs(List.of("Migration went fine"));
        } else {
            report.setSuccessful(false);
            report.setLogs(List.of("Migration went wrong"));
        }
        return report;
    }

}
