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
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;

import java.io.IOException;
import java.util.List;

public class StringValue extends Value implements IBulkIdTarget {

	//private final static Logger logger = LoggerFactory.getLogger(StringValue.class);
	
	public String value;
	public IBulkIdResolver resolver;
	
	public StringValue(Attribute attr, JsonNode node,IBulkIdResolver bulkIdResolver) throws SchemaException {
		super(attr,node);
		parseJson(attr, node);
		this.resolver = bulkIdResolver;
	}
	
	public StringValue(Attribute attr, JsonNode node) throws SchemaException {
		super(attr,node);
		parseJson(attr, node);
		this.resolver = null;
	}
	
	public StringValue(Attribute attr, String value) {
		super();
		this.jtype = JsonNodeType.STRING;
		this.value = value;
	}
	
	public String getBulkId() {
		if (this.value == null) return null;
		if (!value.toLowerCase().startsWith("bulkid:"))
			return null;
		
		return this.value.substring(7);
	}
	
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws ScimException,IOException {
		String val = value;
		if (val == null) return;
		if (hasBulkIds()) 
			val = resolver.translateId(val);
			
		gen.writeString(val);
	}
	
	public void parseJson(Attribute attr, JsonNode node) throws SchemaException {
		if (node == null)
			throw new SchemaException("Was expecting a String value but encountered null");
		if (!this.jtype.equals(JsonNodeType.STRING))
			throw new SchemaException("Invalid field data endpoint. Expecting 'string'."+node.toString());
		this.value = node.asText();
	}
	
	public String getValueArray() {
		return this.value;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#hasBulkIds()
	 */
	@Override
	public boolean hasBulkIds() {
	if(this.value == null) return false;
		
		return (this.value.toLowerCase().startsWith("bulkid:"));
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getBulkIdsRequired()
	 */
	@Override
	public void getBulkIdsRequired(List<String> bulkList) {
		if (!hasBulkIds())
			return;
		bulkList.add(this.value);
	}

	public String toString() {
		return this.value;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getAttributesWithBulkIdValues()
	 */
	@Override
	public void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs) {
		bulkIdAttrs.add(this);
	}

}
