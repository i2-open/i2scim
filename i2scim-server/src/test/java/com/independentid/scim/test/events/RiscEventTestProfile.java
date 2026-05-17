package com.independentid.scim.test.events;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile for RISC end-to-end push tests: the {@link SignalsEventTestProfile}
 * signals setup with RISC emission enabled.
 */
public class RiscEventTestProfile extends SignalsEventTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = new HashMap<>(super.getConfigOverrides());
        map.put("scim.signals.risc.enable", "true");
        return map;
    }

    @Override
    public String getConfigProfile() {
        return "RiscEventTestProfile";
    }
}
