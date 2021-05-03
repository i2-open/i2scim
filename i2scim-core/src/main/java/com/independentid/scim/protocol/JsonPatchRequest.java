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

package com.independentid.scim.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

	/**
	 * @param jsonPatchReq A pointer to  SCIM Json Modify request message to be parsed.
	 * @throws SchemaException Thrown when a missing or required attribute is detected
	 * @throws InvalidValueException Thrown when a Patch operation is missing a required value
	 */
	public JsonPatchRequest(JsonNode jsonPatchReq) throws SchemaException, InvalidValueException {
		this.ops = new ArrayList<>();
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

	public void parseJson(JsonNode node) throws SchemaException, InvalidValueException {
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
			JsonPatchOp op = new JsonPatchOp(oper);
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

}