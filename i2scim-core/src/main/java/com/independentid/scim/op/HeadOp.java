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

package com.independentid.scim.op;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.protocol.RequestCtx;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class HeadOp extends GetOp {

    public HeadOp(HttpServletRequest req, HttpServletResponse resp) {
        super(req, resp);
    }

    @Override
    public void doSuccess(JsonGenerator gen) {
         // don't serialize the result

        if (this.scimresp != null)
            this.scimresp.setHeaders(ctx);
    }
}
