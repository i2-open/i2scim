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

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.op.Operation;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

public interface IEventHandler {
    @PostConstruct
    void init();

    /**
     * Takes a JsonNode input and interprets it and then may generate a SCIM {@link Operation} if action
     * is to be taken.
     * @param node A JsonNode parsed event
     */
    void consume(JsonNode node);

    /**
     * Takes a processed SCIM Operation and publishes it
     * @param op A processed SCIM Operation to be published
     */
    void publish(Operation op);

    /**
     * Indicates if the producer is working.
     * @return true if the handler is producing events (no error state)
     */
    boolean isProducing();

    @PreDestroy
    void shutdown();
}
