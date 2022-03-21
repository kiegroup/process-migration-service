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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;
import org.appformer.maven.integration.MavenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.processmigration.model.Execution;
import org.kie.processmigration.model.Migration;
import org.kie.processmigration.model.MigrationDefinition;
import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.ProcessRef;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieServiceResponse;
import org.kie.server.api.model.ReleaseId;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.RestAssured;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProcessMigrationIT extends AbstractBaseIT {

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    private static final String PROCESS_ID = "test.myprocess";
    private static final String SOURCE_CONTAINER_ID = "test_1.0.0";
    private static final String TARGET_CONTAINER_ID = "test_2.0.0";

    private KieServicesClient kieClient;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void deployProcesses() throws IOException {
        kieClient = createClient();
        KieServices ks = KieServices.Factory.get();
        MavenRepository repo = MavenRepository.getMavenRepository();
        for (String version : List.of("1.0.0", "2.0.0")) {

            org.kie.api.builder.ReleaseId builderReleaseId = ks.newReleaseId(GROUP_ID, ARTIFACT_ID, version);
            File kjar = readFile(CONTAINER_ID + "-" + version + ".jar");
            File pom = readFile(CONTAINER_ID + "-" + version + ".pom");
            repo.installArtifact(builderReleaseId, kjar, pom);

            ReleaseId releaseId = new ReleaseId(GROUP_ID, ARTIFACT_ID, version);
            KieContainerResource resource = new KieContainerResource(CONTAINER_ID, releaseId);
            ServiceResponse<KieContainerResource> response = kieClient.createContainer(CONTAINER_ID + "_" + version, resource);
            assertThat(response.getType(), is(KieServiceResponse.ResponseType.SUCCESS));
        }
    }

    @Test
    void testBasicMigration() throws IOException {
        // Given
        startProcesses();

        // When
        createMigration();

        // Then
        ProcessServicesClient processClient = kieClient.getServicesClient(ProcessServicesClient.class);
        List<ProcessInstance> instances = processClient.findProcessInstances(SOURCE_CONTAINER_ID, 0, 10);
        assertThat(instances, hasSize(1));
        assertThat(instances.get(0).getId(), is(2L));
        assertCount(1, KIE_SERVER_ID, SOURCE_CONTAINER_ID);
        instances = processClient.findProcessInstances(TARGET_CONTAINER_ID, 0, 10);
        assertThat(instances, hasSize(1));
        assertThat(instances.get(0).getId(), is(1L));
        assertCount(1, KIE_SERVER_ID, TARGET_CONTAINER_ID);
    }

    private void startProcesses() {
        ProcessServicesClient client = kieClient.getServicesClient(ProcessServicesClient.class);
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
                .basic(PIM_USERNAME, PIM_PASSWORD)
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
        assertThat(migration.getDefinition().getRequester(), is(PIM_USERNAME));
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
                .basic(PIM_USERNAME, PIM_PASSWORD)
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

    private void assertCount(int size, String kieServerId, String containerId) {
        given().auth()
                .basic(PIM_USERNAME, PIM_PASSWORD)
                .get("/rest/kieservers/" + kieServerId + "/instances/" + containerId)
                .then()
                .statusCode(200)
                .header("X-Total-Count", String.valueOf(size));
    }

    private KieServicesClient createClient() {
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(KIE_ENDPOINT, KIE_USERNAME, KIE_PASSWORD);
        configuration.setTimeout(60000);
        configuration.setMarshallingFormat(MarshallingFormat.JSON);
        return KieServicesFactory.newKieServicesClient(configuration);
    }

    private File readFile(String resource) throws IOException {
        File tmpFile = new File(resource);
        tmpFile.deleteOnExit();
        try (OutputStream os = new FileOutputStream(tmpFile)) {
            InputStream is = ProcessMigrationIT.class.getResource("/kjars/" + resource).openStream();
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            os.write(buffer);
        }
        return tmpFile;
    }
}
