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

package com.independentid.scim.events.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.util.Map;

public class JsonKafkaDeserializer implements Deserializer<JsonNode> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

    }

    @Override
    public JsonNode deserialize(String topic, byte[] data) {
        if (data == null) return null;
        try {
            return JsonUtil.getJsonTree(data);
        } catch (IOException e) {
           throw new SerializationException("Unable to deserialize Kafka data",e);
        }
    }

    @Override
    public JsonNode deserialize(String topic, Headers headers, byte[] data) {
        //return null;
        return deserialize(topic,data);
    }

    @Override
    public void close() {

    }
}
