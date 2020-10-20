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

package com.independentid.scim.schema;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.serializer.ScimSerializer;

/**
 * @author pjdhunt
 *
 */
public class SchemaExtension implements ScimSerializer {

	public String id;
	public boolean required;

	/**
	 * @throws SchemaException 
	 * 
	 */
	public SchemaExtension(JsonNode node) throws SchemaException {
		this.parseJson(node);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.schema.ScimSerializer#parseJson(com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	public void parseJson(JsonNode node) throws SchemaException {
		JsonNode n_id = node.get("schema");
		if (n_id == null)
			throw new SchemaException("Expecting sub-attribute 'schema' for attribute 'schemaExtension'.");
		this.id = n_id.asText();
		
		JsonNode item = node.get("required");
		if (item != null)
			this.required = item.asBoolean();
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.schema.ScimSerializer#serialize(com.fasterxml.jackson.core.JsonGenerator, com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		serialize(gen, ctx, false);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.schema.ScimSerializer#serialize(com.fasterxml.jackson.core.JsonGenerator, com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
		gen.writeStartObject();
		gen.writeStringField("schema", id);
		if (this.required)
			gen.writeBooleanField("required", required);
		gen.writeEndObject();
	}

}
