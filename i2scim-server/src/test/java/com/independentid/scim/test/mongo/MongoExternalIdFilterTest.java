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

package com.independentid.scim.test.mongo;

import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.test.misc.AbstractExternalIdFilterTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the issue #99 externalId filter regression tests ({@link AbstractExternalIdFilterTest}) against the
 * Mongo provider.
 */
@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
public class MongoExternalIdFilterTest extends AbstractExternalIdFilterTest {

    @Override
    protected IScimProvider provider() {
        return (MongoProvider) InjectionManager.getInstance().getProvider();
    }
}
