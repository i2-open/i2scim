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

public class IntegerValue extends Value {
	public Integer value;
	
	public IntegerValue() {
	}

	public IntegerValue(Attribute attr, JsonNode node) throws SchemaException, ParseException {
		super(attr, node);
		parseJson(node);
	}
	
	public IntegerValue(Attribute attr, Integer val) {
		super();
		this.attr = attr;
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
	public void parseJson(JsonNode node) throws SchemaException, ParseException {
		if (!this.jtype.equals(JsonNodeType.NUMBER))
			throw new SchemaException("Invalid field data endpoint. Expecting integer 'number'."+node.toString());
		this.value = node.asInt();
			
	}

	@Override
	public Integer getRawValue() {
		return this.value;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof IntegerValue) {
			IntegerValue obVal = (IntegerValue) obj;
			return obVal.value.equals(value);
		}
		return false;
	}

	@Override
	public int compareTo(Value o) {
		if (o instanceof IntegerValue) {
			IntegerValue obVal = (IntegerValue) o;
			return value.compareTo(obVal.value);
		}
		throw new ClassCastException("Unable to compare Value types");
	}
}
