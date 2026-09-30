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
package com.independentid.scim.test.misc;

import com.independentid.scim.protocol.ScimResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.junit.jupiter.api.Test;

/**
 * Issue #107: an unexpected runtime failure inside a backend call must produce a SCIM Error body with status 500, not
 * the container's default error page.
 */
@QuarkusTest
@TestProfile(FaultInjectionTestProfile.class)
public class BackendFailureTest extends AbstractErrorHandlingTest {

    @Test
    public void d_backendRuntimeFailureIsScimInternalError() throws Exception {
        HttpGet get = new HttpGet(TestUtils.mapPathToReqUrl(baseUrl, "/Users/" + FaultInjectingProvider.FAULT_ID));

        assertScimError(get, ScimResponse.ST_INTERNAL, null);
    }
}
