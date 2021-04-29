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
package com.independentid.scim.resource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;

public abstract class Value implements Comparable<Value> {
	public JsonNodeType jtype;

	Attribute attr;

	public Value() {
		
	}
	
	public Value(Attribute attr, JsonNode node) {
		this.jtype = node.getNodeType();
		this.attr = attr;
	}
	
	public abstract void serialize(JsonGenerator gen, RequestCtx ctx) throws ScimException,IOException;
	
	public abstract void parseJson(JsonNode node) throws ConflictException, SchemaException, ParseException;
	
	public abstract Object getRawValue();
	
	public abstract JsonNode toJsonNode(ObjectNode parent, String aname);

	public  String toString(Attribute attr) {
		return toJsonNode(null,attr.getName()).toString();
	}

	public  Attribute getAttribute() {
		return attr;
	}

	@Override
	public int hashCode() {
		return this.getRawValue().hashCode();
	}

}
