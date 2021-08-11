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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.InvalidValueException;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/*
 * This class holds a parsed SCIM JSON Modify request as per Sec 3.5.2 of RFC7644
 * @author pjdhunt
 *
 */
public class JsonPatchRequest {
	
	protected final ArrayList<JsonPatchOp> ops;
	protected RequestCtx ctx;

	/**
	 * @param jsonPatchReq A pointer to  SCIM Json Modify request message to be parsed.
	 * @param ctx RequestCtx object for the request. Primarily used for SchemaManager access
	 * @throws SchemaException Thrown when a missing or required attribute is detected
	 * @throws InvalidValueException Thrown when a Patch operation is missing a required value
	 */
	public JsonPatchRequest(JsonNode jsonPatchReq, RequestCtx ctx) throws SchemaException, InvalidValueException, BadFilterException {
		this.ops = new ArrayList<>();
		this.ctx = ctx;
		parseJson(jsonPatchReq);
	}

	public JsonPatchRequest() {
		this.ops = new ArrayList<>();
	}

	public JsonPatchRequest(List<JsonPatchOp> ops) {
		this.ops = new ArrayList<>();
		this.ops.addAll(ops);
	}

	public void addOperation(JsonPatchOp op) {
		this.ops.add(op);
	}

	public void parseJson(JsonNode node) throws SchemaException, InvalidValueException, BadFilterException {
		JsonNode snode = node.get(ScimParams.ATTR_SCHEMAS);
		if (snode == null) throw new SchemaException("JSON is missing 'schemas' attribute.");

		boolean invalidSchema = true;
		if (snode.isArray()) {
			Iterator<JsonNode> jiter = snode.elements();
			while (jiter.hasNext() && invalidSchema){
				JsonNode anode = jiter.next();
				if (anode.asText().equalsIgnoreCase(ScimParams.SCHEMA_API_PatchOp))
					invalidSchema = false;
			}
		}

		if (invalidSchema)
			throw new SchemaException("Expecting JSON with schemas attribute to be an array with value of: "+ScimParams.SCHEMA_API_PatchOp);
		
		JsonNode opsnode = node.get(ScimParams.ATTR_PATCH_OPS);
		if (opsnode == null)
			throw new SchemaException("Missing 'Operations' attribute array.");
		
		if (!opsnode.isArray()) {
			throw new SchemaException("Expecting 'Operations' to be an array.");
		}
		
		Iterator<JsonNode> oiter = opsnode.elements();
		while (oiter.hasNext()) {
			JsonNode oper = oiter.next();
			JsonPatchOp op = new JsonPatchOp(oper, ctx);
			this.ops.add(op);
		}	
	}
	
	public int getSize() {
		return this.ops.size();
	}
	
	public Iterator<JsonPatchOp> iterator() {
		return this.ops.iterator();
	}
	
	public String toString() {
		if (this.ops.size() == 0) return "JsonPatchRequest ops: <EMPTY>";
		
		StringBuilder buf = new StringBuilder();
		buf.append("JsonPatchRequest ops: \n");
		Iterator<JsonPatchOp> iter = this.ops.iterator();
		while (iter.hasNext()) {
			JsonPatchOp jop = iter.next();
			buf.append("op=").append(jop.op);
			if (jop.path !=null)
				buf.append(", path=").append(jop.path);
			if (iter.hasNext()) buf.append(",\n");
		}
		return buf.toString();
	}

	public JsonNode toJsonNode() {
		ObjectNode node = JsonUtil.getMapper().createObjectNode();
		ArrayNode anode = node.putArray(ScimParams.ATTR_SCHEMAS);
		anode.add(ScimParams.SCHEMA_API_PatchOp);
		ArrayNode opsNode = node.putArray(ScimParams.ATTR_PATCH_OPS);
		for(JsonPatchOp op : ops)
			opsNode.add(op.toJsonNode());
		return node;
	}

	public static JsonPatchBuilder builder() {
		return new JsonPatchBuilder();
	}

	public static JsonPatchBuilder builder(JsonPatchOp op) {
		return new JsonPatchBuilder(op);
	}

}
