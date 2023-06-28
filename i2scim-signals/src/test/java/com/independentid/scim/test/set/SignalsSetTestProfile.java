package com.independentid.scim.test.set;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/*
This profile is used to test the mapper and other functions but does not turn on the event handler.
 */

public class SignalsSetTestProfile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {

        return new HashMap<>(Map.of(
                "scim.prov.providerClass", MemoryProvider.class.getName(),

                "quarkus.log.min-level", "DEBUG",
                "logging.level.com.independentid.scim", "DEBUG",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "DEBUG",
                "scim.root.dir", ".",  //enables local debug testing

                "scim.security.enable", "false",
                "scim.signals.enable", "false",
                "scim.event.enable", "true"
                //   "scim.signals.stream.receiver.jwksUrl","NONE"
        ));
    }

    @Override
    public String getConfigProfile() {
        return "SignalsEventMinTestProfile";
    }
}
