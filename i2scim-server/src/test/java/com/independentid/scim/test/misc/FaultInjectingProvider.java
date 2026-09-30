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

import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Test-only memory provider that throws an unexpected {@link RuntimeException} when a resource with id
 * {@link #FAULT_ID} is retrieved. It is only active in {@link FaultInjectionTestProfile}, which enables it as a CDI
 * alternative, so that tests can verify a backend failure yields a SCIM 500 error response (issue #107).
 */
@Alternative
@ApplicationScoped
public class FaultInjectingProvider extends MemoryProvider {

    public static final String FAULT_ID = "forceRuntimeFailure";

    @Override
    public ScimResponse get(RequestCtx ctx) throws ScimException {
        if (FAULT_ID.equals(ctx.getPathId()))
            throw new IllegalStateException("Injected backend failure");
        return super.get(ctx);
    }
}
