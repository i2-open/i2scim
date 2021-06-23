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
