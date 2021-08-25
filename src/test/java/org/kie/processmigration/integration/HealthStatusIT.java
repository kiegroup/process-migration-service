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

package org.kie.processmigration.integration;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ResponseBodyExtractionOptions;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.KieServerConfig;
import org.kie.processmigration.service.KieService;
import org.kie.processmigration.test.Profiles;

import javax.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(Profiles.KieServerIntegrationProfile.class)
class HealthStatusIT {

    @Inject
    KieService service;

    @Test
    void testLivenessHealthChecks() {
        ResponseBodyExtractionOptions body = given()
                .when()
                .get("/q/health/live")
                .then().statusCode(HttpStatus.SC_OK)
                .extract().body();
        assertThat(body.jsonPath().getString("status"), is("UP"));
        int count = 0;
        for(KieServerConfig config : service.getConfigs()) {
            assertThat(body.jsonPath().getString("checks[" + count + "].name"), is("kie-server " + config.getName()));
            assertThat(body.jsonPath().getString("checks[" + count + "].status"), is("UP"));
            count++;
        }
    }

    @Test
    void testReadinessHealthChecks() {
        ResponseBodyExtractionOptions body = given()
                .when()
                .get("/q/health/ready")
                .then().statusCode(HttpStatus.SC_OK)
                .extract().body();
        assertThat(body.jsonPath().getString("status"), is("UP"));
        assertThat(body.jsonPath().getString("checks[0].name"), is("Database connections health check"));
        assertThat(body.jsonPath().getString("checks[0].status"), is("UP"));
        int count = 1;
        for(KieServerConfig config : service.getConfigs()) {
            assertThat(body.jsonPath().getString("checks[" + count + "].name"), is("kie-server " + config.getName()));
            assertThat(body.jsonPath().getString("checks[" + count + "].status"), is("UP"));
            count++;
        }
    }
}
