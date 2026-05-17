package com.independentid.signals;

/**
 * OpenID RISC event-type URIs (OpenID RISC Profile 1.0), parallel to {@link EventTypes}.
 * The last path segment of each URI is the short name used in the
 * {@code scim.signals.risc.types} configuration list.
 */
public class RiscEventTypes {

    public final static String ACCOUNT_PURGED =
            "https://schemas.openid.net/secevent/risc/event-type/account-purged";
    public final static String ACCOUNT_DISABLED =
            "https://schemas.openid.net/secevent/risc/event-type/account-disabled";
    public final static String ACCOUNT_ENABLED =
            "https://schemas.openid.net/secevent/risc/event-type/account-enabled";
    public final static String IDENTIFIER_CHANGED =
            "https://schemas.openid.net/secevent/risc/event-type/identifier-changed";

    /**
     * @param eventTypeUri a RISC event-type URI
     * @return the short name (last path segment), e.g. {@code account-purged}.
     */
    public static String shortName(String eventTypeUri) {
        int slash = eventTypeUri.lastIndexOf('/');
        return slash < 0 ? eventTypeUri : eventTypeUri.substring(slash + 1);
    }
}
