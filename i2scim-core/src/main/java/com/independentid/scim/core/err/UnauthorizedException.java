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

/*
 * ForbiddenException is thrown when SCIM client attempts to perform an
 * operation that is not permitted based on the supplied authorization.
 * See Sec 3.12 of RFC7644.
 */
public class UnauthorizedException extends ScimException {
	{ status = ScimResponse.ST_UNAUTHORIZED; }
	/**
	 *
	 */
	private static final long serialVersionUID = 1L;

	public UnauthorizedException() {

	}

	public UnauthorizedException(String message) {
		super(message);

	}

	public UnauthorizedException(String message, String scimType) {
		super(message, scimType);
	}

	public UnauthorizedException(Throwable cause) {
		super(cause);

		this.detail = cause.getLocalizedMessage();
	}

	public UnauthorizedException(String message, Throwable cause) {
		super(message, cause);
		this.detail = cause.getLocalizedMessage();
	}

}
