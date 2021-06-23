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
