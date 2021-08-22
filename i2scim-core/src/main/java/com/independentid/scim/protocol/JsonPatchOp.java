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

package com.independentid.scim.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.InvalidValueException;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

public class JsonPatchOp {

    public final static String OP_ACTION_ADD = "add";
    public final static String OP_ACTION_REMOVE = "remove";
    public final static String OP_ACTION_REPLACE = "replace";
    public static final String OP_ACTION = "op";
    public static final String OP_VALUE = "value";
    public static final String OP_PATH = "path";

    public String path;
    public String op;
    public JsonNode jsonValue;
    protected RequestCtx ctx;

    public JsonPatchOp(String op, String path, Value value) {
        this.op = op;
        this.path = path;
        ObjectNode node = JsonUtil.getMapper().createObjectNode();
        this.ctx = null;
        //Convert to JSON value...
        if (value == null)
            this.jsonValue = null;
        else
            this.jsonValue = value.toJsonNode(null, "field").findValue("field");
    }

    public JsonPatchOp(JsonNode node, RequestCtx ctx) throws SchemaException, InvalidValueException, BadFilterException {
        this.ctx = ctx;
        JsonNode onode = node.get(OP_ACTION);
        if (onode == null)
            throw new SchemaException("Missing attribute 'op' defining the SCIM patch operation type.");

        String type = onode.asText();
        switch (type) {
            case OP_ACTION_ADD:
            case OP_ACTION_REMOVE:
            case OP_ACTION_REPLACE:
                op = type;
                break;
            default:
                throw new InvalidValueException("Invalid SCIM Patch operation value for 'op'. Found: " + type);
        }


        JsonNode pnode = node.get(OP_PATH);
        if (pnode == null)
            path = null;
        else
            path = pnode.asText();
        validatePathFilter();


        if (path == null && op.equals(OP_ACTION_REMOVE))
            throw new SchemaException("Missing path value for a SCIM Patch 'remove' operation.");

        this.jsonValue = node.get(OP_VALUE);
        if (this.jsonValue == null && !op.equals(OP_ACTION_REMOVE))
            throw new InvalidValueException("No value provided for SCIM PATCH ADD or REPLACE operation.");
    }

    private void validatePathFilter() throws BadFilterException {

        if (path == null || this.ctx == null || !path.contains("["))
            return;

        Filter.parseFilter(path,this.ctx);

    }

    public JsonNode toJsonNode() {
        ObjectNode node = JsonUtil.getMapper().createObjectNode();
        node.put(OP_ACTION, op);
        if (path != null)
            node.put(OP_PATH, path);
        if (jsonValue != null)
            node.set(OP_VALUE, jsonValue);
        return node;
    }

}
