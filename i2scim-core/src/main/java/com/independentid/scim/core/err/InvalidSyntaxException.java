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

import com.independentid.scim.protocol.ScimResponse;

/**
 * @author pjdhunt
 * InvalidSyntaxException - The request body message structure was invalid or
 * did not conform to the request schema.
 */
public class InvalidSyntaxException extends ScimException {

	private static final long serialVersionUID = 1L;
	
	public final static String SCIM_TYPE = ScimResponse.ERR_TYPE_SYNTAX;
	
	/**
	 * InvalidSyntaxException - The request body message structure was 
	 * invalid or did not conform to the request schema.   
	 */
	public InvalidSyntaxException() {
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	/**
	 * InvalidSyntaxException - The request body message structure was 
	 * invalid or did not conform to the request schema.   
	 * @param message A description of the error to be returned in error detail
	 */
	public InvalidSyntaxException(String message) {
		super(message);
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public InvalidSyntaxException(Throwable cause) {
		super(cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

	public InvalidSyntaxException(String message, Throwable cause) {
		super(message, cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

}
