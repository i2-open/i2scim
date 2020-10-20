/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015 Phillip Hunt, All Rights Reserved                        *
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
import java.io.StringWriter;
import java.text.ParseException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.server.ConflictException;
import com.independentid.scim.server.ScimException;

public abstract class Value {
	public JsonNodeType jtype;
	
	public Value() {
		
	}
	
	public Value(Attribute attr, JsonNode node) {
		this.jtype = node.getNodeType();
	}
	
	public abstract void serialize(JsonGenerator gen, RequestCtx ctx) throws ScimException,IOException;
	
	public abstract void parseJson(Attribute attr, JsonNode node) throws ConflictException, SchemaException, ParseException;
	
	public abstract Object getValueArray();
	
	public  JsonNode toJsonNode() throws ScimException {
		StringWriter writer = new StringWriter();
		try {
			JsonGenerator gen = JsonUtil.getGenerator(writer, false);
			this.serialize(gen, null);
			gen.close();
			writer.close();
			return JsonUtil.getJsonTree(writer.toString());
		} catch (IOException e) {
			// Should not happen
			e.printStackTrace();
		}
		return null;
	};
	
	public  String toString() {
		StringWriter writer = new StringWriter();
		try {
			JsonGenerator gen = JsonUtil.getGenerator(writer, false);
		
			this.serialize(gen, null);
			
			gen.close();
			writer.close();
			String result = writer.getBuffer().toString();
			return result;
		} catch (IOException | ScimException e) {
			// Should not happen
			e.printStackTrace();
		}
		return super.toString();
	}
}
