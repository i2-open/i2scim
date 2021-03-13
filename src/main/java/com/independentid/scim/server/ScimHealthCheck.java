/*
 * Copyright (c) 2021.
 *
 * Confidential and Proprietary
 *
 * This unpublished source code may not be distributed outside
 * “Independent Identity Org”. without express written permission of
 * Phillip Hunt.
 *
 * People at companies that have signed necessary non-disclosure
 * agreements may only distribute to others in the company that are
 * bound by the same confidentiality agreement and distribution is
 * subject to the terms of such agreement.
 */

package com.independentid.scim.server;

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.schema.SchemaManager;
import org.eclipse.microprofile.health.*;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.text.ParseException;

@Readiness
@Singleton
public class ScimHealthCheck implements HealthCheck {

    @Inject
    @Resource(name="ConfigMgr")
    ConfigMgr configMgr;

    @Inject
    @Named("PoolMgr")
    PoolManager poolManager;

    @Inject
    SchemaManager schemaManager;

    @Inject
    BackendHandler handler;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder =
                HealthCheckResponse.named("SCIM.server");
        IScimProvider provider;
        boolean providerReady = false;
        String providerClass = "<UNDEFINED>";
        try {
            provider = handler.getProvider();
            providerClass = provider.getClass().toString();
            PersistStateResource cfgState = provider.getConfigState();
            if (cfgState != null)
                providerReady = true;

        } catch (ScimException | IOException | ParseException e) {
            responseBuilder = responseBuilder
                    .withData("scim.error.exceptionMessage","Exception polling backend: "+e.getLocalizedMessage())
                    .withData("scim.provider.bean",providerClass)
                    .withData("scim.provider.ready",providerReady);
            return responseBuilder.down().build();

        }



        responseBuilder = responseBuilder
                .withData("scim.provider.bean",providerClass)
                .withData("scim.provider.ready",providerReady)
                .withData("scim.backend.handlerStatus",handler.isReady())
                .withData("scim.poolmanager.poolPendingCnt",poolManager.getPendingTasksCnt())
                .withData("scim.schemaManager.resourceTypeCnt",schemaManager.getResourceTypeCnt())
                .withData("scim.schemaManager.schemaCnt",schemaManager.getSchemaCnt())
                .withData("scim.security.enable", configMgr.isSecurityEnabled())
                .withData("scim.security.authen.jwt", configMgr.isAuthJwt())
                .withData("scim.security.authen.basic", configMgr.isAuthBasic());

        return responseBuilder.up().build();


    }
}
