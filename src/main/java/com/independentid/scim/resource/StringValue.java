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
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class StringValue extends Value implements IBulkIdTarget {

	//private final static Logger logger = LoggerFactory.getLogger(StringValue.class);

	// store data as byte arrays to improve GC of security risky data.
	public char[] value;
	public IBulkIdResolver resolver;
	boolean isBulkId = false;
	
	public StringValue(Attribute attr, JsonNode node,IBulkIdResolver bulkIdResolver) throws SchemaException {
		super(attr,node);
		parseJson(node);
		this.resolver = bulkIdResolver;
	}
	
	public StringValue(Attribute attr, JsonNode node) throws SchemaException {
		super(attr,node);
		parseJson(node);
		this.resolver = null;
	}
	
	public StringValue(Attribute attr, String value) {
		super();
		this.jtype = JsonNodeType.STRING;
		if (value.startsWith("bulkid:")) {
			isBulkId = true;
			this.value = value.substring(7).toCharArray();
		} else
			this.value = value.toCharArray();
		this.attr = attr;
	}
	
	public String getBulkId() {
		if (this.value == null || !isBulkId)
			return null;
		
		return new String(this.value);
	}
	
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws ScimException,IOException {
		if (this.value == null || this.value.length == 0)
			return;
		String val = new String(value);
		if (hasBulkIds()) 
			val = resolver.translateId(val);
			
		gen.writeString(val);
	}

	@Override
	public JsonNode toJsonNode(ObjectNode parent,String aname) {
		if (parent == null)
		   parent = JsonUtil.getMapper().createObjectNode();
		parent.put(aname,new String(this.value));
		return parent;
	}

	public void parseJson(JsonNode node) throws SchemaException {
		if (node == null)
			throw new SchemaException("Was expecting a String value but encountered null");
		if (!this.jtype.equals(JsonNodeType.STRING))
			throw new SchemaException("Invalid field data endpoint. Expecting 'string'."+node.toString());
		this.value = node.asText().toCharArray();
	}
	
	public String getRawValue() {
		return new String(this.value);
	}

	public byte[] getBytes() {
		return (new String(this.value)).getBytes(StandardCharsets.UTF_8);
	}

	public char[] getCharArray() {
		return this.value;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#hasBulkIds()
	 */
	@Override
	public boolean hasBulkIds() {
	if(this.value == null || this.value.length==0) return false;
		
		return this.isBulkId;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getBulkIdsRequired()
	 */
	@Override
	public void getBulkIdsRequired(List<String> bulkList) {
		if (!hasBulkIds())
			return;
		bulkList.add(new String(this.value));
	}

	public String toString() {
		return new String(this.value);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getAttributesWithBulkIdValues()
	 */
	@Override
	public void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs) {
		bulkIdAttrs.add(this);
	}

	/**
	 * Used by indexing to generate an endswith Map
	 * @return A the String value with the bytes in reverse order (e.g. Flip becomes pilF)
	 */
	public String reverseValue() {
		char[] valChars = this.value;
		char[] revChars = new char[valChars.length];
		for (int byteAt = 0; byteAt < valChars.length; byteAt++) {
			revChars[byteAt] = valChars[valChars.length - byteAt - 1];
		}
		return new String(revChars);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof StringValue) {
			StringValue obVal = (StringValue) obj;
			if (obVal.value.length != this.value.length)
				return false;
			if (attr.getCaseExact()) {
				for (int i = 0; i < this.value.length; i++)
					if (!Character.valueOf(this.value[i]).equals(obVal.value[i]))
						return false;
				return true;
			}
			return obVal.toString().equalsIgnoreCase(this.toString());
		}

		return false;
	}

	@Override
	public int compareTo(Value o) {
		if (o instanceof StringValue) {
			StringValue obVal = (StringValue) o;
			if(attr.getCaseExact())
				return toString().compareTo(obVal.toString());
			return toString().toLowerCase().compareTo(obVal.toString().toLowerCase());
		}
		throw new ClassCastException("Unable to compare Value types");
	}
}
