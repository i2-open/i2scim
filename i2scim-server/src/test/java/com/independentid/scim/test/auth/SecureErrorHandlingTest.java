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

package com.independentid.scim.test.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.test.misc.AbstractErrorHandlingTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the issue #107 error-handling regression tests ({@link AbstractErrorHandlingTest}) with
 * {@code scim.security.enable=true} (Mongo provider), so the request context is built by the security filter.
 */
@QuarkusTest
@TestProfile(ScimAuthTestProfile.class)
public class SecureErrorHandlingTest extends AbstractErrorHandlingTest {

    @Inject
    ConfigMgr cmgr;

    @Override
    protected void authorize(HttpUriRequestBase request) {
        String cred = cmgr.getRootUser() + ":" + cmgr.getRootPassword();
        request.addHeader(HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Bulk sub-operations are not yet evaluated against ACIs (their request contexts are not populated by the
     * security filter), so with security enabled they must fail closed with a per-operation 403 rather than an
     * internal error, while malformed operations still report their own 400.
     */
    @Override
    @Test
    public void e_bulkIsolatesMalformedOperations() throws Exception {
        String body = BULK_REQUEST_START + "\"Operations\":["
                + bulkCreateUser("secGood1", "bulkSecGood1") + ","
                + BULK_BAD_DATA + "]}";

        JsonNode ops = assertBulkResponse(body);
        assertThat(ops.size()).as("one result per operation").isEqualTo(2);
        assertBulkOpError(ops.get(0), "POST", "secGood1", ScimResponse.ST_FORBIDDEN);
        assertBulkOpError(ops.get(1), "POST", "badData");
    }
}
