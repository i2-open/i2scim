package com.independentid.signals;

import com.independentid.scim.backend.IIdentifierGenerator;
import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.set.SubjectIdentifier;
import org.jose4j.jwt.NumericDate;

import java.util.ArrayList;
import java.util.List;

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
        }
        return events;
    }

    /** RISC events are emitted only for {@code User} resources. */
    private boolean isUser(ScimResource res) {
        return res != null && "User".equals(res.getResourceType());
    }

    /** Builds a RISC account-level SET sharing the operation's txn and toe. */
    private SecurityEventToken buildAccountEvent(Operation op, ScimResource subject, String eventTypeUri) {
        SecurityEventToken event = new SecurityEventToken();
        event.SetSubjectIdentifier(new SubjectIdentifier(subject));
        String txn = op.getRequestCtx().getTranId();
        if (txn != null) event.setTxn(txn);
        event.setJti(idGen.getNewIdentifier());
        event.setToe(NumericDate.fromMilliseconds(op.getStats().getFinishDate().getTime()));
        // Account events omit the optional RISC "reason" attribute.
        event.AddEventPayload(eventTypeUri, JsonUtil.getMapper().createObjectNode());
        return event;
    }
}
