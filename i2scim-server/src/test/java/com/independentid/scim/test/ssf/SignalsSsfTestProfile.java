package com.independentid.scim.test.ssf;

import com.independentid.scim.backend.memory.MemoryProvider;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*
This profile is sets up testing to confirm the MockSignals server and test StreamHandler class. EventHandler is disabled.
 */
public class SignalsSsfTestProfile implements QuarkusTestProfile {

    public Map<String, String> getConfigOverrides() {
        String cfgFile = "cfg.json";
        try {
            File tempFile = File.createTempFile("SsfConfig", ".json");
            cfgFile = tempFile.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
                "scim.signals.test", "true",
                "scim.signals.enable", "false",
                "scim.signals.ssf.configFile", cfgFile,
                "scim.signals.pub.pem.path", "/data/issuer.pem",
                "scim.signals.pub.iss", "myissuer.io",
                "scim.signals.pub.aud", "example.com",
                "scim.signals.rcv.iss", "myissuer.io",
                "scim.signals.rcv.iss.jwksJson", "{\"keys\":[{\"e\":\"AQAB\",\"kid\":\"myissuer.io\",\"kty\":\"RSA\",\"n\":\"rCPRnmNNptz1Y4QIAwbDDiXgYvB2PV_X2_LCtEOaV68_wxynXVErPQzJvpA6Zlr1dn0w1H2azxw_G1jgQAcw7yg3YWEQCh89kwcRZVA33dwtIMIatNtwIKB40nbW4-NoHwg2UfiyG7i2xO8VMi4N-hBp8qnhrR2JvWAuhykKwLEXdyl9-0rGBlxxruvqkJsnrhEiMQkk9-B2mw36CsL1XHd9GFTLcNF8Gc55oI36qsMqTDRQZtHbc19WGwEopunuEqkG1AAsRzAlyjS_-KMT5biVB1WXOz5WC_6XBrdlK_pWNDIEOUG0jzS18eenHb3ZeefpJp6M1vs09Rc67nHqvw\"}]}", "scim.signals.rcv.aud", "example.com"
        ));

        map.putAll(Map.of(
                "quarkus.http.auth.permission.permit1.paths=", "/*",
                "quarkus.http.auth.permission.permit1.policy", "permit",
                "quarkus.http.auth.permission.permit1.methods", "GET,POST,HEAD,DELETE,PATCH"
        ));
        return map;
    }

    @Override
    public String getConfigProfile() {
        return "SignalsEventTestProfile";
    }
}
