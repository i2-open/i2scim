/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015,2020 Phillip Hunt, All Rights Reserved                   *
 *                                                                    *
 *  Confidential and Proprietary                                      *
 *                                                                    *
 *  This unpublished source code may not be distributed outside       *
 *  “Independent Identity Org”. without express written permission of *
 *  Phillip Hunt.                                                     *
 *                                                                    *
 *  People at companies that have signed necessary non-disclosure     *
 *  agreements may only distribute to others in the company that are  *
 *  bound by the same confidentiality agreement and distribution is   *
 *  subject to the terms of such agreement.                           *
 **********************************************************************/

package com.independentid.scim.resource;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.Meta;
import com.independentid.scim.schema.SchemaException;

public class DateValue extends Value {

	public Date value;
	
	public DateValue(Attribute cfg, JsonNode node) throws SchemaException, ParseException {
		super(cfg,node);
		parseJson(cfg,node);
	}
	
	public DateValue(Attribute cfg, Date date) throws SchemaException, ParseException {
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
	
	public String getValueArray() {
		String dateValue = Meta.ScimDateFormat.format(this.value);
		return dateValue;
	}
	
	public Date getDateValue() {
		return this.value;
	}

}
