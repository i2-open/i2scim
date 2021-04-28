/*
 * Copyright (c) 2021.
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

import javax.servlet.http.HttpServletResponse;

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
