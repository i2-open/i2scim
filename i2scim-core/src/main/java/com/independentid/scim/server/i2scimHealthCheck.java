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

import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.schema.SchemaManager;
import org.eclipse.microprofile.health.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.text.ParseException;

/**
 * This check verifies if i2scim is ready to serve requests. It checks to see the provider is initialized.
 *
 * Note: at this time, it does not check whether the provider is still functional
 */
@Readiness
@Singleton
public class i2scimHealthCheck implements org.eclipse.microprofile.health.HealthCheck {
    private final Logger logger = LoggerFactory.getLogger(i2scimHealthCheck.class);
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
                HealthCheckResponse.named("i2scim.server");
        IScimProvider provider;
        boolean providerReady = false;
        String providerClass = "<UNDEFINED>";
        try {
            provider = handler.getProvider();
            providerClass = provider.getClass().toString();
            if (!handler.isReady()) {
                responseBuilder = responseBuilder
                        .withData("scim.provider.bean",providerClass)
                        .withData("scim.provider.ready",false);
                return responseBuilder.down().build();
            }
            PersistStateResource cfgState = provider.getConfigState();
            if (cfgState != null)
                providerReady = true;

        } catch (ScimException | IOException | ParseException e) {
            logger.error("Health check failed: "+e.getMessage());
            responseBuilder = responseBuilder
                    .withData("scim.error.exceptionMessage","Exception polling backend: "+e.getLocalizedMessage())
                    .withData("scim.provider.bean",providerClass)
                    .withData("scim.provider.ready",false);
            return responseBuilder.down().build();

        }

        logger.info("Health check completed.");
        responseBuilder = responseBuilder
                .withData("scim.provider.class",providerClass)
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
