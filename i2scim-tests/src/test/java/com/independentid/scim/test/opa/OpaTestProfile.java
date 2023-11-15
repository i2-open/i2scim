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

package com.independentid.scim.test.opa;

import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.filter.OpaSecurityFilter;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class OpaTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        String env_opa_uri = System.getenv("TEST_OPA_URL");
        if (env_opa_uri == null)
            env_opa_uri = "http://localhost:8181/v1/data/i2scim";


        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.prov.memory.maxbackups", "2",
                "scim.prov.memory.backup.mins","5",

                "quarkus.http.test-port", "0",
                "quarkus.log.level","INFO",
                "logging.level.com.independentid.scim","DEBUG",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "DEBUG"

        ));
        cmap.putAll(Map.of(
                "quarkus.http.auth.basic", "true",
                "scim.security.enable", "true",
                "scim.security.authen.basic", "true",
                "scim.security.authen.jwt", "true",
                "scim.security.acis","classpath:/schema/aciSecurityTest.json",

                "scim.event.enable","false",
                "scim.root.dir","." ,
                "scim.security.mode", OpaSecurityFilter.ACCESS_TYPE_OPA, //enables local debug testing
                "scim.opa.authz.url", env_opa_uri
        ));
        TestUtils.configTestEndpointsMap(cmap);
        // cmap.put("scim.prov.mongo.uri","mongodb://localhost:27117");
        return cmap;


    }

    @Override
    public String getConfigProfile() {
        return "ScimAuthenTestProfile";
    }
}
