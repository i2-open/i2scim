package com.independentid.scim.test.events;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class SsefEventTestProfile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {

        return new HashMap<>(Map.of(
                "scim.prov.providerClass", MemoryProvider.class.getName(),

                "quarkus.log.min-level", "DEBUG",
                "logging.level.com.independentid.scim", "DEBUG",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "DEBUG",
                "scim.root.dir", ".",  //enables local debug testing

                "scim.security.enable", "false",
                "scim.ssef.enable","true",
                "scim.event.enable", "true"
        ));
    }

    @Override
    public String getConfigProfile() {
        return "SsefEventTestProfile";
    }
}
