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
package com.independentid.scim.core.err;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.protocol.ScimResponse;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ScimException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected int status = ScimResponse.ST_BAD_REQUEST;
	protected String detail = null;
	protected String scimType = null;
	
	public ScimException() {
		
	}

	public ScimException(String message) {
		super(message);
		
	}
	
	public ScimException(String message,String scimType) {
		super(message);
		this.scimType = scimType;
	}

	public ScimException(Throwable cause) {
		super(cause);
		
	}

	public ScimException(String message, Throwable cause) {
		super(message, cause);
		
	}
	
	public int getStatus() {
		return this.status;
	}
	
	public void setStatus(int status) {
		this.status = status;
	}
	
	public void setDetail(String detailmsg) {
		this.detail = detailmsg;
	}
	
	public String getDetail() {
		if (this.detail == null) 
			return this.getMessage();
		else
			return this.detail;
	}
	
	public String getScimType() {
		return this.scimType;
	}
	
	public void serialize(JsonGenerator gen,HttpServletResponse resp) throws IOException {
		gen.writeStartObject();
		gen.writeArrayFieldStart("schemas");
		gen.writeString(ScimResponse.SCHEMA_ERROR);
		gen.writeEndArray();
		if (this.scimType != null)
			gen.writeStringField("scimType", this.scimType);
		if (this.detail != null)
			gen.writeStringField("detail",this.detail);
		else
			gen.writeStringField("detail",this.getLocalizedMessage());
		gen.writeNumberField("status", this.status);
		gen.writeEndObject();
		resp.setStatus(this.status);
		gen.close();
	}

}
