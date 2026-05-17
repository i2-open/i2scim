package com.independentid.scim.test.events;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for {@link RiscEventMapperTest}: the memory-backend setup of
 * {@link SignalsEventTestProfile} but with signals and event publication
 * disabled. The mapper is exercised directly, so the test must not also push
 * SETs to the shared {@link MockSignalsServer} (which would pollute the static
 * counters other event tests assert on).
 */
public class RiscMapperTestProfile extends SignalsEventTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = new HashMap<>(super.getConfigOverrides());
        map.put("scim.signals.enable", "false");
        map.put("scim.event.enable", "false");
        return map;
    }

    @Override
    public String getConfigProfile() {
        return "RiscMapperTestProfile";
    }
}
