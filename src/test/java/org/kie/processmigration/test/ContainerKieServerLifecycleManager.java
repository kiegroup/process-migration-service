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

package org.kie.processmigration.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.appformer.maven.integration.MavenRepository;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class ContainerKieServerLifecycleManager implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerKieServerLifecycleManager.class);
    private static final String URL = "http://%s:%s/kie-server/services/rest/server";
    private static final String CONTAINER_IMAGE = System.getProperty("kieserver.container.name", "jboss/kie-server-showcase");
    private static final String CONTAINER_TAG = System.getProperty("kieserver.container.tag", "latest");

    public static final String GROUP_ID = "com.myspace.test";
    public static final String ARTIFACT_ID = "test";
    public static final String CONTAINER_ID = "test";
    public static final String KIE_SERVER_ID = "pim-kie-server";

    private final GenericContainer container;

    public ContainerKieServerLifecycleManager() throws IOException {
        LOGGER.info("Trying to create container for: {}:{}", CONTAINER_IMAGE, CONTAINER_TAG);

        KieServices ks = KieServices.Factory.get();
        MavenRepository repo = MavenRepository.getMavenRepository();
        for (String version : List.of("1.0.0", "2.0.0")) {
            ReleaseId builderReleaseId = ks.newReleaseId(GROUP_ID, ARTIFACT_ID, version);
            File kjar = readFile(CONTAINER_ID + "-" + version + ".jar");
            File pom = readFile(CONTAINER_ID + "-" + version + ".pom");
            repo.installArtifact(builderReleaseId, kjar, pom);
        }

        this.container = new GenericContainer(DockerImageName.parse(CONTAINER_IMAGE + ":" + CONTAINER_TAG))
                .withExposedPorts(8080)
                .withEnv("KIE_SERVER_ID", KIE_SERVER_ID)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(System.getProperty("user.home") + "/.m2/repository/com/myspace/test/test/"),
                        "/opt/jboss/.m2/repository/com/myspace/test/test/");
    }

    private File readFile(String resource) throws IOException {
        File tmpFile = new File(resource);
        tmpFile.deleteOnExit();
        try (OutputStream os = new FileOutputStream(tmpFile);
            InputStream is = ContainerKieServerLifecycleManager.class.getResource("/kjars/" + resource).openStream()) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            os.write(buffer);
        }
        return tmpFile;
    }


    @Override
    public Map<String, String> start() {
        try {
            container.start();
            container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(container.getContainerName())));
            Map<String, String> props = new HashMap<>();
            props.put("kieservers[0].host", String.format(URL, container.getHost(), container.getFirstMappedPort()));
            props.put("kieservers[0].username", "kieserver");
            props.put("kieservers[0].password", "kieserver1!");
            return props;
        } catch (Exception e) {
            LOGGER.warn("Unable to start Docker container for: {}:{}", CONTAINER_IMAGE, CONTAINER_TAG, e);
        }
        Iterator<String> propNames = ConfigProvider.getConfig().getPropertyNames().iterator();
        while (propNames.hasNext()) {
            if (propNames.next().toLowerCase().startsWith("kieservers")) {
                return null;
            }
        }
        throw new IllegalStateException("Unable to proceed with Integration tests. Either provide a valid Docker environment or the configuration for an existing kie server");
    }

    @Override
    public void stop() {
        if (container.isRunning()) {
            container.stop();
        }
    }
}
