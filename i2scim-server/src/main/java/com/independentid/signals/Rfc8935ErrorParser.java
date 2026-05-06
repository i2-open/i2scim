package com.independentid.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.serializer.JsonUtil;

import java.util.Optional;

public final class Rfc8935ErrorParser {

    private Rfc8935ErrorParser() {
    }

    public static Optional<Rfc8935Error> parse(byte[] body) {
        if (body == null || body.length == 0) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JsonUtil.getJsonTree(body);
        } catch (Exception e) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        JsonNode errNode = root.get("err");
        if (errNode == null || !errNode.isTextual()) {
            return Optional.empty();
        }
        String code = errNode.asText();
        if (!Rfc8935Error.KNOWN_CODES.contains(code)) {
            return Optional.empty();
        }
        JsonNode descNode = root.get("description");
        String description = descNode == null || !descNode.isTextual() ? null : descNode.asText();
        JsonNode jtiNode = root.get("jti");
        Optional<String> jti = jtiNode == null || !jtiNode.isTextual()
                ? Optional.empty() : Optional.of(jtiNode.asText());
        return Optional.of(new Rfc8935Error(code, description, jti));
    }
}
