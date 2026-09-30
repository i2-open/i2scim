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

import com.independentid.scim.test.misc.AbstractErrorHandlingTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the issue #107 error-handling regression tests ({@link AbstractErrorHandlingTest}) against the Mongo provider
 * with security disabled.
 */
@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
public class MongoErrorHandlingTest extends AbstractErrorHandlingTest {
}
