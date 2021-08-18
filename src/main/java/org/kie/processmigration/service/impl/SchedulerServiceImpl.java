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

package org.kie.processmigration.service.impl;

import java.util.Date;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.exceptions.InvalidMigrationException;
import org.kie.processmigration.model.exceptions.MigrationNotFoundException;
import org.kie.processmigration.service.MigrationService;
import org.kie.processmigration.service.SchedulerService;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.Startup;
import io.quarkus.runtime.annotations.RegisterForReflection;

@ApplicationScoped
@Startup
public class SchedulerServiceImpl implements SchedulerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerServiceImpl.class);

    @Inject
    Scheduler scheduler;

    @Override
    public void scheduleMigration(Migration migration) {
        JobDetail job = JobBuilder.newJob(MigrationJob.class)
                .withIdentity(migration.getId().toString())
                .build();
        try {
            LOGGER.debug("Schedule migration for {}", migration.getId());
            scheduler.scheduleJob(job, buildTrigger(migration));
        } catch (SchedulerException e) {
            LOGGER.error("Unable to schedule migration for {}", migration.getId(), e);
        }
    }

    @Override
    public void reScheduleMigration(Migration migration) {
        try {
            LOGGER.debug("re-scheduling migration for {}", migration.getId());
            Trigger trigger = buildTrigger(migration);
            scheduler.rescheduleJob(trigger.getKey(), buildTrigger(migration));
        } catch (SchedulerException e) {
            LOGGER.error("Unable to re-schedule job for {}", migration.getId(), e);
        }
    }

    private Trigger buildTrigger(Migration migration) {
        TriggerBuilder<Trigger> builder = TriggerBuilder.newTrigger().withIdentity(migration.getId().toString());
        if (migration.getDefinition().getExecution() == null || migration.getDefinition().getExecution().getScheduledStartTime() == null) {
            builder = builder.startNow();
        } else {
            builder.startAt(Date.from(migration
                    .getDefinition()
                    .getExecution()
                    .getScheduledStartTime()));
        }
        return builder.build();
    }

    @RegisterForReflection
    public static class MigrationJob implements Job {

        @Inject
        MigrationService migrationService;

        @Override
        @Transactional
        public void execute(JobExecutionContext jobExecutionContext) {
            Long migrationId = Long.valueOf(jobExecutionContext
                    .getJobDetail().getKey().getName());
            try {
                Migration migration = migrationService.get(migrationId);
                LOGGER.debug("Triggering migration job for {}", migrationId);
                migrationService.migrate(migration);
            } catch (InvalidMigrationException | MigrationNotFoundException e) {
                LOGGER.error("Unable to perform asynchronous migration", e);
                try {
                    jobExecutionContext.getScheduler().deleteJob(jobExecutionContext.getJobDetail().getKey());
                } catch (SchedulerException ex) {
                    LOGGER.error("Unable to delete job for failed migration {}", migrationId, ex);
                }
            }
        }
    }

}
