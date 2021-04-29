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


public class ConflictException extends ScimException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
		
	/**
	 * ConflictException - Used to return HTTP Status 409. Caused by 
	 * an mis-match version (etag), or a conflict occurred for an
	 * attribute that is defined as "unique".
	 */
	public ConflictException() {
		this.scimType = null;
		this.status = 409;
	}

	/**
	 * ConflictException - Used to return HTTP Status 409. Caused by 
	 * an mis-match version (etag), or a conflict occurred for an
	 * attribute that is defined as "unique".
	 * @param message A string containing explanation text to be 
	 * returned in SCIM "detail" error message.
	 */
	public ConflictException(String message) {
		super(message);
		this.scimType = null;
		this.status = 409;
	}

	public ConflictException(Throwable cause) {
		super(cause);
		this.scimType = null;
		this.status = 409;
		this.detail = cause.getLocalizedMessage();
	}

	public ConflictException(String message, Throwable cause) {
		super(message, cause);
		this.scimType = null;
		this.status = 409;
	}

}
