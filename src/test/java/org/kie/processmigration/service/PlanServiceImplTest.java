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

import java.util.List;

import javax.inject.Inject;
import javax.transaction.Transactional;

import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.ProcessRef;
import org.kie.processmigration.model.exceptions.PlanNotFoundException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class PlanServiceImplTest {

    @Inject
    PlanService planService;

    @BeforeEach
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
        Plan plan = createPlan(1);

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
        Plan plan = createPlan(1);
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
        Plan plan = createPlan(1);
        Long id = planService.create(plan).getId();

        // When
        assertThat(id, notNullValue());
        Plan other = createPlan(2);

        // Then
        assertThat(planService.update(id, other), equalTo(other));
        assertThat(planService.findAll(), hasSize(1));
    }

    private Plan createPlan(int id) {
        return new Plan()
                .setName("name" + id)
                .setSource(new ProcessRef()
                        .setContainerId("containerId" + id)
                        .setProcessId("sourceProcessId" + id))
                .setTarget(new ProcessRef()
                        .setContainerId("targetContainerId" + id)
                        .setProcessId("targetProcessId" + id))
                .setDescription("description" + id);
    }
}
