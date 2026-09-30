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

	/**
	 * Creates a SchemaException that reports a specific SCIM error type (RFC 7644 Section 3.12), for example
	 * {@link com.independentid.scim.protocol.ScimResponse#ERR_TYPE_BADVAL} when a value does not conform to its
	 * attribute's type.
	 * @param message  The error detail.
	 * @param scimType The SCIM error type to report.
	 * @param cause    The underlying cause (may be null).
	 */
	public SchemaException(String message, String scimType, Throwable cause) {
		super(message, cause);
		this.scimType = scimType;
	}


}
