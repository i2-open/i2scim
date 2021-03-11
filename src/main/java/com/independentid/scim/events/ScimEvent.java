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
import org.jose4j.jwt.JwtClaims;

public class ScimEvent {
    JsonNode obj;
    Operation op;

    public ScimEvent(Operation operation) {
        op = operation;
    }

    public String toStringMessage() {

       return this.op.toString();

    }

    public void parseMessage(String message) {
        // parses a message from kafka
    }

    public Operation toOperation() {
        return null;
    }
}
