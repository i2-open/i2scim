/*
 * Copyright 2026.  Independent Identity Incorporated
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
 * InvalidPathException - HTTP 400 with scimType {@code invalidPath} (RFC 7644 §3.12): the "path" attribute was
 * invalid or malformed, e.g. it names an attribute or sub-attribute that is not defined in the schema. A valid path
 * whose value filter matches nothing is {@link NoTargetException} instead.
 */
public class InvalidPathException extends ScimException {

	private static final long serialVersionUID = 1L;

	public final static String SCIM_TYPE = ScimResponse.ERR_TYPE_PATH;

	public InvalidPathException() {
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public InvalidPathException(String message) {
		super(message);
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public InvalidPathException(Throwable cause) {
		super(cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

	public InvalidPathException(String message, Throwable cause) {
		super(message, cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

}
