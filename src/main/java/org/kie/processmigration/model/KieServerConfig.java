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

package org.kie.processmigration.model;

import org.kie.server.api.model.KieServerInfo;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.CredentialsProvider;
import org.kie.server.client.KieServicesClient;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@EqualsAndHashCode
@ToString
@Accessors(chain = true)
@Getter
@Setter
public class KieServerConfig {

    public static final String UNKNOWN_STATUS = "UNKNOWN";
    public static final String SUCCESS_STATUS = "SUCCESS";

    private String id;

    private String name;

    private String host;

    @JsonIgnore
    private CredentialsProvider credentialsProvider;

    @JsonIgnore
    private KieServicesClient client;

    public String getId() {
        if (client == null) {
            return null;
        }
        try {
            if (this.id == null) {
                ServiceResponse<KieServerInfo> info = client.getServerInfo();
                this.id = info.getResult().getServerId();
            }
        } catch (Exception e) {
            return null;
        }
        return id;
    }

    public String getStatus() {
        if (client == null) {
            return UNKNOWN_STATUS;
        }
        try {
            return client.getServerInfo().getType().name();
        } catch (Exception e) {
            return UNKNOWN_STATUS;
        }
    }

}
