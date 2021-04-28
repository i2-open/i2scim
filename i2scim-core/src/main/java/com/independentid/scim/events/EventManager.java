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

package com.independentid.scim.events;

import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.op.Operation;
import com.independentid.scim.resource.TransactionRecord;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.server.ScimV2Servlet;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Startup
@Singleton
@Named("EventManager")
public class EventManager {
    private final static Logger logger = LoggerFactory.getLogger(EventManager.class);

    @ConfigProperty(name = "scim.event.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "scim.event.audiences", defaultValue = "default.audience.exceptionalid.com")
    String[] audiences;

    @ConfigProperty(name = "scim.event.types", defaultValue="replication")
    String[] types;

    @ConfigProperty (name= "scim.kafkaRepEventHandler.client.id", defaultValue="defaultClient")
    String clientId;

    @Inject
    PoolManager poolManager;

    @Inject
    Instance<IEventHandler> handlers;

    @Inject
    BackendHandler backendHandler;

    @Inject
    SchemaManager schemaManager;

    //static List<Operation> queue = Collections.synchronizedList(new ArrayList<Operation>());
    private static EventManager self = null;

    public EventManager() {
        if (self == null)
            self = this;
    }

    /**
     * Used by Operations to invoke EventManager
     * @return The singleton EventManager instance when CDI not available (ie. Operation classes)
     */
    public static EventManager getInstance() { return self; }

    @PostConstruct
    public void init() {
        if (isEnabled()) {
            logger.info("Event Manager Started.");
            return;
        }
       logger.warn("Event Manager *DISABLED*.");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (isEnabled())
            logger.info("Event Manager restarted.");
        else
            logger.warn("Event Manager *DISABLED*.");
    }

    public String[] getAudiences() {
        return audiences;
    }

    public void setAudiences(String[] audiences) {
        this.audiences = audiences;
    }

    public String[] getTypes() {
        return types;
    }

    public void setTypes(String[] types) {
        this.types = types;
    }

    /**
     * Called by the servlet (e.g. {@link ScimV2Servlet}) after the operation is executed.
     * @param op The {@link Operation} that was performed (not complete or errored)
     */
    public void publishEvent(Operation op) {
        try {
            if (isEnabled()) {
                TransactionRecord rec = new TransactionRecord(schemaManager,clientId,op);

                PublishOperation pop = new PublishOperation(rec,handlers.iterator(), backendHandler);
                poolManager.addPublishOperation(pop);
            }
        } catch (SchemaException e) {
           //ignore - should not happen.
            logger.error("Unexpected error creating transaction record: "+e.getLocalizedMessage(),e);
        }

    }

}
