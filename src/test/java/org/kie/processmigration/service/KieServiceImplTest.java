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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.processmigration.model.KieServerConfig;
import org.kie.processmigration.model.ProcessInfo;
import org.kie.processmigration.model.ProcessRef;
import org.kie.processmigration.model.RunningInstance;
import org.kie.processmigration.model.exceptions.InvalidKieServerException;
import org.kie.processmigration.model.exceptions.ProcessDefinitionNotFoundException;
import org.kie.processmigration.test.MockKieServerLifecycleManager;
import org.kie.processmigration.test.Profiles;
import org.kie.processmigration.test.TestKieServer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@TestProfile(Profiles.MockKieServerProfile.class)
class KieServiceImplTest {

    private static final String RETRY = "retry";
    private static final Collection<String> SUCCESS = List.of("kie-server-1", "kie-server-2");
    private static final Collection<String> FAILED = List.of("not-found", "unauthorized", "forbidden", RETRY);

    @TestKieServer
    MockKieServerLifecycleManager mockKieServer;

    @Inject
    KieService kieService;

    @AfterEach
    void resetServer() {
        mockKieServer.reset();
    }

    @Test
    void testRetryErroredConfigs() throws InterruptedException {
        assertThat(kieService.getConfigs(), hasSize(6));

        //Wait for retries to the error-ed servers every 2 seconds
        Thread.sleep(4000L);
        FAILED.stream().filter(n -> !RETRY.equals(n)).forEach(n -> mockKieServer.getWireMockServer()
                .verify(new CountMatchingStrategy(CountMatchingStrategy.GREATER_THAN_OR_EQUAL, 2),
                        getRequestedFor(urlPathEqualTo("/" + n + "/services/rest/server"))));
    }

    @Test
    void testGetConfigs() throws JsonProcessingException, InterruptedException {
        Collection<KieServerConfig> configs = kieService.getConfigs();
        assertThat(configs, hasSize(6));
        configs.stream()
                .filter(c -> SUCCESS.stream().anyMatch(n -> Objects.equals(c.getHost(), getExpectedKieServerHost(n))))
                .forEach(this::assertSuccessConfig);
        configs.stream()
                .filter(c -> FAILED.stream().anyMatch(n -> Objects.equals(c.getHost(), getExpectedKieServerHost(n))))
                .forEach(this::assertFailedConfig);

        mockKieServer.enableRetryServer();
        Thread.sleep(3000L);
        configs.stream()
                .filter(c -> Objects.equals(c.getHost(), getExpectedKieServerHost(RETRY)))
                .forEach(this::assertSuccessConfig);

    }

    @Test
    void testHasKieServer() {
        SUCCESS.forEach(n -> assertThat(n, kieService.hasKieServer(n), is(Boolean.TRUE)));
        FAILED.forEach(n -> assertThat(n, kieService.hasKieServer(n), is(Boolean.FALSE)));
    }

    @Test
    void testGetQueryServicesClient() {
        SUCCESS.forEach(n -> {
            try {
                assertThat(n, kieService.getQueryServicesClient(n), notNullValue());
            } catch (InvalidKieServerException e) {
                fail("Unexpected exception", e);
            }
        });
        FAILED.forEach(n -> assertThrows(InvalidKieServerException.class, () -> kieService.getQueryServicesClient(n)));
    }

    @Test
    void testGetProcessAdminServicesClient() {
        SUCCESS.forEach(n -> {
            try {
                assertThat(n, kieService.getProcessAdminServicesClient(n), notNullValue());
            } catch (InvalidKieServerException e) {
                fail("Unexpected exception", e);
            }
        });
        FAILED.forEach(n -> assertThrows(InvalidKieServerException.class, () -> kieService.getProcessAdminServicesClient(n)));
    }

    @Test
    void testGetDefinitions() throws InvalidKieServerException {
        assertThrows(InvalidKieServerException.class, () -> kieService.getDefinitions("not-found"));

        assertThat(kieService.getDefinitions("kie-server-1"), anEmptyMap());
        Map<String, Set<String>> definitions = kieService.getDefinitions("kie-server-2");
        assertThat(definitions, hasEntry(is("example-1.0.0"), contains("process1")));
        assertThat(definitions, hasEntry(is("example-1.0.1"), contains("process1", "process2")));
    }

    @Test
    void testGetDefinition() throws InvalidKieServerException, ProcessDefinitionNotFoundException {
        assertThrows(InvalidKieServerException.class, () -> kieService.getDefinition("not-found", new ProcessRef().setContainerId("foo").setProcessId("bar")));
        assertThrows(ProcessDefinitionNotFoundException.class, () -> kieService.getDefinition("kie-server-2", new ProcessRef().setContainerId("foo").setProcessId("bar")));

        ProcessInfo processInfo = kieService.getDefinition("kie-server-2", new ProcessRef().setContainerId("example-1.0.1").setProcessId("process2"));
        assertThat(processInfo, notNullValue());
        assertThat(processInfo.getContainerId(), is("example-1.0.1"));
        assertThat(processInfo.getProcessId(), is("process2"));
        assertThat(processInfo.getNodes(), hasSize(2));
        assertThat(processInfo.getSvgFile(), is("<test/>"));
    }

    @Test
    void testExistsProcessDefinition() throws InvalidKieServerException {
        assertThrows(InvalidKieServerException.class, () ->
                kieService.existsProcessDefinition("not-found", new ProcessRef().setProcessId("foo").setContainerId("bar")));
        assertThat(kieService.existsProcessDefinition("kie-server-2",
                new ProcessRef().setContainerId("example-1.0.1").setProcessId("process2")), is(Boolean.TRUE));
    }

    @Test
    void testGetRunningInstances() throws InvalidKieServerException {
        assertThrows(InvalidKieServerException.class, () -> kieService.getRunningInstances("not-found", "foo", 0, 10));
        List<RunningInstance> runningInstances = kieService.getRunningInstances("kie-server-2", "example-1.0.1", 0, 10);
        assertThat(runningInstances, hasSize(1));
        RunningInstance instance = runningInstances.get(0);
        assertThat(instance.getId(), is(1));
        assertThat(instance.getStartTime(), notNullValue());
        assertThat(instance.getProcessInstanceId(), is(9L));
    }

    private void assertSuccessConfig(KieServerConfig config) {
        assertThat(config.getId(), notNullValue());
        assertThat(config.getClient(), notNullValue());
        assertThat(config.getCredentialsProvider(), notNullValue());
        assertThat(config.getStatus(), is(KieServerConfig.SUCCESS_STATUS));
    }

    private void assertFailedConfig(KieServerConfig config) {
        assertThat(config.getId(), nullValue());
        assertThat(config.getClient(), nullValue());
        assertThat(config.getCredentialsProvider(), notNullValue());
        assertThat(config.getStatus(), is(KieServerConfig.UNKNOWN_STATUS));
    }

    private String getExpectedKieServerHost(String name) {
        return this.mockKieServer.getWireMockServer().baseUrl() + "/" + name + "/services/rest/server";
    }

}