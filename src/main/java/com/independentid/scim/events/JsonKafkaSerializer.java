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
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class JsonKafkaSerializer implements Serializer<JsonNode> {

    public JsonKafkaSerializer() {
    }

    @Override
    public byte[] serialize(String topic, JsonNode data) {
        if (data == null) return null;
        try {
            return JsonUtil.getMapper().writeValueAsBytes(data);

        } catch (JsonProcessingException e) {
            throw new SerializationException("Error serializing JSON message", e);
        }
    }

    @Override
    public void configure(Map configs, boolean isKey) {
        //nothing to config
    }

    @Override
    public byte[] serialize(String topic, Headers headers, JsonNode data) {
        //return null;
        return serialize(topic,data);
    }

    @Override
    public void close() {

    }
}
