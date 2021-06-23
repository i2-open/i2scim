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

package com.independentid.scim.test.http;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimHttpTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.schema.path","/schema/scimSchemaTest.json",  //this test schema changes some default returns for testing

                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.prov.memory.maxbackups", "2",
                "scim.prov.memory.backup.mins","5",

                //"scim.prov.mongo.dbname", "testHttpSCIM",
                //"scim.prov.mongo.uri","mongodb://localhost:27017",
                //"scim.prov.providerClass","com.independentid.scim.backend.mongo.MongoProvider",

                "quarkus.http.test-port","0",
                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","INFO",
                "quarkus.log.category.\"com.independentid.scim.test\".level","INFO"
        ));
        cmap.putAll(Map.of(
                "scim.json.pretty","true",
                "scim.security.enable", "false",
                "scim.event.enable","false",
                "scim.root.dir","."  //enables local debug testing
        ));
        return cmap;
    }

    @Override
    public String getConfigProfile() {
        return "ScimHttpTestProfile";
    }
}
