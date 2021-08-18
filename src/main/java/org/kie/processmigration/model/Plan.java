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

import java.util.HashMap;
import java.util.Map;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

@Entity
@Table(name = "plans")
@SequenceGenerator(name = "planIdSeq", sequenceName = "PLAN_ID_SEQ")
@EqualsAndHashCode(callSuper = false)
@ToString
@Accessors(chain = true)
@Getter
@Setter
public class Plan extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "migrationIdSeq")
    @EqualsAndHashCode.Exclude
    private Long id;

    @NotBlank
    private String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String description;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "containerId", column = @Column(name = "source_container_id")),
            @AttributeOverride(name = "processId", column = @Column(name = "source_process_id")),
    })
    @NotNull
    @Valid
    private ProcessRef source;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "containerId", column = @Column(name = "target_container_id")),
            @AttributeOverride(name = "processId", column = @Column(name = "target_process_id")),
    })
    @NotNull
    @Valid
    private ProcessRef target;

    @ElementCollection(fetch = FetchType.EAGER)
    @MapKeyColumn(name = "source")
    @Column(name = "target")
    @CollectionTable(
            name = "plan_mappings",
            joinColumns = @JoinColumn(name = "plan_id")
    )
    private Map<String, String> mappings = new HashMap<>();

    public Plan copy(Plan plan) {
        this.name = plan.name;
        this.description = plan.description;
        this.source = plan.source;
        this.target = plan.target;
        this.mappings = plan.mappings;
        return this;
    }
}
