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

import java.net.URI;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Embeddable
@EqualsAndHashCode
@ToString
@Accessors(chain = true)
@Getter
@Setter
public class Execution {

    public enum ExecutionType {
        ASYNC,
        SYNC
    }

    public enum ExecutionStatus {
        SCHEDULED,
        STARTED,
        COMPLETED,
        FAILED,
        CANCELLED,
        CREATED
    }

    @Column(name = "execution_type")
    private ExecutionType type;

    @JsonInclude(Include.NON_NULL)
    @Column(name = "callback_url")
    private URI callbackUrl;

    @JsonInclude(Include.NON_NULL)
    @Column(name = "scheduled_start_time")
    private Instant scheduledStartTime;

}
