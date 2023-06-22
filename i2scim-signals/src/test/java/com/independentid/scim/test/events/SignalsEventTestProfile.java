package com.independentid.scim.test.events;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/*
This profile is for full event testing with event handler enabled.
 */
public class SignalsEventTestProfile implements QuarkusTestProfile {


    public Map<String, String> getConfigOverrides() {

        HashMap<String, String> map = new HashMap<>(Map.of(
                "scim.prov.providerClass", MemoryProvider.class.getName(),

                "quarkus.log.min-level", "DEBUG",
                "logging.level.com.independentid.scim", "DEBUG",
                "quarkus.log.category.\"com.independentid.scim.test\".level", "DEBUG",
                "scim.root.dir", ".",
                "scim.security.enable", "false",

                "scim.event.enable", "true"
        ));

        map.putAll(Map.of(
                "scim.signals.enable", "true",
                "scim.signals.stream.issuer", "myissuer.io",
                "scim.signals.stream.issuer.jwksUrl", "http://localhost:8888/jwks/myissuer.io",

                "scim.signals.stream.auth", "TBD",
                "scim.signals.stream.aud", "example.com"
        ));
        return map;
    }

    @Override
    public String getConfigProfile() {
        return "SignalsEventTestProfile";
    }
}
