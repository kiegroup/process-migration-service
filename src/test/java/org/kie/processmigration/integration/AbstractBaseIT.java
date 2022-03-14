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

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;

abstract class AbstractBaseIT {

    protected static final String GROUP_ID = "com.myspace.test";
    protected static final String ARTIFACT_ID = "test";
    protected static final String CONTAINER_ID = "test";

    protected static final String PIM_ENDPOINT = System.getProperty("pim.http.url");
    protected static final String PIM_USERNAME = System.getProperty("pim.username");
    protected static final String PIM_PASSWORD = System.getProperty("pim.password");

    protected static final String KIE_SERVER_ID = System.getProperty("kie.server.id");
    protected static final String KIE_ENDPOINT = System.getProperty("kie.server.http.url");
    protected static final String KIE_USERNAME = System.getProperty("kie.server.username");
    protected static final String KIE_PASSWORD = System.getProperty("kie.server.password");

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = PIM_ENDPOINT;
    }
}
