/*
 * Copyright 2021.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.server;

import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.schema.SchemaManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Liveness allows devops to interrogate if the server is instantiated. It does not test readiness.
 */
@Liveness
@Singleton
public class i2scimLivenessCheck implements HealthCheck {
    private final Logger logger = LoggerFactory.getLogger(i2scimLivenessCheck.class);
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
