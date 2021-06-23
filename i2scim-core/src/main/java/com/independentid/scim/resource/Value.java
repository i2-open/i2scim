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
