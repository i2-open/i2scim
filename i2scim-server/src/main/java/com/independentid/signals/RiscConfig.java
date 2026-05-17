package com.independentid.signals;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolved {@code scim.signals.risc.*} settings, as a plain value object so
 * {@link RiscEventMapper} can be unit-tested without MicroProfile config.
 */
public class RiscConfig {

    /** Default identifier attributes inspected for Identifier Changed (slice 3, #89). */
    public static final List<String> DEFAULT_IDENTIFIER_ATTRS = List.of("userName", "emails");

    /** Default RISC subject identifier format — the stable {@code scim} subject (slice 4, #90). */
    public static final String DEFAULT_SUBJECT_FORMAT = "scim";

    private final boolean enable;
    private final boolean allTypes;
    private final List<String> enabledTypes = new ArrayList<>();
    private final List<String> identifierAttrs = new ArrayList<>();
    private final boolean addRemoveIsChange;
    private final String subjectFormat;

    /**
     * @param enable {@code scim.signals.risc.enable} — RISC emission master switch.
     * @param types  {@code scim.signals.risc.types} — short names (e.g. {@code account-purged})
     *               of the RISC event types to emit; a single {@code *} (or empty) means all.
     */
    public RiscConfig(boolean enable, List<String> types) {
        this(enable, types, DEFAULT_IDENTIFIER_ATTRS, true);
    }

    /**
     * @param enable            {@code scim.signals.risc.enable} — RISC emission master switch.
     * @param types             {@code scim.signals.risc.types} — RISC event-type short names; {@code *} means all.
     * @param identifierAttrs   {@code scim.signals.risc.identifier.attrs} — attributes whose
     *                          primary value drives Identifier Changed.
     * @param addRemoveIsChange {@code scim.signals.risc.identifier.addRemoveIsChange} — whether a
     *                          pure add or removal of an identifier value counts as a change.
     */
    public RiscConfig(boolean enable, List<String> types,
                      List<String> identifierAttrs, boolean addRemoveIsChange) {
        this(enable, types, identifierAttrs, addRemoveIsChange, DEFAULT_SUBJECT_FORMAT);
    }

    /**
     * @param enable            {@code scim.signals.risc.enable} — RISC emission master switch.
     * @param types             {@code scim.signals.risc.types} — RISC event-type short names; {@code *} means all.
     * @param identifierAttrs   {@code scim.signals.risc.identifier.attrs} — attributes whose
     *                          primary value drives Identifier Changed.
     * @param addRemoveIsChange {@code scim.signals.risc.identifier.addRemoveIsChange} — whether a
     *                          pure add or removal of an identifier value counts as a change.
     * @param subjectFormat     {@code scim.signals.risc.subject.format} — the subject identifier
     *                          format ({@code scim}/{@code email}/{@code username}/{@code phone})
     *                          used for all RISC events.
     */
    public RiscConfig(boolean enable, List<String> types,
                      List<String> identifierAttrs, boolean addRemoveIsChange, String subjectFormat) {
        this.enable = enable;
        if (types == null || types.isEmpty() || (types.size() == 1 && "*".equals(types.get(0)))) {
            this.allTypes = true;
        } else {
            this.allTypes = false;
            this.enabledTypes.addAll(types);
        }
        if (identifierAttrs == null || identifierAttrs.isEmpty()) {
            this.identifierAttrs.addAll(DEFAULT_IDENTIFIER_ATTRS);
        } else {
            this.identifierAttrs.addAll(identifierAttrs);
        }
        this.addRemoveIsChange = addRemoveIsChange;
        this.subjectFormat = (subjectFormat == null || subjectFormat.isBlank())
                ? DEFAULT_SUBJECT_FORMAT : subjectFormat;
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

    /** @return the attribute names whose primary value drives Identifier Changed. */
    public List<String> getIdentifierAttrs() {
        return this.identifierAttrs;
    }

    /** @return whether a pure add or removal of an identifier value counts as a change. */
    public boolean isAddRemoveChange() {
        return this.addRemoveIsChange;
    }

    /** @return the RISC subject identifier format used for all RISC events. */
    public String getSubjectFormat() {
        return this.subjectFormat;
    }
}
