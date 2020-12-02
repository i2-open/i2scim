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
import com.independentid.scim.schema.SchemaException;

public class JsonPatchOp {

	public final static String OP_ADD = "add";
	public final static String OP_REMOVE = "remove";
	public final static String OP_REPLACE = "replace";
	
	public String path;
	public String op;
	public JsonNode value;
	
	
	public JsonPatchOp(RequestCtx ctx, JsonNode node) throws SchemaException {
		JsonNode onode = node.get("op");
		if (onode == null)
			throw new SchemaException("Missing attribute 'op' defining the SCIM patch operation type.");
		
		String type = onode.asText();
		switch (type) {
		case OP_ADD:
		case OP_REMOVE:
		case OP_REPLACE:
			op =type;
			break;
		default:
			op = "error";
			
		}
		if (op.equals("error"))
			throw new SchemaException("Invalid SCIM Patch operation value for 'op'. Found: "+type);
		
		JsonNode pnode = node.get("path");
		if (pnode == null)
			this.path = null;
		else
			this.path = pnode.asText();
		
		if (this.path == null && this.op.equals("remove"))
			throw new SchemaException("Missing path value for a SCIM Patch 'remove' operation.");
		
		this.value = node.get("value");
	}

}
