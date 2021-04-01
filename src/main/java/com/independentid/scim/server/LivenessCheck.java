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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.*;

import javax.annotation.Resource;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.text.ParseException;

/**
 * Liveness allows devops to interrogate if the server is instantiated. It does not test readiness.
 */
@Liveness
@Singleton
public class LivenessCheck implements HealthCheck {

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

    @ConfigProperty(name = "scim.prov.providerClass", defaultValue="com.independentid.scim.backend.mongo.MongoProvider")
    String providerName;

    @Inject
    Instance<IScimProvider> providers;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder =
                HealthCheckResponse.named("SCIM.server");

        if (handler == null | schemaManager == null | configMgr == null) {
            responseBuilder.withData("scim.error.missing","One or manager classes missing or not injected (Config, Schema, BackendHandler).");
            return responseBuilder.down().build();
        }
        boolean provLoaded = false;
        for (IScimProvider item : providers) {
            String cname = item.getClass().toString();
            if (cname.contains(providerName)) {
                provLoaded = true;
                break;
            }
        }

        responseBuilder = responseBuilder
                .withData("scim.provider.bean",providerName)
                .withData("scim.provider.loaded",provLoaded)
                .withData("scim.backend.handlerStatus",handler.isReady())
                .withData("scim.poolmanager.poolPendingCnt",poolManager.getPendingTasksCnt())
                .withData("scim.schemaManager.resourceTypeCnt",schemaManager.getResourceTypeCnt())
                .withData("scim.schemaManager.schemaCnt",schemaManager.getSchemaCnt())
                .withData("scim.security.enable", configMgr.isSecurityEnabled())
                .withData("scim.security.authen.jwt", configMgr.isAuthJwt())
                .withData("scim.security.authen.basic", configMgr.isAuthBasic());

        if (provLoaded)
            return responseBuilder.up().build();
        return responseBuilder.down().build();

    }
}
