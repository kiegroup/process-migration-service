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

import java.time.Instant;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@EqualsAndHashCode(callSuper = false)
@ToString
@Accessors(chain = true)
@Getter
@Setter
public class MigrationReportDto {

    private Long id;

    private Long migrationId;

    private Long processInstanceId;

    private Instant startDate;

    private Instant endDate;

    private Boolean successful;

    public MigrationReportDto() {}

    public MigrationReportDto(MigrationReport report) {
        this.id = report.getId();
        this.migrationId = report.getMigrationId();
        this.processInstanceId = report.getProcessInstanceId();
        this.startDate = report.getStartDate();
        this.endDate = report.getEndDate();
        this.successful = report.getSuccessful();
    }
}
