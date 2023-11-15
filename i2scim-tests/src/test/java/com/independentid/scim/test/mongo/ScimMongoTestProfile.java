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

package com.independentid.scim.test.mongo;

import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimMongoTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {

        Map<String,String> cmap = new HashMap<>( Map.of(
                "scim.prov.providerClass", MongoProvider.class.getName(),
                "scim.prov.mongo.dbname", "mongoTestSCIM",
                "scim.schema.path","classpath:/schema/scimSchemaTest.json",
                "quarkus.http.test-port","0",
                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","INFO",
                "quarkus.log.category.\"com.independentid.scim.test\".level","INFO",

                "scim.security.enable", "false",
                "scim.event.enable","false",
                "scim.root.dir","."
        ));
        // cmap.put("scim.prov.mongo.uri","mongodb://localhost:27117");
        TestUtils.configTestEndpointsMap(cmap);
        return cmap;
    }

    @Override
    public String getConfigProfile() {
        return "ScimMongoTestProfile";
    }
}
