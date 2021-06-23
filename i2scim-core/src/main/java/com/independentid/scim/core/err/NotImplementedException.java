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

public class NotImplementedException extends ScimException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public NotImplementedException() {
		this.status = ScimResponse.ST_NOSUPPORT;
	}

	public NotImplementedException(String message) {
		super(message);
		this.status = ScimResponse.ST_NOSUPPORT;
	}

	public NotImplementedException(String message, String scimType) {
		super(message, scimType);
		this.status = ScimResponse.ST_NOSUPPORT;
	}

	public NotImplementedException(Throwable cause) {
		super(cause);
		this.status = ScimResponse.ST_NOSUPPORT;
	}

	public NotImplementedException(String message, Throwable cause) {
		super(message, cause);
		this.status = ScimResponse.ST_NOSUPPORT;
	}

}
