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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.mockito.InjectMock;
import lombok.SneakyThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.kie.processmigration.listener.CountDownJobListener;
import org.kie.processmigration.model.Execution;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.MigrationDefinition;
import org.kie.processmigration.model.MigrationReport;
import org.kie.processmigration.model.MigrationReportDto;
import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.ProcessRef;
import org.kie.processmigration.model.exceptions.InvalidMigrationException;
import org.kie.processmigration.model.exceptions.MigrationNotFoundException;
import org.kie.processmigration.service.KieService;
import org.kie.processmigration.service.MigrationService;
import org.kie.processmigration.service.PlanService;
import org.kie.processmigration.test.persistence.scripts.ScriptPersistenceUtil;
import org.kie.processmigration.test.persistence.scripts.ScriptFilter;
import org.kie.server.api.model.admin.MigrationReportInstance;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.admin.ProcessAdminServicesClient;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.kie.processmigration.model.Execution.ExecutionStatus.COMPLETED;
import static org.kie.processmigration.model.Execution.ExecutionStatus.CREATED;
import static org.kie.processmigration.model.Execution.ExecutionStatus.SCHEDULED;
import static org.kie.processmigration.model.Execution.ExecutionType.ASYNC;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.quartz.impl.matchers.EverythingMatcher.allJobs;

public abstract class AbstractScriptsBaseTest {

    private static final String KIE_SERVER_ID = System.getProperty("kie.server.id", "kie-server");

    protected static final String INITIAL_DB_SCRIPTS_RESOURCE_PATH = "/initial-ddls";
    protected static final String DB_CREATE_SCRIPTS_RESOURCE_PATH = "/ddl-scripts";
    protected static final String DB_UPGRADE_SCRIPTS_RESOURCE_PATH = "/upgrade-scripts";
    protected ScriptPersistenceUtil scriptRunner;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    EntityManagerFactory emf;

    @Inject
    MigrationService migrationService;

    @Inject
    PlanService planService;

    @InjectMock
    KieService kieService;

    @Inject
    Scheduler scheduler;

    @BeforeEach
    @SneakyThrows
    void setupAndDropSchema() {
        Properties props = new Properties();
        props.putAll(emf.getProperties());
        scriptRunner = new ScriptPersistenceUtil(dataSource, props);
        scriptRunner.executeScriptRunner(DB_CREATE_SCRIPTS_RESOURCE_PATH, ScriptFilter.drop());
    }

    @AfterEach
    @SneakyThrows
    void restoreSchema() {
        scriptRunner.executeScriptRunner(DB_CREATE_SCRIPTS_RESOURCE_PATH, ScriptFilter.create());
    }

    protected Plan createPlan(PlanService planService) {
        Plan plan = new Plan()
                .setSource(new ProcessRef().setContainerId("source-container").setProcessId("source-process"))
                .setTarget(new ProcessRef().setContainerId("target-container").setProcessId("target-process"))
                .setName("migrationPlan");

        Plan resultPlan = planService.create(plan);
        assertPlan(resultPlan, plan);

        return resultPlan;
    }

    protected MigrationReportInstance createReport(Long instanceId) {
        MigrationReportInstance report = new MigrationReportInstance();
        report.setStartDate(new Date());
        report.setEndDate(new Date());
        report.setProcessInstanceId(instanceId);
        report.setSuccessful(true);
        report.setLogs(new ArrayList<>(List.of("Migration went fine")));
        return report;
    }

    protected void assertPlan(Plan actual, Plan expected) {
        assertThat(actual.getId(), notNullValue());
        assertThat(actual.getName(), is(expected.getName()));
        assertThat(actual.getDescription(), is(expected.getDescription()));
        assertThat(actual.getSource().getContainerId(), is(expected.getSource().getContainerId()));
        assertThat(actual.getSource().getProcessId(), is(expected.getSource().getProcessId()));
        assertThat(actual.getTarget().getContainerId(), is(expected.getTarget().getContainerId()));
        assertThat(actual.getTarget().getProcessId(), is(expected.getTarget().getProcessId()));
    }

    protected void assertMigration(Migration migration, Execution.ExecutionType executionType) {
        assertNotNull(migration);
        assertThat(migration.getId(), notNullValue());
        assertThat(migration.getStatus(), in(executionType == ASYNC ? List.of(SCHEDULED, CREATED) : Collections.singletonList(COMPLETED)));
        assertThat(migration.getStartedAt(), executionType == ASYNC ? nullValue() : notNullValue());
        assertThat(migration.getFinishedAt(), executionType == ASYNC ? nullValue() : notNullValue());
        assertThat(migration.getCancelledAt(), nullValue());
        assertThat(migration.getErrorMessage(), nullValue());
        assertThat(migration.getDefinition(), notNullValue());
        
        List<MigrationReport> reports = MigrationReport.listByMigrationId(migration.getId());
        assertThat(reports, hasSize(1));
        assertThat(reports.get(0).getId(), notNullValue());
        assertThat(reports.get(0).getMigrationId(), equalTo(migration.getId()));
        assertThat(reports.get(0).getStartDate(), notNullValue());
        assertThat(reports.get(0).getEndDate(), notNullValue());
        assertThat(reports.get(0).getProcessInstanceId(), notNullValue());
        assertThat(reports.get(0).getSuccessful(), equalTo(Boolean.TRUE));
    }

    protected void testSimpleMigration(Execution execution, PlanService planService,
                                   Scheduler scheduler, KieService kieService, MigrationService migrationService)
            throws SchedulerException, InvalidMigrationException, InterruptedException, MigrationNotFoundException {
        Plan plan = createPlan(planService);
        MigrationDefinition definition = new MigrationDefinition();
        definition.setPlanId(plan.getId());
        definition.setKieServerId(KIE_SERVER_ID);
        definition.setProcessInstanceIds(new ArrayList<>(List.of(1L)));
        definition.setExecution(execution);

        CountDownLatch count = new CountDownLatch(1);
        scheduler.getListenerManager().addJobListener(new CountDownJobListener(count), allJobs());

        when(kieService.hasKieServer(definition.getKieServerId())).thenReturn(Boolean.TRUE);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getSource())).thenReturn(Boolean.TRUE);
        when(kieService.existsProcessDefinition(definition.getKieServerId(), plan.getTarget())).thenReturn(Boolean.TRUE);

        QueryServicesClient mockQueryServicesClient = mock(QueryServicesClient.class);
        when(kieService.getQueryServicesClient(definition.getKieServerId())).thenReturn(mockQueryServicesClient);
        ProcessAdminServicesClient mockAdminServicesClient = mock(ProcessAdminServicesClient.class);
        when(kieService.getProcessAdminServicesClient(definition.getKieServerId())).thenReturn(mockAdminServicesClient);

        List<ProcessInstance> instances = new ArrayList<>();
        ProcessInstance instance = new ProcessInstance();
        instance.setId(1L);
        instance.setContainerId("source-container");
        instances.add(instance);
        when(mockQueryServicesClient.findProcessInstancesByContainerId(eq(plan.getSource().getContainerId()), anyList(), anyInt(), anyInt())).thenReturn(instances);
        when(mockQueryServicesClient.findProcessInstanceById(instance.getId())).thenReturn(instance);

        MigrationReportInstance report = createReport(instance.getId());
        when(mockAdminServicesClient.migrateProcessInstance(anyString(), anyLong(), anyString(), anyString(), anyMap())).thenReturn(report);

        // When
        Migration migration = migrationService.submit(definition);
  
        // Then
        if (execution.getType() == ASYNC) {
            assertThat(scheduler.checkExists(new JobKey(migration.getId().toString())), is(Boolean.TRUE));
            assertThat(scheduler.checkExists(new TriggerKey(migration.getId().toString())), is(Boolean.TRUE));
            if (!count.await(10, TimeUnit.SECONDS)) {
                fail("Failed while waiting for the jobs to be completed");
            }
        }
        assertMigration(migration, execution.getType());

        // Then
        List<Migration> migrations = migrationService.findAll();

        assertThat(migrations, notNullValue());
        assertThat(migrations, hasSize(1));
        migration = migrations.get(0);
        assertThat(migration.getStatus(), is(COMPLETED));

        assertThat(migration.getCancelledAt(), nullValue());
        assertThat(migration.getStartedAt(), notNullValue());
        assertThat(migration.getFinishedAt(), notNullValue());
        List<MigrationReportDto> results = migrationService.getResults(migration.getId());
        assertThat(results, hasSize(1));
        assertThat(results.get(0).getProcessInstanceId(), is(report.getProcessInstanceId()));
        assertThat(results.get(0).getSuccessful(), is(report.isSuccessful()));
        assertThat(results.get(0).getStartDate(), is(report.getStartDate().toInstant()));
        assertThat(results.get(0).getEndDate(), is(report.getEndDate().toInstant()));
        assertThat(results.get(0).getMigrationId(), is(migration.getId()));
        assertThat(migrationService.getReport(results.get(0).getId()).getLogs(),
                                containsInAnyOrder(report.getLogs().toArray()));

        migrationService.delete(migration.getId());

        verify(mockAdminServicesClient, times(1)).migrateProcessInstance(anyString(), anyLong(), anyString(), anyString(), anyMap());
    }

    protected void createSchema() throws IOException, SQLException {
        scriptRunner.executeScriptRunner(DB_CREATE_SCRIPTS_RESOURCE_PATH, ScriptFilter.create());
    }

    protected void dropSchema() throws IOException, SQLException {
        scriptRunner.executeScriptRunner(DB_CREATE_SCRIPTS_RESOURCE_PATH, ScriptFilter.drop());
    }

    protected void createInitialSchema() throws IOException, SQLException {
        //create PIM very first schema version
        scriptRunner.executeScriptRunner(INITIAL_DB_SCRIPTS_RESOURCE_PATH, ScriptFilter.create());
    }
}
