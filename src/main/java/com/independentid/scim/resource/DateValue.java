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
import java.util.Date;

public class DateValue extends Value {

	public Date value;
	
	public DateValue(Attribute attr, JsonNode node) throws SchemaException, ParseException {
		super(attr,node);
		parseJson(attr,node);
	}
	
	public DateValue(Attribute cfg, Date date) {
		super();
		this.jtype = JsonNodeType.STRING;
		this.value = date;
	}
	
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		String dateValue = Meta.ScimDateFormat.format(this.value);
		gen.writeString(dateValue);
	}
	
	public void parseJson(Attribute attr,JsonNode node) throws SchemaException, ParseException {
		if (node == null)
			throw new SchemaException("Was expecting a String value but encountered null");
		if (!this.jtype.equals(JsonNodeType.STRING))
			throw new SchemaException("Invalid field data endpoint. Expecting 'string' datetime."+node.toString());
		this.value = Meta.ScimDateFormat.parse(node.asText());
	}

	@Override
	public JsonNode toJsonNode(ObjectNode parent, String aname) {
		if (parent == null)
			parent = JsonUtil.getMapper().createObjectNode();
		parent.put(aname,Meta.ScimDateFormat.format(this.value));
		return parent;
	}
	
	public String getValueArray() {
		return Meta.ScimDateFormat.format(this.value);
	}
	
	public Date getDateValue() {
		return this.value;
	}

	public String toString() {
		return Meta.ScimDateFormat.format(this.value);
	}

}
