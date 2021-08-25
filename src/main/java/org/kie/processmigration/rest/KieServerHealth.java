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

package org.kie.processmigration.rest;

import io.quarkus.runtime.Startup;
import io.smallrye.health.api.HealthRegistry;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.kie.processmigration.model.KieServerConfig;
import org.kie.processmigration.service.KieService;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@Startup
public class KieServerHealth {

    @Inject
    KieService kieService;

    @Inject
    @Liveness
    HealthRegistry livenessHealthRegistry;

    @Inject
    @Readiness
    HealthRegistry readinessHealthRegistry;

    @PostConstruct
    public void init() {
        kieService.getConfigs().forEach(this::register);
    }

    private void register(KieServerConfig conf) {
        KieServerHealthCheck healthCheck = new KieServerHealthCheck(conf);
        livenessHealthRegistry.register(conf.getName(), healthCheck);
        readinessHealthRegistry.register(conf.getName(), healthCheck);
    }


}
