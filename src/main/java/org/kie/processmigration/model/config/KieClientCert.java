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

package org.kie.processmigration.model.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigMapping(prefix = "client-cert")
public interface KieClientCert {

    @WithParentName
    Optional<ClientCertConfig> clientCert();

    interface ClientCertConfig {
        @WithName("cert-name")
        String certName();

        @WithName("cert-credentials-provider")
        Optional<String> certCredentialsProvider();

        @WithName("cert-password")
        Optional<String> certPassword();

        @WithName("keystore-path")
        String keystorePath();

        @WithName("keystore-credentials-provider")
        Optional<String> keystoreCredentialsProvider();

        @WithName("keystore-password")
        Optional<String> keystorePassword();

        @WithName("truststore-path")
        String truststorePath();

        @WithName("truststore-credentials-provider")
        Optional<String> truststoreCredentialsProvider();

        @WithName("truststore-password")
        Optional<String> truststorePassword();
    }


}
