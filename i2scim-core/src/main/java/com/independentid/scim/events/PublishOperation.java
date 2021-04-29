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
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.err.DuplicateTxnException;
import com.independentid.scim.resource.TransactionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.RecursiveAction;

/**
 * This class is used to implement a runnable that will sequence through IEventHandlers discovered to publish events.
 * This runnable is invoked by the {@link EventManager}.
 */
public class PublishOperation extends RecursiveAction {
    private final static Logger logger = LoggerFactory.getLogger(PublishOperation.class);

    TransactionRecord rec;
    BackendHandler backendHandler;
    Iterator<IEventHandler> handlers;

    protected PublishOperation(TransactionRecord rec, Iterator<IEventHandler> handlerIterator, BackendHandler handler) {
        this.rec = rec;
        backendHandler = handler;
        handlers = handlerIterator;
    }

    @Override
    protected void compute() {
        try {
            backendHandler.storeTransactionRecord(rec);
        } catch (DuplicateTxnException e) {
            logger.error("Error publishing transaction record("+rec.getId()+"). Event will not be published.");
            return;
        }

        while (handlers.hasNext())
            handlers.next().publish(rec.getOp());
    }
}
