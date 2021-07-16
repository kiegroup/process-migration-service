package org.kie.processmigration.test;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ContainerKieServerLifecycleManager implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerKieServerLifecycleManager.class);
    private static final String URL = "http://%s:%s/kie-server/services/rest/server";
    private static final String CONTAINER_IMAGE = System.getProperty("kieserver.container.name", "jboss/kie-server");
    private static final String CONTAINER_TAG = System.getProperty("kieserver.container.tag", "latest");

    private final GenericContainer container;

    public ContainerKieServerLifecycleManager() {
        LOGGER.info("Trying to create container for: {}/{}", CONTAINER_IMAGE, CONTAINER_TAG);
        this.container = new GenericContainer(DockerImageName.parse(CONTAINER_IMAGE + ":" + CONTAINER_TAG))
                .withExposedPorts(8080);
    }

    @Override
    public Map<String, String> start() {
        try {
            container.start();
            container.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger(container.getContainerName())));
            //TODO: Wait for kieserver to be available
            Map<String, String> props = new HashMap<>();
            props.put("kieservers[0].host", String.format(URL, container.getHost(), container.getFirstMappedPort()));
            props.put("kieservers[0].username", "kieserver");
            props.put("kieservers[0].password", "kieserver1!");
            return props;
        } catch (IllegalStateException e) {
            LOGGER.warn("Unable to start Docker container for: {}/{}", CONTAINER_IMAGE, CONTAINER_TAG, e);
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
