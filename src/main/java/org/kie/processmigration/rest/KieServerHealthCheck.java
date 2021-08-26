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

package org.kie.processmigration.rest;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.kie.processmigration.model.KieServerConfig;

import static org.kie.processmigration.model.KieServerConfig.SUCCESS_STATUS;

public class KieServerHealthCheck implements HealthCheck {

    private final KieServerConfig config;

    public KieServerHealthCheck(KieServerConfig config) {
        this.config = config;
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = new HealthCheckResponse().named("kie-server " + config.getName());
        if (SUCCESS_STATUS.equals(config.getStatus())) {
            return response.up().build();
        }
        return response.down().build();
    }


}
