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

public class InvalidValueException extends ScimException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public final static String SCIM_TYPE = ScimResponse.ERR_TYPE_BADVAL;
	
	public InvalidValueException() {
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public InvalidValueException(String message) {
		super(message);
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public InvalidValueException(Throwable cause) {
		super(cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

	public InvalidValueException(String message, Throwable cause) {
		super(message, cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

}
