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

package com.independentid.scim.test.devops;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimDevOpsTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> cmap = new HashMap<>(Map.of(
                "scim.prov.mongo.dbname", "healthTestSCIM",
                "scim.prov.mongo.uri", "mongodb://admin:t0p-Secret@localhost:27017",

                "quarkus.http.test-port", "0",
                "quarkus.log.min-level","DEBUG",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "INFO",

                "scim.security.enable", "true",
                "scim.security.authen.basic", "true",
                "scim.security.authen.jwt", "true",
                "scim.security.acis","classpath:/schema/aciSecurityTest.json"
        ));
        cmap.putAll(Map.of(
                "scim.event.enable","false",
                "scim.root.dir","."  //enables local debug testing

        ));
        return cmap;


    }

    @Override
    public String getConfigProfile() {
        return "ScimDevOpsTestProfile";
    }
}
