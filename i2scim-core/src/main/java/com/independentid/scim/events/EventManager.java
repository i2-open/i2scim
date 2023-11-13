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

package com.independentid.scim.events;

import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.op.Operation;
import com.independentid.scim.resource.TransactionRecord;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    BackendHandler backendHandler = BackendHandler.getInstance();

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
        if (!isEnabled()) {
            logger.warn("Event Manager *DISABLED*.");
            return;
        }
        logger.info("Event Manager starting.");
        for (IEventHandler handler : handlers) {
            // Do this to ensure the handlers are instantiated.
            if (handler.isProducing()) {
                logger.info("  loaded Event Handler: " + handler.getClass().getName());
            }
        }

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
     * Called by the servlet (e.g. {ScimV2Servlet}) after the operation is executed.
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
