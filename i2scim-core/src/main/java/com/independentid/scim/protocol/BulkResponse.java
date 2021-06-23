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
package com.independentid.scim.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.op.Operation;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author pjdhunt
 * The BulkResponse contains one or more operations to be included in a SCIM Bulk Response per Section 3.7.3 RFC7644
 */
public class BulkResponse extends ScimResponse {
//TODO: This code has not JUnit code and is not fully implemented.
	protected final RequestCtx ctx;
	protected final ArrayList<Operation> ops;
	protected int httpstat = 200;
	protected String stype = null;
	protected String detail = null;
	//protected int failOnErrors = 0; //the count of maximum errors before failing.
	
	/**
	 * 
	 */
	public BulkResponse(RequestCtx ctx) {
		
		this.ctx = ctx;
		this.ops = new ArrayList<>();
		
	}

	/**
	 * 
	 */
	public BulkResponse(RequestCtx ctx,Operation resp) {
		
		this.ctx = ctx;
		this.ops = new ArrayList<>();
		this.ops.add(resp);
	
	}

	/**
	 * @param resp a completed <Operation> object to be added to the response.
	 */
	public void addOpResp(Operation resp) {
		this.ops.add(resp);
	}
	
	public void setHttpStatus(int stat) {
		this.httpstat = stat;
	}
	
	public void setScimTypeError(String scimType, String detail) {
		this.stype = scimType;
		this.detail = detail;
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
		for (Operation op : this.ops) {
			if (!op.isDone())
				throw new IOException("Not all operations are done: " + op);
			//TODO is this the correct response
			op.doResponse(gen);
		}
		
		gen.writeEndArray();
		gen.writeEndObject();

	}


}


