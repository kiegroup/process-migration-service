/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.Execution;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.MigrationDefinition;
import org.kie.processmigration.model.exceptions.InvalidMigrationException;
import org.kie.processmigration.model.exceptions.MigrationNotFoundException;
import org.kie.processmigration.service.SchedulerService;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerKey;

import javax.inject.Inject;
import javax.swing.text.StyledEditorKit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.*;
import static org.quartz.impl.matchers.EverythingMatcher.allJobs;

@QuarkusTest
class SchedulerServiceImplTest {

    @Inject
    SchedulerService schedulerService;

    @InjectMock
    MigrationService migrationService;

    @Inject
    Scheduler scheduler;

    @AfterEach
    void resetScheduler() throws SchedulerException {
        if(scheduler != null) {
            scheduler.clear();
        }
    }

    @Test
    void testScheduler() {
        assertThat(schedulerService, notNullValue());
    }

    @Test
    void testScheduleMigrationNow() throws InvalidMigrationException, InterruptedException, MigrationNotFoundException, SchedulerException {
        // Given
        MigrationDefinition definition = new MigrationDefinition();
        definition.setPlanId(1L);
        definition.setKieServerId("kie-server-1");
        definition.setRequester("me");
        definition.setProcessInstanceIds(List.of(1L));
        Migration migration = new Migration(definition);
        migration.id = 99L;
        CountDownLatch count = new CountDownLatch(1);
        when(migrationService.get(migration.id)).thenReturn(migration);
        scheduler.getListenerManager().addJobListener(new TestJobListener(count), allJobs());

        // When
        schedulerService.scheduleMigration(migration);

        // Then
        count.await(10, TimeUnit.SECONDS);
        verify(migrationService, times(1)).get(99L);
        verify(migrationService, times(1)).migrate(migration);
        assertThat(scheduler.checkExists(new JobKey(migration.id.toString())), is(Boolean.FALSE));
        assertThat(scheduler.checkExists(new TriggerKey(migration.id.toString())), is(Boolean.FALSE));
    }

    @Test
    void testScheduleMigration() throws InvalidMigrationException, InterruptedException, MigrationNotFoundException, SchedulerException {
        // Given
        MigrationDefinition definition = new MigrationDefinition();
        definition.setPlanId(1L);
        definition.setKieServerId("kie-server-1");
        definition.setRequester("me");
        definition.setProcessInstanceIds(List.of(1L));
        Instant when = Instant.now().plus(500, ChronoUnit.MILLIS);
        definition.setExecution(new Execution().setType(Execution.ExecutionType.ASYNC).setScheduledStartTime(when));
        Migration migration = new Migration(definition);
        migration.id = 99L;
        CountDownLatch count = new CountDownLatch(1);
        scheduler.getListenerManager().addJobListener(new TestJobListener(count), allJobs());
        when(migrationService.get(migration.id)).thenReturn(migration);

        // When
        schedulerService.scheduleMigration(migration);

        // Then
        assertThat(scheduler.checkExists(new JobKey(migration.id.toString())), is(Boolean.TRUE));
        assertThat(scheduler.checkExists(new TriggerKey(migration.id.toString())), is(Boolean.TRUE));
        count.await(10, TimeUnit.SECONDS);

        verify(migrationService, times(1)).get(99L);
        verify(migrationService, times(1)).migrate(migration);
    }


    @Test
    void testReScheduleMigration() throws InvalidMigrationException, InterruptedException, MigrationNotFoundException, SchedulerException {
        // Given
        MigrationDefinition definition = new MigrationDefinition();
        definition.setPlanId(1L);
        definition.setKieServerId("kie-server-1");
        definition.setRequester("me");
        definition.setProcessInstanceIds(List.of(1L));
        Instant when = Instant.now().plus(5, ChronoUnit.HOURS);
        definition.setExecution(new Execution().setType(Execution.ExecutionType.ASYNC).setScheduledStartTime(when));
        Migration migration = new Migration(definition);
        migration.id = 99L;
        CountDownLatch count = new CountDownLatch(1);
        scheduler.getListenerManager().addJobListener(new TestJobListener(count), allJobs());
        when(migrationService.get(migration.id)).thenReturn(migration);

        schedulerService.scheduleMigration(migration);

        // When
        migration.getDefinition().getExecution().setScheduledStartTime(Instant.now().plus(1, ChronoUnit.SECONDS));
        schedulerService.reScheduleMigration(migration);

        // Then
        assertThat(scheduler.checkExists(new JobKey(migration.id.toString())), is(Boolean.TRUE));
        assertThat(scheduler.checkExists(new TriggerKey(migration.id.toString())), is(Boolean.TRUE));
        count.await(10, TimeUnit.SECONDS);

        verify(migrationService, times(1)).get(99L);
        verify(migrationService, times(1)).migrate(migration);
    }


    @Test
    void testScheduleMigrationNotFound() throws InvalidMigrationException, InterruptedException, MigrationNotFoundException, SchedulerException {
        // Given
        MigrationDefinition definition = new MigrationDefinition();
        definition.setPlanId(1L);
        definition.setKieServerId("kie-server-1");
        definition.setRequester("me");
        definition.setProcessInstanceIds(List.of(1L));
        Migration migration = new Migration(definition);
        migration.id = 99L;
        CountDownLatch count = new CountDownLatch(1);
        scheduler.getListenerManager().addJobListener(new TestJobListener(count), allJobs());

        // When
        schedulerService.scheduleMigration(migration);
        count.await(10, TimeUnit.SECONDS);
        // Then
        verify(migrationService, times(0)).migrate(migration);
    }

    private static final class TestJobListener implements JobListener {

        private final CountDownLatch count;

        TestJobListener(CountDownLatch count) {
            this.count = count;
        }

        @Override
        public String getName() {
            return "Test Job Listener";
        }

        @Override
        public void jobToBeExecuted(JobExecutionContext jobExecutionContext) {

        }

        @Override
        public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {

        }

        @Override
        public void jobWasExecuted(JobExecutionContext jobExecutionContext, JobExecutionException e) {
            count.countDown();
        }
    }

}
