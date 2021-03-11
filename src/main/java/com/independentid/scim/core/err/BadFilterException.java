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

public class BadFilterException extends ScimException {

	public final static String SCIM_TYPE = ScimResponse.ERR_TYPE_FILTER;
	
	private static final long serialVersionUID = 1L;

	public BadFilterException() {
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public BadFilterException(String message) {
		super(message);
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public BadFilterException(String message, String scimType) {
		super(message, scimType);
		this.status = 400;
		
	}

	public BadFilterException(Throwable cause) {
		super(cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

	public BadFilterException(String message, Throwable cause) {
		super(message, cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

}
