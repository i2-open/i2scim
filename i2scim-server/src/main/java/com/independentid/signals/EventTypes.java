package com.independentid.signals;

/**
 * SCIM Event Types as defined in draft-ietf-scim-events
 * https://datatracker.ietf.org/doc/draft-ietf-scim-events/
 */
public class EventTypes {

    // Feed Events - Resources added/removed from event feeds
    public final static String FEED_ADD = "urn:ietf:params:scim:event:feed:add";
    public final static String FEED_REMOVE = "urn:ietf:params:scim:event:feed:remove";

    // Provisioning Events - Resource lifecycle operations
    public final static String PROV_CREATE_FULL = "urn:ietf:params:scim:event:prov:create:full";
    public final static String PROV_CREATE_NOTICE = "urn:ietf:params:scim:event:prov:create:notice";
    public final static String PROV_PUT_FULL = "urn:ietf:params:scim:event:prov:put:full";
    public final static String PROV_PUT_NOTICE = "urn:ietf:params:scim:event:prov:put:notice";
    public final static String PROV_PATCH_FULL = "urn:ietf:params:scim:event:prov:patch:full";
    public final static String PROV_PATCH_NOTICE = "urn:ietf:params:scim:event:prov:patch:notice";
    public final static String PROV_DELETE = "urn:ietf:params:scim:event:prov:delete";
    public final static String PROV_ACTIVATE = "urn:ietf:params:scim:event:prov:activate";
    public final static String PROV_DEACTIVATE = "urn:ietf:params:scim:event:prov:deactivate";

    // Miscellaneous Events
    public final static String MISC_ASYNC_RESP = "urn:ietf:params:scim:event:misc:asyncresp";
}
