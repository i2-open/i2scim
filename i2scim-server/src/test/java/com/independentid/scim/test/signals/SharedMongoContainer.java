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

package com.independentid.scim.test.signals;

import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A single {@code mongo:8.0} Testcontainers instance shared by all signals
 * durability/store tests ({@code MongoPendingAckStoreTest}, {@code MongoPendingPushStoreTest},
 * {@code SignalsDurabilityTest}).
 *
 * <p>Started once per JVM — surefire runs with the default {@code reuseForks=true}, so all
 * three classes execute in the same fork — and deliberately never stopped: the Testcontainers
 * ryuk reaper terminates it on JVM exit. This replaces three separate container starts with one,
 * which is the dominant cost of these tests. Each test class uses a distinct database name so
 * they don't collide on the shared server.
 *
 * <p>The image tag matches {@code quarkus.mongodb.devservices.image-name} (mongo:8.0) so a full
 * suite run pulls a single mongo image rather than two.
 */
final class SharedMongoContainer {

    static final MongoDBContainer INSTANCE =
            new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    static {
        // Deliberately never stopped here — ryuk reaps it when the test JVM exits.
        INSTANCE.start();
    }

    private SharedMongoContainer() {}

    static String replicaSetUrl() {
        return INSTANCE.getReplicaSetUrl();
    }
}
