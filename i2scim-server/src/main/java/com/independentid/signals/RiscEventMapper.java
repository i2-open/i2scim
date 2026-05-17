package com.independentid.signals;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.IIdentifierGenerator;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.protocol.JsonPatchOp;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.BooleanValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.set.SubjectIdentifier;
import org.jose4j.jwt.NumericDate;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Derives OpenID RISC {@link SecurityEventToken}s from a completed SCIM
 * {@link Operation}. A pure transformation — no I/O, no Quarkus lookups — so it
 * can be unit-tested directly. Slice 1 (#87) derives Account Purged from a
 * {@code User} DELETE.
 */
public class RiscEventMapper {

    private final RiscConfig config;
    private final IIdentifierGenerator idGen;

    public RiscEventMapper(RiscConfig config, IIdentifierGenerator idGen) {
        this.config = config;
        this.idGen = idGen;
    }

    /**
     * Maps a completed operation to the RISC events it implies.
     * @param op a completed SCIM operation.
     * @return the derived RISC SETs; empty when no RISC event applies.
     */
    public List<SecurityEventToken> mapToRiscEvents(Operation op) {
        List<SecurityEventToken> events = new ArrayList<>();
        if (op == null || !config.isEnabled()) return events;
        RequestCtx ctx = op.getRequestCtx();
        if (ctx == null) return events;

        if ("DEL".equals(op.getScimType())) {
            ScimResource preImage = ctx.getPreImageResource();
            if (isUser(preImage) && config.emits(RiscEventTypes.shortName(RiscEventTypes.ACCOUNT_PURGED))) {
                events.add(buildAccountEvent(op, preImage, RiscEventTypes.ACCOUNT_PURGED));
            }
        } else if ("ADD".equals(op.getScimType())) {
            ScimResource postImage = op.getTransactionResource();
            if (isUser(postImage)) {
                addActiveEvent(events, op, postImage, activeEventType(isActive(postImage)));
            }
        } else if ("PUT".equals(op.getScimType())) {
            ScimResource preImage = ctx.getPreImageResource();
            if (isUser(preImage)) {
                ScimResource postImage = op.getTransactionResource();
                boolean before = isActive(preImage);
                boolean after = isActive(postImage);
                if (before != after) {
                    addActiveEvent(events, op, preImage, activeEventType(after));
                }
                addIdentifierEvents(events, op, preImage, postImage);
            }
        } else if ("PAT".equals(op.getScimType()) && op instanceof PatchOp) {
            ScimResource preImage = ctx.getPreImageResource();
            if (isUser(preImage)) {
                PatchOp pop = (PatchOp) op;
                Boolean signal = activeSignalFromPatch(pop);
                if (signal != null) {
                    addActiveEvent(events, op, preImage, activeEventType(signal));
                }
                ScimResource postImage = applyPatch(preImage, pop, ctx);
                if (postImage != null) {
                    addIdentifierEvents(events, op, preImage, postImage);
                }
            }
        }
        return events;
    }

    /** RISC events are emitted only for {@code User} resources. */
    private boolean isUser(ScimResource res) {
        return res != null && "User".equals(res.getResourceType());
    }

    /** @return the RISC event-type URI for the given {@code active} state. */
    private String activeEventType(boolean active) {
        return active ? RiscEventTypes.ACCOUNT_ENABLED : RiscEventTypes.ACCOUNT_DISABLED;
    }

    /** Reads a User's {@code active} state; an absent {@code active} is treated as enabled. */
    private boolean isActive(ScimResource res) {
        if (res == null) return true;
        try {
            Value v = res.getValue("active");
            if (v instanceof BooleanValue) {
                Boolean b = ((BooleanValue) v).getRawValue();
                return b == null || b;
            }
        } catch (SchemaException e) {
            // active is undefined for this resource — treat as enabled
        }
        return true;
    }

    /**
     * Inspects a JSON Patch request for operations that set the {@code active}
     * attribute. The last such operation wins.
     * @return the resulting {@code active} state, or {@code null} if no patch
     *         operation touched {@code active}.
     */
    private Boolean activeSignalFromPatch(PatchOp op) {
        Boolean signal = null;
        JsonPatchRequest req = op.getPatchRequest();
        if (req == null) return null;
        for (Iterator<JsonPatchOp> it = req.iterator(); it.hasNext(); ) {
            Boolean s = activeFromPatchOp(it.next());
            if (s != null) signal = s;
        }
        return signal;
    }

    /** @return the {@code active} state implied by one patch op, or {@code null} if it does not touch {@code active}. */
    private Boolean activeFromPatchOp(JsonPatchOp patchOp) {
        boolean targetsActive = "active".equalsIgnoreCase(attrName(patchOp.path));
        if (JsonPatchOp.OP_ACTION_REMOVE.equals(patchOp.op)) {
            // Removing active reverts it to the schema default — enabled.
            return targetsActive ? Boolean.TRUE : null;
        }
        // add or replace: the new value is taken directly as the signal.
        if (targetsActive) {
            return patchOp.jsonValue != null && patchOp.jsonValue.asBoolean();
        }
        // A path-less value object may carry active among other attributes.
        if (patchOp.path == null && patchOp.jsonValue != null
                && patchOp.jsonValue.isObject() && patchOp.jsonValue.has("active")) {
            return patchOp.jsonValue.get("active").asBoolean();
        }
        return null;
    }

    /**
     * Derives a PATCH post-image by applying the patch to a copy of the
     * pre-image — a pure, in-memory transformation (no backend I/O).
     * @return the post-image, or {@code null} if it cannot be derived.
     */
    private ScimResource applyPatch(ScimResource preImage, PatchOp op, RequestCtx ctx) {
        JsonPatchRequest req = op.getPatchRequest();
        if (req == null) return null;
        try {
            ScimResource postImage = preImage.copy(null);
            postImage.modifyResource(req, ctx);
            return postImage;
        } catch (ScimException | ParseException e) {
            return null;
        }
    }

    /** @return the bare attribute name from a patch path (strips any schema URN prefix). */
    private String attrName(String path) {
        if (path == null) return null;
        int colon = path.lastIndexOf(':');
        return colon < 0 ? path : path.substring(colon + 1);
    }

    /** Adds the given account-state RISC event when its type is configured for emission. */
    private void addActiveEvent(List<SecurityEventToken> events, Operation op,
                                ScimResource subject, String eventTypeUri) {
        if (config.emits(RiscEventTypes.shortName(eventTypeUri))) {
            events.add(buildAccountEvent(op, subject, eventTypeUri));
        }
    }

    /** Builds a RISC account-level SET sharing the operation's txn and toe. */
    private SecurityEventToken buildAccountEvent(Operation op, ScimResource subject, String eventTypeUri) {
        SecurityEventToken event = newEvent(op, subject);
        // Account events omit the optional RISC "reason" attribute.
        event.AddEventPayload(eventTypeUri, JsonUtil.getMapper().createObjectNode());
        return event;
    }

    /**
     * Emits an Identifier Changed SET for each configured identifier attribute
     * whose primary value differs between the pre- and post-image.
     */
    private void addIdentifierEvents(List<SecurityEventToken> events, Operation op,
                                     ScimResource preImage, ScimResource postImage) {
        if (!config.emits(RiscEventTypes.shortName(RiscEventTypes.IDENTIFIER_CHANGED))) return;
        for (String attr : config.getIdentifierAttrs()) {
            String before = IdentifierExtractor.primaryValue(preImage, attr);
            String after = IdentifierExtractor.primaryValue(postImage, attr);
            if (isIdentifierChange(before, after)) {
                events.add(buildIdentifierEvent(op, preImage, after));
            }
        }
    }

    /**
     * @return true when {@code before}→{@code after} is an identifier change.
     *         A pure add or removal (one side {@code null}) counts only when
     *         {@code addRemoveIsChange} is configured.
     */
    private boolean isIdentifierChange(String before, String after) {
        if (Objects.equals(before, after)) return false;
        if (before == null || after == null) return config.isAddRemoveChange();
        return true;
    }

    /** Builds a RISC Identifier Changed SET carrying the new value in {@code new-value}. */
    private SecurityEventToken buildIdentifierEvent(Operation op, ScimResource subject, String newValue) {
        SecurityEventToken event = newEvent(op, subject);
        ObjectNode payload = JsonUtil.getMapper().createObjectNode();
        if (newValue != null) payload.put("new-value", newValue);
        event.AddEventPayload(RiscEventTypes.IDENTIFIER_CHANGED, payload);
        return event;
    }

    /** Builds a bare RISC SET — subject, distinct jti, and the operation's shared txn/toe. */
    private SecurityEventToken newEvent(Operation op, ScimResource subject) {
        SecurityEventToken event = new SecurityEventToken();
        event.SetSubjectIdentifier(new SubjectIdentifier(subject));
        String txn = op.getRequestCtx().getTranId();
        if (txn != null) event.setTxn(txn);
        event.setJti(idGen.getNewIdentifier());
        event.setToe(NumericDate.fromMilliseconds(op.getStats().getFinishDate().getTime()));
        return event;
    }
}
