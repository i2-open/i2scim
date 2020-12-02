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
	
	void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException;

	void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException, ScimException;
}
