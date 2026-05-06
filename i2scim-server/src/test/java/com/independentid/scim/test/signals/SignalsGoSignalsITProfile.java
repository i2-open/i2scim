/*
 * Test profile for SignalsGoSignalsIT. Points i2scim at a goSignals
 * instance launched via docker-compose-signals.yml.
 */

package com.independentid.scim.test.signals;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class SignalsGoSignalsITProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.ofEntries(
                Map.entry("scim.signals.enable", "true"),
                Map.entry("scim.event.enable", "true"),
                Map.entry("scim.signals.test", "true"),
                Map.entry("scim.signals.ssf.serverUrl", "http://localhost:8888"),
                Map.entry("scim.signals.pub.iss", "i2scim-it"),
                Map.entry("scim.signals.pub.aud", "goSignals1"),
                Map.entry("scim.signals.rcv.iss", "goSignals1"),
                Map.entry("scim.signals.rcv.aud", "i2scim-it"),
                Map.entry("scim.signals.pub.unauthorized.retry.max", "2"),
                Map.entry("scim.signals.pub.unauthorized.retry.delay", "100"),
                Map.entry("scim.signals.pub.status.check.interval", "500"),
                Map.entry("scim.signals.pub.idle.verify.interval", "2000"),
                Map.entry("scim.signals.rcv.unauthorized.retry.max", "2"),
                Map.entry("scim.signals.rcv.unauthorized.retry.delay", "100"),
                Map.entry("scim.signals.rcv.status.check.interval", "500"),
                Map.entry("scim.prov.providerClass", "com.independentid.scim.backend.memory.MemoryProvider"),
                Map.entry("scim.security.enable", "false")
        );
    }
}
