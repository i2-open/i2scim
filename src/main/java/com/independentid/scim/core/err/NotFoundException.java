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

public class NotFoundException extends ScimException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public NotFoundException() {
		this.status = ScimResponse.ST_NOTFOUND;
	}

	public NotFoundException(String message) {
		super(message);
		this.status = ScimResponse.ST_NOTFOUND;
	}

	public NotFoundException(String message, String scimType) {
		super(message, scimType);
		this.status = ScimResponse.ST_NOTFOUND;
	}

	public NotFoundException(Throwable cause) {
		super(cause);
		this.status = ScimResponse.ST_NOTFOUND;
	}

	public NotFoundException(String message, Throwable cause) {
		super(message, cause);
		this.status = ScimResponse.ST_NOTFOUND;
	}

}
