package com.independentid.signals;

import java.util.Optional;
import java.util.Set;

public record Rfc8935Error(String code, String description, Optional<String> jti) {

    public static final String AUTHENTICATION_FAILED = "authentication_failed";
    public static final String INVALID_REQUEST = "invalid_request";
    public static final String INVALID_AUDIENCE = "invalid_audience";
    public static final String INVALID_ISSUER = "invalid_issuer";
    public static final String INVALID_KEY = "invalid_key";
    public static final String JWS_SIGNATURE_FAILED = "jws_signature_failed";
    public static final String JWE_DECRYPTION_FAILED = "jwe_decryption_failed";

    public static final Set<String> KNOWN_CODES = Set.of(
            AUTHENTICATION_FAILED,
            INVALID_REQUEST,
            INVALID_AUDIENCE,
            INVALID_ISSUER,
            INVALID_KEY,
            JWS_SIGNATURE_FAILED,
            JWE_DECRYPTION_FAILED
    );
}
