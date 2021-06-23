/*
 * Copyright 2021.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
		parseJson(node);
	}
	
	public DateValue(Attribute attr, Date date) {
		super();
		this.jtype = JsonNodeType.STRING;
		this.value = date;
		this.attr = attr;
	}
	
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		String dateValue = Meta.ScimDateFormat.format(this.value);
		gen.writeString(dateValue);
	}
	
	public void parseJson(JsonNode node) throws SchemaException, ParseException {
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
	
	public String getRawValue() {
		return Meta.ScimDateFormat.format(this.value);
	}
	
	public Date getDateValue() {
		return this.value;
	}

	public String toString() {
		return Meta.ScimDateFormat.format(this.value);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof DateValue) {
			DateValue obVal = (DateValue) obj;
			return obVal.value.equals(value);
		}
		return false;
	}

	@Override
	public int compareTo(Value o) {
		if (o instanceof DateValue) {
			DateValue obVal = (DateValue) o;
			return value.compareTo(obVal.value);
		}
		throw new ClassCastException("Unable to compare Value types");
	}
}
