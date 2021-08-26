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

package org.kie.processmigration.service.impl;

import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.exceptions.PlanNotFoundException;
import org.kie.processmigration.service.PlanService;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PlanServiceImpl implements PlanService {

    @Override
    public List<Plan> findAll() {
        return Plan.findAll().list();
    }

    @Override
    public Plan get(Long id) throws PlanNotFoundException {
        Optional<Plan> plan = Plan.findByIdOptional(id);
        return plan.orElseThrow(() -> new PlanNotFoundException(id));
    }

    @Override
    @Transactional
    public Plan delete(Long id) throws PlanNotFoundException {
        Plan plan = get(id);
        plan.delete();
        return plan;
    }

    @Override
    @Transactional
    public Plan create(Plan plan) {
        plan.persist();
        return plan;
    }

    @Override
    @Transactional
    public Plan update(Long id, Plan plan) throws PlanNotFoundException {
        Plan current = get(id);
        current.copy(plan).persist();
        return current;
    }
}
