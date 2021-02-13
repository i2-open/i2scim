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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.independentid.scim.op.IBulkOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class OperationKeySerializer implements Serializer<IBulkOp> {

    public OperationKeySerializer() {
    }

    @Override
    public byte[] serialize(String topic, IBulkOp operation) {
        if (operation == null) return null;
        if (operation instanceof Operation) {
            Operation op = (Operation) operation;
            String id = op.getResourceId();
            if (id != null)
                return id.getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    @Override
    public void configure(Map configs, boolean isKey) {
        //nothing to config
    }

    @Override
    public byte[] serialize(String topic, Headers headers, IBulkOp data) {
        //return null;
        return serialize(topic,data);
    }

    @Override
    public void close() {

    }
}