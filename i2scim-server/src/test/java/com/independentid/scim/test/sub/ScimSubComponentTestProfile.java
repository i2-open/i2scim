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

package com.independentid.scim.test.sub;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class ScimSubComponentTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String,String> cmap = new HashMap<>( Map.of(
                "scim.schema.path","/schema/scimSchemaTest.json",
                "scim.test.configOnly","true",
                "scim.json.pretty","true",

                "quarkus.log.min-level","DEBUG",
                "logging.level.com.independentid.scim","INFO",
                "quarkus.log.category.\"com.independentid.scim.test\".level","INFO",

                "scim.prov.providerClass", MemoryProvider.class.getName(),
                "scim.prov.persist.schema","false",
                "scim.security.enable", "false",
                "scim.event.enable","false"
        ));
        cmap.put("scim.root.dir",".");
        cmap.put("scim.kafka.log.enable","false"); //enables local debug testing
        return cmap;
    }

    @Override
    public String getConfigProfile() {
        return "ScimSubComponentConfigTestProfile";
    }
}
