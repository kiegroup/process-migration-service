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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;

import org.kie.processmigration.model.BpmNode;
import org.kie.processmigration.model.KieServerConfig;
import org.kie.processmigration.model.ProcessInfo;
import org.kie.processmigration.model.ProcessRef;
import org.kie.processmigration.model.RunningInstance;
import org.kie.processmigration.model.config.KieClientCert;
import org.kie.processmigration.model.config.KieServers;
import org.kie.processmigration.model.exceptions.CredentialsException;
import org.kie.processmigration.model.exceptions.InvalidKieServerException;
import org.kie.processmigration.model.exceptions.ProcessDefinitionNotFoundException;
import org.kie.processmigration.service.KieService;
import org.kie.server.api.exception.KieServicesHttpException;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResourceList;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.definition.ProcessDefinition;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.UIServicesClient;
import org.kie.server.client.admin.ProcessAdminServicesClient;
import org.kie.server.client.credentials.EnteredCredentialsProvider;
import org.kie.server.client.credentials.EnteredTokenCredentialsProvider;
import org.kie.server.common.rest.ClientCertificate;
import org.kie.server.common.rest.NoEndpointFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.credentials.CredentialsProvider;
import io.quarkus.credentials.runtime.CredentialsProviderFinder;
import io.quarkus.runtime.Startup;

@ApplicationScoped
@Startup
public class KieServiceImpl implements KieService {

    private static final long CONFIGURATION_TIMEOUT = 60000;
    private static final Integer DEFAULT_PAGE_SIZE = 100;
    private static final long AWAIT_EXECUTOR = 5;
    private static final long RETRY_DELAY = 2;
    private static final Logger logger = LoggerFactory.getLogger(KieServiceImpl.class);
    private static final String CREDENTIALS_PROVIDER_USER_KEY = "user";
    private static final String CREDENTIALS_PROVIDER_PASSWORD_KEY = "password";

    private CredentialsProvider credentialsProvider = CredentialsProviderFinder.find("quarkus.file.vault");
    final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    final Collection<KieServerConfig> configs = new ArrayList<>();

    @Inject
    KieServers kieServers;

    @Inject
    KieClientCert cert;

    @PostConstruct
    void loadConfigs() {
        if (kieServers.kieservers() != null && !kieServers.kieservers().isEmpty()) {
            kieServers.kieservers().forEach(this::loadConfig);
        }
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(AWAIT_EXECUTOR, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    @Override
    public Collection<KieServerConfig> getConfigs() {
        return Collections.unmodifiableCollection(configs);
    }

    @Override
    public boolean hasKieServer(String kieServerId) {
        return configs
                .stream()
                .anyMatch(config -> config.getId() != null && config.getId().equals(kieServerId));
    }

    @Override
    public KieServicesClient getClient(String kieServerId) throws InvalidKieServerException {
        return configs.stream()
                .filter(config -> kieServerId.equals(config.getId()))
                .findFirst()
                .orElseThrow(() -> new InvalidKieServerException(kieServerId))
                .getClient();
    }

    @Override
    public ProcessAdminServicesClient getProcessAdminServicesClient(String kieServerId) throws
            InvalidKieServerException {
        return getClient(kieServerId).getServicesClient(ProcessAdminServicesClient.class);
    }

    @Override
    public QueryServicesClient getQueryServicesClient(String kieServerId) throws InvalidKieServerException {
        return getClient(kieServerId).getServicesClient(QueryServicesClient.class);
    }

    @Override
    public List<RunningInstance> getRunningInstances(String kieServerId, String containerId, Integer page, Integer
            pageSize) throws InvalidKieServerException {
        ProcessServicesClient processServicesClient = getProcessServicesClient(kieServerId);
        List<ProcessInstance> instanceList = processServicesClient.findProcessInstances(containerId, page, pageSize);

        int i = 0;
        List<RunningInstance> result = new ArrayList<>();
        for (ProcessInstance instance : instanceList) {
            i++;
            result.add(new RunningInstance(i, instance));
        }

        return result;
    }

    @Override
    public Map<String, Set<String>> getDefinitions(String kieServerId) throws InvalidKieServerException {
        Map<String, Set<String>> definitions = new HashMap<>();
        ServiceResponse<KieContainerResourceList> response = getClient(kieServerId).listContainers();
        QueryServicesClient queryServicesClient = getQueryServicesClient(kieServerId);
        response.getResult().getContainers().forEach(container -> {
            if (!definitions.containsKey(container.getContainerId())) {
                definitions.put(container.getContainerId(), new HashSet<>());
            }
            boolean finished = false;
            int page = 0;
            while (!finished) {
                List<ProcessDefinition> processes = queryServicesClient.findProcessesByContainerId(container.getContainerId(), page++, DEFAULT_PAGE_SIZE);
                if (processes.size() < DEFAULT_PAGE_SIZE) {
                    finished = true;
                }
                processes.forEach(definition -> definitions.get(container.getContainerId()).add(definition.getId()));
            }
        });
        return definitions;
    }

    @Override
    public boolean existsProcessDefinition(String kieServerId, ProcessRef processRef) throws
            InvalidKieServerException {
        QueryServicesClient queryService = getQueryServicesClient(kieServerId);
        return queryService.findProcessByContainerIdProcessId(processRef.getContainerId(), processRef.getProcessId()) != null;
    }

    @Override
    public ProcessInfo getDefinition(String kieServerId, ProcessRef processRef) throws
            ProcessDefinitionNotFoundException, InvalidKieServerException {
        ProcessInfo processInfo = new ProcessInfo();

        //get SVG file
        String svgFile;
        try {
            svgFile = getUIServicesClient(kieServerId).getProcessImage(processRef.getContainerId(), processRef.getProcessId());
        } catch (KieServicesHttpException e) {
            if (Response.Status.NOT_FOUND.getStatusCode() == e.getHttpCode()) {
                logger.debug("Process definition {} not found in {}", processRef, kieServerId);
                throw new ProcessDefinitionNotFoundException(kieServerId, processRef);
            } else {
                logger.warn("Unable to fetch SVG file from {}", kieServerId, e);
                throw e;
            }
        }

        //Add this replacement here because in react-svgmt, ? and = are not allowed.
        svgFile = svgFile.replaceAll("\\?shapeType=BACKGROUND", "_shapeType_BACKGROUND");
        processInfo.setSvgFile(svgFile);

        ProcessDefinition pd = getProcessServicesClient(kieServerId).getProcessDefinition(processRef.getContainerId(), processRef.getProcessId());
        if (!pd.getContainerId().equals(processRef.getContainerId())) {
            throw new ProcessDefinitionNotFoundException(kieServerId, processRef);
        }
        List<BpmNode> nodes = new ArrayList<>();
        if (pd.getNodes() != null) {
            nodes = pd.getNodes()
                    .stream()
                    .map(n -> new BpmNode().setId(n.getUniqueId()).setName(n.getName()).setType(n.getType()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        processInfo.setNodes(nodes);
        processInfo.setContainerId(processRef.getContainerId());
        processInfo.setProcessId(processRef.getProcessId());
        return processInfo;
    }

    private void loadConfig(KieServers.KieServer config) {
        KieServerConfig kieConfig = new KieServerConfig().setHost(config.host());
        if (config.credentialsProvider().isPresent()) {
            String user = credentialsProvider.getCredentials(config.credentialsProvider().get()).get(CREDENTIALS_PROVIDER_USER_KEY);
            String password = credentialsProvider.getCredentials(config.credentialsProvider().get()).get(CREDENTIALS_PROVIDER_PASSWORD_KEY);
            if (user == null) {
                throw new CredentialsException("Missing credential in vault with key " + config.credentialsProvider().get());
            }
            kieConfig.setCredentialsProvider(new EnteredCredentialsProvider(user, password));
        } else if (config.username().isPresent() && config.password().isPresent()) {
            kieConfig.setCredentialsProvider(new EnteredCredentialsProvider(config.username().get(), config.password().get()));
        }
        if (config.token().isPresent()) {
            kieConfig.setCredentialsProvider(new EnteredTokenCredentialsProvider(config.token().get()));
        }
        try {
            KieServicesClient client = createKieServicesClient(kieConfig);
            if (client != null) {
                kieConfig.setClient(client);
                if (client.getServerInfo().getResult() != null) {
                    kieConfig
                            .setName(client.getServerInfo().getResult().getName())
                            .setId(client.getServerInfo().getResult().getServerId());
                }
            }
        } catch (Exception e) {
            logger.info("Unable to create kie server configuration for {}. Retry asynchronously", config);
            retryConnection(kieConfig);
        }
        configs.add(kieConfig);
        logger.info("Loaded kie server configuration for: {}", kieConfig);
    }

    private KieServicesClient createKieServicesClient(KieServerConfig config) {
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(config.getHost(), config.getCredentialsProvider());
        configuration.setTimeout(CONFIGURATION_TIMEOUT);
        configuration.setMarshallingFormat(MarshallingFormat.JSON);
        if (cert.clientCert().isPresent()) {
            configuration.setClientCertificate(new ClientCertificate()
                    .setCertName(cert.clientCert().get().certName())
                    .setCertPassword(resolvePassword(cert.clientCert().get().certCredentialsProvider(), cert.clientCert().get().certPassword()))
                    .setKeystore(cert.clientCert().get().keystorePath())
                    .setKeystorePassword(resolvePassword(cert.clientCert().get().keystoreCredentialsProvider(), cert.clientCert().get().keystorePassword()))
                    .setTruststore(cert.clientCert().get().truststorePath())
                    .setTruststorePassword(resolvePassword(cert.clientCert().get().truststoreCredentialsProvider(), cert.clientCert().get().truststorePassword())));
        }
        return KieServicesFactory.newKieServicesClient(configuration);
    }

    private String resolvePassword(Optional<String> credentialsProviderKey, Optional<String> passwordKey) {
        if(credentialsProviderKey.isPresent()) {
            String password = credentialsProvider.getCredentials(credentialsProviderKey.get()).get(CREDENTIALS_PROVIDER_PASSWORD_KEY);
            if (password == null) {
                throw new CredentialsException("Missing credential in vault with key " + credentialsProviderKey.get());
            }
            return password;
        }
        if(passwordKey.isEmpty()) {
            throw new CredentialsException("Either the password or the credentials-provider key must be defined");
        }
        return passwordKey.get();
    }

    private UIServicesClient getUIServicesClient(String kieServerId) throws InvalidKieServerException {
        return getClient(kieServerId).getServicesClient(UIServicesClient.class);
    }

    private ProcessServicesClient getProcessServicesClient(String kieServerId) throws InvalidKieServerException {
        return getClient(kieServerId).getServicesClient(ProcessServicesClient.class);
    }

    private void retryConnection(KieServerConfig kieConfig) {
        executorService.schedule(new KieServerClientConnector(kieConfig), RETRY_DELAY, TimeUnit.SECONDS);
    }

    /*
     * Runnable for checks on failed endpoints
     */
    class KieServerClientConnector implements Runnable {

        final KieServerConfig kieConfig;

        KieServerClientConnector(KieServerConfig kieConfig) {
            this.kieConfig = kieConfig;
        }

        @Override
        public void run() {
            logger.debug("Trying to create KieServerClient for {}", kieConfig);
            if (kieConfig.getClient() == null) {
                try {
                    kieConfig.setClient(createKieServicesClient(kieConfig));
                } catch (NoEndpointFoundException e) {
                    logger.warn("Unable to connect to KieServer: {}. The client will try to reconnect in the background", kieConfig);
                } catch (Exception e) {
                    logger.warn("Unable to create KieServer client: {}", kieConfig, e);
                } finally {
                    if (kieConfig.getClient() == null) {
                        logger.debug("KieServerClient for {} could not be created. Retrying...", kieConfig);
                        retryConnection(kieConfig);
                    } else {
                        logger.debug("KieServerClient for {} created.", kieConfig);
                    }
                }
            }
        }
    }
}
