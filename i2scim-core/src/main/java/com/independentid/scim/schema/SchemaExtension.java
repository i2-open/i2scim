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

package com.independentid.scim.schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;

import java.io.IOException;

/**
 * @author pjdhunt Used by ResourceType to define a schema extension which is just an id and a boolean required state.
 */
public class SchemaExtension implements ScimSerializer {

    public String id;
    public boolean required = false;

    /**
     * @param node A {@link JsonNode} representation of a schema extension attribute value of a {@link ResourceType}
     * @throws SchemaException Exception thrown when invalid SCIM extension definition detected
     */
    public SchemaExtension(JsonNode node) throws SchemaException {
        this.parseJson(node);
    }

    /* (non-Javadoc)
     * @see com.independentid.scim.schema.ScimSerializer#parseJson(com.fasterxml.jackson.databind.JsonNode)
     */
    @Override
    public void parseJson(JsonNode node) throws SchemaException {
        JsonNode n_id = node.get("schema");
        if (n_id == null)
            throw new SchemaException("Expecting sub-attribute 'schema' for attribute 'schemaExtension'.");
        this.id = n_id.asText();

        JsonNode item = node.get("required");
        if (item != null)
            this.required = item.asBoolean();
    }

    /* (non-Javadoc)
     * @see com.independentid.scim.schema.ScimSerializer#serialize(com.fasterxml.jackson.core.JsonGenerator, com.independentid.scim.protocol.RequestCtx)
     */
    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
        serialize(gen, ctx, false);
    }

    /* (non-Javadoc)
     * @see com.independentid.scim.schema.ScimSerializer#serialize(com.fasterxml.jackson.core.JsonGenerator, com.independentid.scim.protocol.RequestCtx)
     */
    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("schema", id);
        gen.writeBooleanField("required", required);
        gen.writeEndObject();
    }

    public JsonNode toJsonNode() {
        ObjectNode node = JsonUtil.getMapper().createObjectNode();
        node.put("schema", id);
        node.put("required", required);
        return node;
    }

}
