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
import com.independentid.scim.core.err.DuplicateTxnException;
import com.independentid.scim.op.Operation;
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
            logger.error("Duplicate transaction (" + rec.getId() + ") will not be published.");
            return;
        }

        while (handlers.hasNext()) {
            IEventHandler handler = handlers.next();
            Operation op = rec.getOp();
            handler.publish(op);

        }
    }
}
