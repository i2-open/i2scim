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
package com.independentid.scim.schema;

public class StringAttribute extends Attribute {
	
	public StringAttribute() {
		
	}
	
	public String value;
		
	/*
	public StringAttribute(String name, String value) {
		super(name);
		this.value = value;
	}
		
	@XmlElement
	public String getValue() {
		return this.value;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		if (this.value == null) return "";
		buf.append("\"").append(this.getName()).append("\":\"");
		buf.append(this.value).append("\"");
		return buf.toString();
	}

	@Override
	public StringAttribute parse(JsonParser jp, DeserializationContext ctx) throws JsonProcessingException, IOException {
		// TODO Auto-generated method stub
		this.name = jp.getCurrentName();
		this.value = jp.getValueAsString();
		
		return this;
	}

	@Override
	public void serialize(JsonGenerator jgen, SerializerProvider provider) throws JsonProcessingException, IOException {
		jgen.writeStringField(this.name, this.value);
	}
	*/
}
