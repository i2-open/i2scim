package com.independentid.signals;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolved {@code scim.signals.risc.*} settings, as a plain value object so
 * {@link RiscEventMapper} can be unit-tested without MicroProfile config.
 */
public class RiscConfig {

    private final boolean enable;
    private final boolean allTypes;
    private final List<String> enabledTypes = new ArrayList<>();

    /**
     * @param enable {@code scim.signals.risc.enable} — RISC emission master switch.
     * @param types  {@code scim.signals.risc.types} — short names (e.g. {@code account-purged})
     *               of the RISC event types to emit; a single {@code *} (or empty) means all.
     */
    public RiscConfig(boolean enable, List<String> types) {
        this.enable = enable;
        if (types == null || types.isEmpty() || (types.size() == 1 && "*".equals(types.get(0)))) {
            this.allTypes = true;
        } else {
            this.allTypes = false;
            this.enabledTypes.addAll(types);
        }
    }

    public boolean isEnabled() {
        return this.enable;
    }

    /**
     * @param shortName a RISC event-type short name, e.g. {@code account-purged}.
     * @return true if this event type is configured for emission.
     */
    public boolean emits(String shortName) {
        return this.allTypes || this.enabledTypes.contains(shortName);
    }
}
