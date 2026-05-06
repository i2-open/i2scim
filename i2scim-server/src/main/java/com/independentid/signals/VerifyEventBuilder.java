package com.independentid.signals;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import org.jose4j.jwt.NumericDate;

import java.util.UUID;

public final class VerifyEventBuilder {

    public static final String VERIFICATION_EVENT_TYPE =
            "https://schemas.openid.net/secevent/ssf/event-type/verification";

    private VerifyEventBuilder() {
    }

    public static SecurityEventToken build(PushStream stream) {
        SecurityEventToken event = new SecurityEventToken();
        event.setIssuer(stream.iss);
        if (stream.aud != null) {
            event.setAud(stream.aud);
        }
        event.setJti(UUID.randomUUID().toString());
        event.setIssuedAt(NumericDate.now());
        ObjectNode payload = JsonUtil.getMapper().createObjectNode();
        event.AddEventPayload(VERIFICATION_EVENT_TYPE, payload);
        return event;
    }
}
