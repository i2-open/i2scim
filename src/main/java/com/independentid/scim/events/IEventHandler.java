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

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.op.Operation;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

public interface IEventHandler {
    @PostConstruct
    void init();

    /**
     * Takes a JsonNode input and interprets it and then may generate a SCIM {@link Operation} if action
     * is to be taken.
     * @param node A JsonNode parsed event
     */
    void consume(JsonNode node);

    List<Operation> getReceivedOps();

    /**
     * Used to indicate if there are unprocessed received events.
     * @return true if the received queue is empty
     */
    boolean hasNoReceivedEvents();

    /**
     * Returns the list of operations not successfully processed.
     * @return an List of Operations that were not processed successfully.
     */
    List<Operation> getSendErrorOps();

    void process(Operation op);

    /**
     * Indicates if the producer is working.
     * @return true if the handler is producing events (no error state)
     */
    boolean isProducing();

    @PreDestroy
    void shutdown();
}
