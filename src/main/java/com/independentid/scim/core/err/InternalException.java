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

import com.independentid.scim.protocol.ScimResponse;

public class InternalException extends ScimException {

	
	private static final long serialVersionUID = 1L;

	public InternalException() {
		this.status = ScimResponse.ST_INTERNAL;
		
	}

	public InternalException(String message) {
		super(message);
		this.status = ScimResponse.ST_INTERNAL;
		this.detail = message;
		
	}

	public InternalException(String message, String scimType) {
		super(message, scimType);
		this.status = ScimResponse.ST_INTERNAL;
	}

	public InternalException(Throwable cause) {
		super(cause);
		this.status = ScimResponse.ST_INTERNAL;
		this.detail = cause.getLocalizedMessage();
	}

	public InternalException(String message, Throwable cause) {
		super(message, cause);
		this.status = ScimResponse.ST_INTERNAL;
		this.detail = cause.getLocalizedMessage();
	}

}
