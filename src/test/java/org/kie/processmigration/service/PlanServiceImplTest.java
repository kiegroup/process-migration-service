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
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.ProcessRef;
import org.kie.processmigration.model.exceptions.PlanNotFoundException;

import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class PlanServiceImplTest {

    @Inject
    PlanService planService;

    @AfterEach
    @Transactional
    void cleanUp() {
        Plan.deleteAll();
    }

    @Test
    void testService() {
        assertThat(planService, CoreMatchers.notNullValue());
        assertThat(planService.findAll(), empty());
    }

    @Test
    void testCreateAndFindAll() {
        // Given
        Plan plan = new Plan()
                .setName("name")
                .setSource(new ProcessRef()
                        .setContainerId("containerId").setProcessId("sourceProcessId"))
                .setTarget(new ProcessRef().setContainerId("targetContainerId").setProcessId("targetProcessId"))
                .setDescription("description");

        // When
        Plan result = planService.create(plan);

        // Then
        List<Plan> plans = planService.findAll();

        assertThat(plans, notNullValue());
        assertThat(plans, hasSize(1));
        assertThat(plans.get(0), equalTo(result));
    }

    @Test
    void testDelete() throws PlanNotFoundException {
        assertThrows(PlanNotFoundException.class, () -> planService.delete(1L));
        // Given
        Plan plan = new Plan()
                .setName("name")
                .setSource(new ProcessRef()
                        .setContainerId("containerId").setProcessId("sourceProcessId"))
                .setTarget(new ProcessRef().setContainerId("targetContainerId").setProcessId("targetProcessId"))
                .setDescription("description");
        Plan result = planService.create(plan);

        // When
        assertThat(result, notNullValue());
        assertThat(result.getId(), notNullValue());
        assertThat(planService.delete(result.getId()), equalTo(plan));

        // Then
        assertThat(planService.findAll(), empty());
    }


    @Test
    void testUpdate() throws PlanNotFoundException {
        assertThrows(PlanNotFoundException.class, () -> planService.delete(1L));
        // Given
        Plan plan = new Plan()
                .setName("name")
                .setSource(new ProcessRef()
                        .setContainerId("containerId").setProcessId("sourceProcessId"))
                .setTarget(new ProcessRef().setContainerId("targetContainerId").setProcessId("targetProcessId"))
                .setDescription("description");
        Long id = planService.create(plan).getId();

        // When
        assertThat(id, notNullValue());
        Plan other = new Plan()
                .setName("name2")
                .setSource(new ProcessRef()
                        .setContainerId("containerId2").setProcessId("sourceProcessId2"))
                .setTarget(new ProcessRef().setContainerId("targetContainerId2").setProcessId("targetProcessId2"))
                .setDescription("description2");

        // Then
        assertThat(planService.update(id, other), equalTo(other));
        assertThat(planService.findAll(), hasSize(1));
    }
}
