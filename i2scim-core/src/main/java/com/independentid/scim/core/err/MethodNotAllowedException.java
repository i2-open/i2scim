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

import jakarta.servlet.http.HttpServletResponse;

/**
 * MethodNotAllowed when HTTP Method used against a resource where it is not appropriate.  E.g. patch on a container (top level).
 */
public class MethodNotAllowedException extends ScimException {
    { status = HttpServletResponse.SC_METHOD_NOT_ALLOWED; }
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public MethodNotAllowedException () {

    }

    public MethodNotAllowedException (String message) {
        super(message);

    }

    public MethodNotAllowedException (String message, String scimType) {
        super(message, scimType);
    }

    public MethodNotAllowedException (Throwable cause) {
        super(cause);

        this.detail = cause.getLocalizedMessage();
    }

    public MethodNotAllowedException (String message, Throwable cause) {
        super(message, cause);
        this.detail = cause.getLocalizedMessage();
    }
}
