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
import com.fasterxml.jackson.databind.node.ObjectNode;
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
	public JsonNode value;
	
	
	public JsonPatchOp(RequestCtx ctx, JsonNode node) throws SchemaException {
		JsonNode onode = node.get(OP_ACTION);
		if (onode == null)
			throw new SchemaException("Missing attribute 'op' defining the SCIM patch operation type.");
		
		String type = onode.asText();
		switch (type) {
		case OP_ACTION_ADD:
		case OP_ACTION_REMOVE:
		case OP_ACTION_REPLACE:
			op =type;
			break;
		default:
			throw new SchemaException("Invalid SCIM Patch operation value for 'op'. Found: "+type);
		}

		
		JsonNode pnode = node.get(OP_PATH);
		if (pnode == null)
			path = null;
		else
			path = pnode.asText();
		
		if (path == null && op.equals(OP_ACTION_REMOVE))
			throw new SchemaException("Missing path value for a SCIM Patch 'remove' operation.");
		
		this.value = node.get(OP_VALUE);
	}

	public JsonNode toJsonNode() {
		ObjectNode node = JsonUtil.getMapper().createObjectNode();
		node.put(OP_ACTION,op);
		if (path != null)
			node.put(OP_PATH,path);
		if (value != null)
			node.set(OP_VALUE,value);
		return node;
	}

}
