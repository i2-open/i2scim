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
