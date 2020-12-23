/*
 * Copyright (c) 2020.
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

package com.independentid.scim.test.sub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
public class JacksonTest {

    @Test
    public void aTest() {
       ObjectNode node = getNode();
       System.out.println("Basic Node:\n"+node.toPrettyString());
       String nstring = node.toString();
       System.out.println("Compare string:\n"+nstring);

       node.put("objtest",getObj(1));

       System.out.println("Nested obj:\n"+node.toPrettyString());

        node = getNode();
        node.setAll(getObj(2));

        System.out.println("Setall:\n"+node.toPrettyString());

        ArrayNode anode = node.putArray("testArray");
        for (int i=1; i<10; i++) {
            anode.add(getObj(i));
        }

        System.out.println("Array:\n"+node.toPrettyString());
    }

    public ObjectNode getNode() {
        ObjectNode node = JsonUtil.getMapper().createObjectNode();

        node.putArray("schemas").add(Schema.SCHEMA_ID);
        node.put("id","bleh");
        return  node;
    }

    public ObjectNode getObj(int val) {
        ObjectNode node = JsonUtil.getMapper().createObjectNode();
        node.put("attribute",val);
        return node;
    }
}
