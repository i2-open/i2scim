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
import com.independentid.scim.core.err.InternalException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;

import java.io.IOException;
import java.util.ArrayList;

/**
 * @author pjdhunt The BulkResponse contains one or more operations to be included in a SCIM Bulk Response per Section
 * 3.7.3 RFC7644
 */
public class BulkResponse extends ScimResponse {
	protected final RequestCtx ctx;
	protected final ArrayList<Operation> ops;
	protected int httpstat = 200;
	protected String stype = null;
	protected String detail = null;
	//protected int failOnErrors = 0; //the count of maximum errors before failing.

	/**
	 * Bulk response handles serializing results for one or more bulk request operations
	 * @param ctx The original request context.
	 */
	public BulkResponse(RequestCtx ctx) {

		this.ctx = ctx;
		this.ops = new ArrayList<>();

	}

	/**
	 * Bulk response handles serializing results for one or more bulk request operations
	 * @param ctx  The original request context.
	 * @param resp A completed SCIM {@link Operation} whose result needs to be serialized
	 */
	public BulkResponse(RequestCtx ctx, Operation resp) {

		this.ctx = ctx;
		this.ops = new ArrayList<>();
		this.ops.add(resp);

	}

	/**
	 * @param resp a completed {@link Operation} object to be added to the response.
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

	/**
	 * Serializes the BulkResponse and sets the HTTP status of the overall request (normally 200).
	 */
	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		if (ctx != null && ctx.getHttpServletResponse() != null)
			ctx.getHttpServletResponse().setStatus(this.httpstat);
		serialize(gen, ctx, false);
	}

	@Override
	public int getStatus() {
		return this.httpstat;
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
				gen.writeStringField("detail", this.detail);
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

		// Write the result of each operation that was processed.
		for (Operation op : this.ops)
			writeOperationResult(gen, op);

		gen.writeEndArray();
		gen.writeEndObject();

	}

	/**
	 * Writes a single operation result per RFC 7644 Section 3.7.3: method, bulkId, location, version, status (as a
	 * string) and, for a failed operation, the SCIM Error in "response".
	 * @param gen The JsonGenerator to write to.
	 * @param op A processed bulk {@link Operation}.
	 * @throws IOException if the result could not be written.
	 */
	protected void writeOperationResult(JsonGenerator gen, Operation op) throws IOException {
		gen.writeStartObject();
		if (op.getBulkMethod() != null)
			gen.writeStringField("method", op.getBulkMethod());
		if (op.getBulkId() != null)
			gen.writeStringField("bulkId", op.getBulkId());

		if (op.isError()) {
			Exception e = op.getCompletionException();
			ScimException se = (e instanceof ScimException) ? (ScimException) e
					: new InternalException("Internal error processing operation.");
			gen.writeStringField("status", String.valueOf(se.getStatus()));
			gen.writeFieldName("response");
			se.writeError(gen);
		} else {
			ScimResponse sresp = op.getScimResponse();
			if (sresp != null) {
				if (sresp.getLocation() != null)
					gen.writeStringField("location", sresp.getLocation());
				if (sresp.getETag() != null)
					gen.writeStringField("version", sresp.getETag());
				gen.writeStringField("status", String.valueOf(sresp.getStatus()));
			} else
				gen.writeStringField("status", String.valueOf(ScimResponse.ST_OK));
		}
		gen.writeEndObject();
	}

}


