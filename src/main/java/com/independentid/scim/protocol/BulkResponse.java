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
package com.independentid.scim.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.op.Operation;
import com.independentid.scim.schema.SchemaException;

/**
 * @author pjdhunt
 *
 */
public class BulkResponse extends ScimResponse {

	protected RequestCtx ctx;
	protected ArrayList<Operation> ops;
	protected int httpstat = 200;
	protected String stype = null;
	protected String detail = null;
	protected int failOnErrors = 0;
	
	/**
	 * 
	 */
	public BulkResponse(RequestCtx ctx) {
		
		this.ctx = ctx;
		this.ops = new ArrayList<Operation>();
		
	}

	/**
	 * 
	 */
	public BulkResponse(RequestCtx ctx,Operation resp) throws SchemaException {
		
		this.ctx = ctx;
		this.ops = new ArrayList<Operation>();
		this.ops.add(resp);
	
	}
	
	public void addOpResp(Operation resp) {
		this.ops.add(resp);
	}
	
	public void setHttpStatus(int stat) {
		this.httpstat = stat;
	}
	
	public void setScimTypeError(String scimType, String detail) {
		this.stype = scimType;
	}
	
	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
		if (this.httpstat >= 400) {
			gen.writeStartObject();
			gen.writeArrayFieldStart("schemas");
			gen.writeString(ScimParams.SCHEMA_API_Error);
			gen.writeEndArray();
			if (this.stype != null)
				gen.writeStringField("scimType", this.stype);
			if (this.detail != null)
				gen.writeStringField("detail",this.detail);
			gen.writeNumberField("status", this.httpstat);
			gen.writeEndObject();
			// Setting status will now be done by the caller (Operation.java)
			//resp.setStatus(this.httpstat);
			return;
		}
		
		gen.writeStartObject();
		gen.writeArrayFieldStart("schemas");
		gen.writeString(ScimParams.SCHEMA_API_BulkResponse);
		gen.writeEndArray();
		gen.writeArrayFieldStart("Operations");
		
		// Write all the operations out.
		Iterator<Operation> iter = this.ops.iterator();
		while (iter.hasNext()) {
			Operation op = iter.next();
			if (!op.isDone())
				throw new IOException("Not all operations are done: "+op.toString());
				//TODO is this the correct response
			op.doResponse(gen);
		}
		
		gen.writeEndArray();
		gen.writeEndObject();
		
		return;
	}


}


