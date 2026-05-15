/*
 * Copyright 2026.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.independentid.scim.server;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class StartupBanner {

    private static final Logger logger = LoggerFactory.getLogger(StartupBanner.class);

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    void onStart(@Observes StartupEvent ev) {
        logger.info("i2scim server v{}", version);
    }
}
