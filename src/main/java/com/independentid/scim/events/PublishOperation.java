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

import com.independentid.scim.op.Operation;

import java.util.Iterator;
import java.util.concurrent.RecursiveAction;

/**
 * This class is used to implement a runnable that will sequence through IEventHandlers discovered to publish events.
 * This runnable is invoked by the {@link EventManager}.
 */
public class PublishOperation extends RecursiveAction {
    Operation op;
    Iterator<IEventHandler> handlers;

    protected PublishOperation(Operation operation, Iterator<IEventHandler> handlerIterator) {
        op = operation;
        handlers = handlerIterator;
    }

    @Override
    protected void compute() {
        while (handlers.hasNext())
            handlers.next().process(op);

    }
}
