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
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

import java.io.IOException;
import java.text.ParseException;

public class IntegerValue extends Value {
	public Integer value;
	
	public IntegerValue() {
	}

	public IntegerValue(Attribute attr, JsonNode node) throws SchemaException, ParseException {
		super(attr, node);
		parseJson(attr,node);
	}
	
	public IntegerValue(Attribute attr, Integer val) {
		super();
		this.jtype = JsonNodeType.NUMBER;
		this.value = val;
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		gen.writeNumber(this.value);
	}

	@Override
	public JsonNode toJsonNode(ObjectNode parent, String aname) {
		if (parent == null)
			parent = JsonUtil.getMapper().createObjectNode();
		parent.put(aname,this.value);
		return parent;
	}

	@Override
	public void parseJson(Attribute attr, JsonNode node) throws SchemaException, ParseException {
		if (!this.jtype.equals(JsonNodeType.NUMBER))
			throw new SchemaException("Invalid field data endpoint. Expecting integer 'number'."+node.toString());
		this.value = node.asInt();
			
	}

	@Override
	public Integer getValueArray() {
		return this.value;
	}

}
