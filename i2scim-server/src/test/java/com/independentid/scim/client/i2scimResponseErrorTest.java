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

package com.independentid.scim.client;

import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.InvalidPathException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link i2scimResponse} error-response parsing, driven by stubbed HTTP responses (no server needed).
 */
public class i2scimResponseErrorTest {

    private static i2scimResponse respond(int code, String reason, String body, ContentType type) throws Exception {
        BasicClassicHttpResponse raw = new BasicClassicHttpResponse(code, reason);
        if (body != null)
            raw.setEntity(new StringEntity(body, type));
        // The client is only consulted when parsing successful results, so none is needed here.
        return new i2scimResponse(null, CloseableHttpResponse.adapt(raw));
    }

    private static i2scimResponse respondJson(int code, String reason, String body) throws Exception {
        return respond(code, reason, body, ContentType.APPLICATION_JSON);
    }

    @Test
    public void invalidPathIsInvalidPathException() throws Exception {
        i2scimResponse resp = respondJson(400, "Bad Request",
                "{\"schemas\":[\"" + ScimResponse.SCHEMA_ERROR + "\"],\"status\":\"400\",\"scimType\":\"invalidPath\","
                        + "\"detail\":\"No such attribute: nosuchattr\"}");

        assertThat(resp.hasError()).isTrue();
        assertThat(resp.getException())
                .isInstanceOf(InvalidPathException.class)
                .hasMessage("No such attribute: nosuchattr");
        assertThat(resp.getException().getScimType()).isEqualTo(ScimResponse.ERR_TYPE_PATH);
    }

    @Test
    public void nonJsonBadRequestFallsBackToStatus() throws Exception {
        i2scimResponse resp = respond(400, "Bad Request",
                "<html><body><h1>400 Bad Request</h1></body></html>", ContentType.TEXT_HTML);

        assertThat(resp.hasError()).isTrue();
        ScimException e = resp.getException();
        assertThat(e).isNotNull();
        assertThat(e.getStatus()).isEqualTo(400);
        assertThat(e.getMessage()).isEqualTo("Server responded with 400 Bad Request");
        assertThat(e.getScimType()).isNull();
    }

    @Test
    public void nonJsonConflictFallsBackToStatus() throws Exception {
        i2scimResponse resp = respond(409, "Conflict", "<html>proxy error</html>", ContentType.TEXT_HTML);

        assertThat(resp.getException())
                .isInstanceOf(ConflictException.class)
                .hasMessage("Server responded with 409 Conflict");
        assertThat(resp.getException().getScimType()).isNull();
    }

    @Test
    public void badRequestWithoutScimTypeUsesDetail() throws Exception {
        i2scimResponse resp = respondJson(400, "Bad Request",
                "{\"schemas\":[\"" + ScimResponse.SCHEMA_ERROR + "\"],\"status\":\"400\",\"detail\":\"Something was wrong\"}");

        ScimException e = resp.getException();
        assertThat(e).isNotNull();
        assertThat(e.getMessage()).isEqualTo("Something was wrong");
        assertThat(e.getScimType()).isNull();
    }

    @Test
    public void badRequestWithEmptyBodyFallsBackToStatus() throws Exception {
        for (String body : new String[]{"", "   ", "{}"}) {
            i2scimResponse resp = respondJson(400, "Bad Request", body);

            ScimException e = resp.getException();
            assertThat(e).as("body '%s'", body).isNotNull();
            assertThat(e.getMessage()).as("body '%s'", body).isEqualTo("Server responded with 400 Bad Request");
            assertThat(e.getScimType()).as("body '%s'", body).isNull();
        }
    }

    @Test
    public void badRequestWithoutEntityFallsBackToStatus() throws Exception {
        i2scimResponse resp = respond(400, "Bad Request", null, null);

        assertThat(resp.getException()).isNotNull();
        assertThat(resp.getException().getMessage()).isEqualTo("Server responded with 400 Bad Request");
        assertThat(resp.getException().getScimType()).isNull();
    }
}
