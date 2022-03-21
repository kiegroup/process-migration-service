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

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.Execution;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.MigrationDefinition;
import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.ProcessRef;
import org.kie.processmigration.model.exceptions.InvalidKieServerException;
import org.kie.processmigration.service.KieService;
import org.kie.processmigration.test.Profiles;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieServiceResponse;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.ProcessServicesClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.kie.processmigration.test.ContainerKieServerLifecycleManager.ARTIFACT_ID;
import static org.kie.processmigration.test.ContainerKieServerLifecycleManager.CONTAINER_ID;
import static org.kie.processmigration.test.ContainerKieServerLifecycleManager.GROUP_ID;
import static org.kie.processmigration.test.ContainerKieServerLifecycleManager.KIE_SERVER_ID;

@QuarkusTest
@TestProfile(Profiles.KieServerIntegrationProfile.class)
class ProcessMigrationIT {

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static final String PROCESS_ID = "test.myprocess";
    private static final String SOURCE_CONTAINER_ID = "test_1.0.0";
    private static final String TARGET_CONTAINER_ID = "test_2.0.0";

    @ConfigProperty(name = "pim.username")
    String username;

    @ConfigProperty(name = "pim.password")
    String password;

    @Inject
    KieService kieService;

    @Inject
    ObjectMapper mapper;

    @BeforeEach
    void deployProcesses() throws Exception {
        KieServicesClient client = kieService.getClient(KIE_SERVER_ID);
        for (String version : List.of("1.0.0", "2.0.0")) {
            ReleaseId releaseId = new ReleaseId(GROUP_ID, ARTIFACT_ID, version);
            KieContainerResource resource = new KieContainerResource(CONTAINER_ID, releaseId);
            ServiceResponse<KieContainerResource> response = client.createContainer(CONTAINER_ID + "_" + version, resource);
            assertThat(response.getType(), is(KieServiceResponse.ResponseType.SUCCESS));
        }
    }

    @Test
    void testBasicMigration() throws InvalidKieServerException, IOException {
        // Given
        startProcesses();

        // When
        createMigration();

        // Then
        ProcessServicesClient processClient = kieService.getClient(KIE_SERVER_ID).getServicesClient(ProcessServicesClient.class);
        List<ProcessInstance> instances = processClient.findProcessInstances(SOURCE_CONTAINER_ID, 0, 10);
        assertThat(instances, hasSize(1));
        assertThat(instances.get(0).getId(), is(2L));
        instances = processClient.findProcessInstances(TARGET_CONTAINER_ID, 0, 10);
        assertThat(instances, hasSize(1));
        assertThat(instances.get(0).getId(), is(1L));
    }

    private void startProcesses() throws InvalidKieServerException {
        ProcessServicesClient client = kieService.getClient(KIE_SERVER_ID).getServicesClient(ProcessServicesClient.class);
        client.startProcess(SOURCE_CONTAINER_ID, PROCESS_ID);
        client.startProcess(SOURCE_CONTAINER_ID, PROCESS_ID);
    }

    private void createMigration() throws IOException {
        Plan plan = createPlan();
        MigrationDefinition def = new MigrationDefinition();
        def.setPlanId(plan.getId());
        def.setKieServerId(KIE_SERVER_ID);
        def.setProcessInstanceIds(List.of(1L));
        def.setExecution(new Execution().setType(Execution.ExecutionType.SYNC));

        String result = given()
                .body(mapper.writeValueAsString(def))
                .auth()
                .basic(username, password)
                .contentType(MediaType.APPLICATION_JSON)
                .post("/rest/migrations")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .body()
                .asString();
        Migration migration = mapper.readValue(result, Migration.class);
        assertNotNull(migration);
        assertThat(migration.getId(), notNullValue());
        assertThat(migration.getStatus(), is(Execution.ExecutionStatus.COMPLETED));
        assertThat(migration.getStartedAt(), notNullValue());
        assertThat(migration.getFinishedAt(), notNullValue());
        assertThat(migration.getCancelledAt(), nullValue());
        assertThat(migration.getErrorMessage(), nullValue());
        assertThat(migration.getReports(), empty());
        assertThat(migration.getDefinition(), notNullValue());
        assertThat(migration.getDefinition().getRequester(), is(username));
    }

    private Plan createPlan() throws IOException {
        Plan plan = new Plan()
                .setName("test-plan")
                .setDescription("the test plan")
                .setSource(new ProcessRef()
                        .setContainerId(SOURCE_CONTAINER_ID)
                        .setProcessId(PROCESS_ID))
                .setTarget(new ProcessRef()
                        .setContainerId(TARGET_CONTAINER_ID)
                        .setProcessId(PROCESS_ID));

        String result = given()
                .body(mapper.writeValueAsString(plan))
                .contentType(MediaType.APPLICATION_JSON)
                .auth()
                .basic(username, password)
                .post("/rest/plans")
                .then()
                .statusCode(HttpStatus.SC_CREATED)
                .extract()
                .body()
                .asString();
        Plan resultPlan = mapper.readValue(result, Plan.class);

        assertThat(resultPlan.getId(), notNullValue());
        assertThat(resultPlan.getName(), is(plan.getName()));
        assertThat(resultPlan.getDescription(), is(plan.getDescription()));
        assertThat(resultPlan.getSource().getContainerId(), is(resultPlan.getSource().getContainerId()));
        assertThat(resultPlan.getSource().getProcessId(), is(resultPlan.getSource().getProcessId()));
        assertThat(resultPlan.getTarget().getContainerId(), is(resultPlan.getTarget().getContainerId()));
        assertThat(resultPlan.getTarget().getProcessId(), is(resultPlan.getTarget().getProcessId()));

        return resultPlan;
    }

}
