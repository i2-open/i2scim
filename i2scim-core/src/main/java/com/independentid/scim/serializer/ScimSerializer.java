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
package com.independentid.scim.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaException;

import java.io.IOException;
import java.text.ParseException;

public interface ScimSerializer {

	void parseJson(JsonNode node) throws SchemaException, ParseException, ConflictException;

	/**
	 * toJsonNode is useful for doing deep copies and translations to avoid full serialization into strings.
	 * @return An ObjectNode cast as JsonNode representation of the full object. This method does NOT provide security filtering.
	 */
	JsonNode toJsonNode();
	
	void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException;

	void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException, ScimException;
}
