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

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

public class ScimKafkaPartitioner  implements Partitioner {
    private final static Logger logger = LoggerFactory.getLogger(ScimKafkaPartitioner.class);
    static int shards = 1;

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valbytes, Cluster cluster) {
        if (shards <= 1)
            return 0;
        return getPartition((String)key,shards);
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> map) {
        Object val = map.get(KafkaRepEventHandler.PROP_SCIM_KAFKA_REP_SHARDS);
        if (val instanceof Integer)
            shards = (int) val;
        else
            shards = Integer.parseInt((String)val);
        logger.info("i2scim partitioning into "+shards+" nodes.");
    }

    public static int getPartition(String id, int b) {
        UUID uid = UUID.fromString(id);
        return getPartition(uid,b);
    }

    public static int getPartition(UUID id, int b) {
        // adapted from Java hashmap
        int hash = id.hashCode();
        if (hash < 0)
            hash = -hash;
        //int ares = ((hash ^ (hash >>> 16)) % b);
        return hash % b;
    }
}
