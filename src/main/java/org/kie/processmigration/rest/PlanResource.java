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

package org.kie.processmigration.rest;

import org.kie.processmigration.model.Plan;
import org.kie.processmigration.model.exceptions.PlanNotFoundException;
import org.kie.processmigration.service.PlanService;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.Status.CREATED;

@Path("/plans")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PlanResource {

    @Inject
    PlanService planService;

    @GET
    public Response findAll() {
        return Response.ok(planService.findAll()).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) throws PlanNotFoundException {
        return Response.ok(planService.get(id)).build();
    }

    @POST
    public Response create(Plan plan) {
        if (plan.id != null) {
            throw new IllegalArgumentException("The plan ID must not be provided when creating a new plan");
        }
        return Response.status(CREATED).entity(planService.create(plan)).build();
    }

    @PUT
    @Path("/{id}")
    public Response save(@PathParam("id") Long id, Plan plan) throws PlanNotFoundException {
        return Response.ok(planService.update(id, plan)).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) throws PlanNotFoundException {
        return Response.ok(planService.delete(id)).build();
    }
}