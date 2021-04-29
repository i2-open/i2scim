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
