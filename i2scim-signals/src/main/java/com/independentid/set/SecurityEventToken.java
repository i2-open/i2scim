package com.independentid.set;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.serializer.JsonUtil;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.InvalidJwtSignatureException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.Key;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SecurityEventToken {
    private final static Logger logger = LoggerFactory.getLogger(SecurityEventToken.class);

    private final ObjectNode claims;

    private static ObjectMapper mapper = JsonUtil.getMapper();

    public SecurityEventToken() {
        this.claims = JsonUtil.getMapper().createObjectNode();
    }

    public SecurityEventToken(String jsonClaims) throws InvalidJwtException, JsonProcessingException {
        this.claims = (ObjectNode) JsonUtil.getJsonTree(jsonClaims);

    }

    public SecurityEventToken(JsonNode jsonToken) throws InvalidJwtException, JsonProcessingException {
        JwtClaims jwtClaims = JwtClaims.parse(jsonToken.toString());
        String jsonString = jwtClaims.toJson();
        this.claims = (ObjectNode) JsonUtil.getJsonTree(jsonString);
    }

    public SecurityEventToken(String tokenString, Key issPubKey, Key audPrivKey) throws InvalidJwtException, JoseException, JsonProcessingException {
        String payload = tokenString;
        if (audPrivKey != null) {
            JsonWebEncryption jwe = new JsonWebEncryption();
            jwe.setCompactSerialization(tokenString);
            jwe.setKey(audPrivKey);
            payload = jwe.getPayload();
        }

        JsonWebSignature jws = new JsonWebSignature();
        if (issPubKey == null) {
            jws.setAlgorithmConstraints(AlgorithmConstraints.NO_CONSTRAINTS);
        } else {
            jws.setAlgorithmConstraints(AlgorithmConstraints.DISALLOW_NONE);
            jws.setKey(issPubKey);
        }

        jws.setCompactSerialization(payload);

        if (!jws.verifySignature()) {
            throw new InvalidJwtSignatureException(jws, null);
        }
        payload = jws.getPayload();


        this.claims = (ObjectNode) JsonUtil.getJsonTree(payload);
    }

    public void SetScimSubjectIdSimple(String id, String externalId) {
        SubjectIdentifier subId = SubjectIdentifier.NewSubjectIdentifier("scim")
                .setId(id)
                .setExternalId(externalId);
        this.claims.set("sub_id", subId.toJsonNode());


    }


    public void SetScimSubjectId(ScimResource res) {
        SubjectIdentifier subId = new SubjectIdentifier(res);
        this.claims.set("sub_id", subId.toJsonNode());
    }

    public SubjectIdentifier getSubjectIdentifier() {
        JsonNode subNode = this.claims.get("sub_id");
        return JsonUtil.getMapper().convertValue(subNode, SubjectIdentifier.class);
    }

    public void SetScimSubjectId(RequestCtx ctx) {
        SubjectIdentifier subId = new SubjectIdentifier(ctx);
        this.claims.set("sub_id", subId.toJsonNode());
    }

    public void SetSub(String subject) {
        this.claims.put("sub", subject);
    }

    public String GetSub() throws MalformedClaimException {
        return this.claims.get("sub").asText();
    }

    @JsonSetter("jti")
    public void setJti(String jtiVal) {
        this.claims.put("jti", jtiVal);
    }

    @JsonSetter("jti")
    public String getJti() throws MalformedClaimException {
        return this.claims.get("jwt").asText();
    }

    @JsonSetter("txn")
    public void setTxn(String transactionId) {
        this.claims.put("txn", transactionId);
    }

    @JsonSetter("txn")
    public String getTxn() throws MalformedClaimException {
        return this.claims.get("txn").asText();
    }

    @JsonSetter("toe")
    public void setToe(NumericDate date) {
        this.claims.put("toe", date.getValue());
    }

    @JsonGetter("toe")
    public NumericDate getToe() throws MalformedClaimException {
        Number toeNumber = this.claims.get("toe").numberValue();

        if (toeNumber instanceof BigInteger) {
            throw new MalformedClaimException(toeNumber + " is unreasonable for a NumericDate");
        }
        return toeNumber != null ? NumericDate.fromSeconds(toeNumber.longValue()) : null;
    }

    @JsonSetter("iat")
    public void setIssuedAt(NumericDate date) {
        this.claims.put("iat", date.getValue());
    }

    @JsonGetter("iat")
    public NumericDate getIssuedAt() throws MalformedClaimException {
        Number toeNumber = this.claims.get("iat").numberValue();

        if (toeNumber instanceof BigInteger) {
            throw new MalformedClaimException(toeNumber + " is unreasonable for a NumericDate");
        }
        return toeNumber != null ? NumericDate.fromSeconds(toeNumber.longValue()) : null;
    }

    @JsonSetter("iss")
    public void setIssuer(String issuer) {

        this.claims.put("iss", issuer);
    }

    @JsonGetter("iss")
    public String getIssuer() {
        return this.claims.get("iss").asText();
    }

    public void setAud(String aud) {
        ArrayNode anode = JsonUtil.getMapper().createArrayNode();
        anode.add(aud);
        this.claims.set("aud", anode);
    }

    @JsonSetter("aud")
    public void setAud(List<String> aud) {
        ArrayNode anode = JsonUtil.getMapper().createArrayNode();
        aud.forEach(anode::add);
        this.claims.set("aud", anode);
    }

    @JsonGetter("aud")
    public List<String> getAud() throws MalformedClaimException {
        JsonNode auds = this.claims.get("aud");
        if (auds == null) return null;
        List<String> res = new ArrayList<>();
        auds.forEach(node -> res.add(node.asText()));
        return res;
    }

    public void AddEventPayload(String eventId, JsonNode eventPayload) {
        JsonNode events = this.claims.get("events");
        ObjectNode newEvents;
        if (events != null) {
            newEvents = events.deepCopy();
        } else {
            newEvents = JsonUtil.getMapper().createObjectNode();
        }

        newEvents.set(eventId, eventPayload);
        this.claims.set("events", newEvents);
    }

    public Iterator<String> getEventUris() {
        JsonNode events = GetEvents();
        if (events == null) return null;
        return events.fieldNames();
    }

    public JsonNode GetEvent(String eventUri) throws MalformedClaimException {
        JsonNode eventsNode = this.GetEvents();
        if (eventsNode == null) return null;
        return eventsNode.get(eventUri);
    }

    public JsonNode GetEvents() {
        return this.claims.get("events");
    }

    public String JWS(Key issuerSignKey) throws JoseException, MalformedClaimException {
        JsonWebSignature jws = new JsonWebSignature();
        String issuer = this.getIssuer();
        jws.setPayload(this.toJsonString());
        jws.setHeader("typ", "secevent+jwt");
        jws.setHeader("kid", issuer);
        if (issuerSignKey == null) {
            jws.setAlgorithmConstraints(AlgorithmConstraints.ALLOW_ONLY_NONE);
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.NONE);
        } else {
            jws.setAlgorithmConstraints(AlgorithmConstraints.DISALLOW_NONE);
            jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
            jws.setKey(issuerSignKey);
        }
        return jws.getCompactSerialization();
    }

    /**
     * JWE Signs and encrypts a SET token for transmission.
     *
     * @param issuer        Used to identify the key that corresponds to signer key (kid) in the token generated. This is used in JWKS.
     * @param issuerSignKey The key used to sign the SET (if null, inner signed token will not be signed)
     * @param audPubKey     The key used to encrypt the SET (must be provided)
     * @return String containing the encoded signed and encrypted JWT.
     * @throws JoseException if the token cannot be signed or encrypted.
     */
    public String JWE(String issuer, Key issuerSignKey, Key audPubKey) throws JoseException, MalformedClaimException {
        String payload = this.JWS(issuerSignKey);
        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.setHeader("typ", "secevent+jwt");
        jwe.setKey(audPubKey);
        if (audPubKey == null) {
            throw new JoseException("No audience key provided");
        }
        jwe.setPayload(payload);
        if (issuerSignKey == null) {
            jwe.setContentEncryptionAlgorithmConstraints(AlgorithmConstraints.ALLOW_ONLY_NONE);
        } else {
            jwe.setContentEncryptionAlgorithmConstraints(AlgorithmConstraints.DISALLOW_NONE);
            jwe.setContentTypeHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);

        }
        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP);
        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);

        return jwe.getCompactSerialization();
    }

    public JsonNode toJsonNode() {
        return this.claims;
    }

    public String toPrettyString() {
        return toJsonNode().toPrettyString();
    }

    public String toJsonString() {
        return toJsonNode().toString();
    }

}
