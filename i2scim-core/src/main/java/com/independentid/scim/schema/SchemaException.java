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
package com.independentid.scim.schema;


import com.independentid.scim.core.err.ScimException;

public class SchemaException extends ScimException {

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public SchemaException() {
		
	}

	public SchemaException(String message) {
		super(message);
	}

	public SchemaException(Throwable cause) {
		super(cause);
	}

	public SchemaException(String message, Throwable cause) {
		super(message, cause);
	}


}
