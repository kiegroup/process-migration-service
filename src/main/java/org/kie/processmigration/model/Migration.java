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
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.kie.processmigration.model.Execution.ExecutionStatus;
import org.kie.processmigration.model.Execution.ExecutionType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Entity
@Table(name = "migrations")
@SequenceGenerator(name = "migrationIdSeq", sequenceName = "MIGRATION_ID_SEQ")
@EqualsAndHashCode
@ToString
@Accessors(chain = true)
@Getter
@Setter
public class Migration extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "migrationIdSeq")
    private Long id;

    @Embedded
    private MigrationDefinition definition;

    @JsonInclude(Include.NON_NULL)
    @Column(name = "created_at")
    private Instant createdAt;

    @JsonInclude(Include.NON_NULL)
    @Column(name = "finished_at")
    private Instant finishedAt;

    @JsonInclude(Include.NON_NULL)
    @Column(name = "started_at")
    private Instant startedAt;

    @JsonInclude(Include.NON_NULL)
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @JsonInclude(Include.NON_NULL)
    @Column(name = "error_message")
    @Lob
    private String errorMessage;

    private ExecutionStatus status;

    @JsonIgnore
    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "migration_id")
    private List<MigrationReport> reports = new ArrayList<>();

    public Migration() {
    }

    public Migration(MigrationDefinition definition) {
        this.definition = definition;
        Instant now = Instant.now();
        createdAt = now;
        if (definition.getExecution() != null && ExecutionType.ASYNC.equals(definition.getExecution().getType()) &&
                definition.getExecution().getScheduledStartTime() != null &&
                now.isBefore(definition.getExecution().getScheduledStartTime())) {
            status = Execution.ExecutionStatus.SCHEDULED;
        } else {
            status = Execution.ExecutionStatus.CREATED;
        }
    }

    public Migration start() {
        startedAt = Instant.now();
        status = ExecutionStatus.STARTED;
        return this;
    }

    public Migration complete(Boolean hasErrors) {
        finishedAt = Instant.now();
        if (Boolean.TRUE.equals(hasErrors)) {
            status = ExecutionStatus.FAILED;
        } else {
            status = ExecutionStatus.COMPLETED;
        }
        return this;
    }

    public Migration cancel() {
        cancelledAt = Instant.now();
        status = ExecutionStatus.CANCELLED;
        return this;
    }

    public Migration fail(Exception e) {
        finishedAt = Instant.now();
        status = ExecutionStatus.FAILED;
        errorMessage = e.toString();
        return this;
    }
}
