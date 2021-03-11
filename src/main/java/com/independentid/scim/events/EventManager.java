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

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.op.Operation;
import com.independentid.scim.resource.TransactionRecord;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
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

    public static final String PARAM_KAFKA_BOOTSTRAP = "scim.kafka.bootstrap";

    @ConfigProperty(name = "scim.event.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "scim.event.issuer", defaultValue = "default.issuer.exceptionalid.com")
    String issuer;

    @ConfigProperty(name = "scim.event.audiences", defaultValue = "default.audience.exceptionalid.com")
    String[] audiences;

    @ConfigProperty(name = "scim.event.expiredays", defaultValue = "365")
    int expDays;

    @ConfigProperty(name = "scim.event.types", defaultValue="replication")
    String[] types;

    @ConfigProperty(name = "scim.event.signedTypes", defaultValue = "replication=true")
    String[] signedTypes;

    @ConfigProperty(name = "scim.event.signKey", defaultValue = "/certs/signKeyjwks.json")
    String signedKeyPath;

    @ConfigProperty(name = "scim.event.encryptedTypes", defaultValue = "None")
    String[] encryptedTypes;

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

    IScimProvider provider;

    //static List<Operation> queue = Collections.synchronizedList(new ArrayList<Operation>());

    public EventManager() {

    }

    @PostConstruct
    public void init() {
        if (isEnabled()) {
            logger.info("Event Manager Started.");
            provider = backendHandler.getProvider();
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

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String[] getAudiences() {
        return audiences;
    }

    public void setAudiences(String[] audiences) {
        this.audiences = audiences;
    }

    public int getExpDays() {
        return expDays;
    }

    public void setExpDays(int expDays) {
        this.expDays = expDays;
    }

    public String[] getTypes() {
        return types;
    }

    public void setTypes(String[] types) {
        this.types = types;
    }

    public String[] getSignedTypes() {
        return signedTypes;
    }

    public void setSignedTypes(String[] signedTypes) {
        this.signedTypes = signedTypes;
    }

    public String getSignedKeyPath() {
        return signedKeyPath;
    }

    public void setSignedKeyPath(String signedKeyPath) {
        this.signedKeyPath = signedKeyPath;
    }

    public String[] getEncryptedTypes() {
        return encryptedTypes;
    }

    public void setEncryptedTypes(String[] encryptedTypes) {
        this.encryptedTypes = encryptedTypes;
    }

    /**
     * Called by the servlet (e.g. {@link com.independentid.scim.server.ScimV2Servlet}) after the operation is executed.
     * @param op The {@link Operation} that was performed (not complete or errored)
     */
    public void logEvent(Operation op) {
        try {
            if (isEnabled()) {
                TransactionRecord rec = new TransactionRecord(schemaManager,clientId,op);

                PublishOperation pop = new PublishOperation(rec,handlers.iterator(), provider);
                poolManager.addPublishOperation(pop);
            }
        } catch (SchemaException e) {
           //ignore - should not happen.
            logger.error("Unexpected error creating transaction record: "+e.getLocalizedMessage(),e);
        }

    }

}
