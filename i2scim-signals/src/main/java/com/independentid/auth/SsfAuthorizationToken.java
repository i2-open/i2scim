package com.independentid.auth;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.UnauthorizedException;
import com.independentid.scim.serializer.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
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
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class SsfAuthorizationToken {
    protected final static Logger logger = LoggerFactory.getLogger(SsfAuthorizationToken.class);
    private final ObjectNode claims;
    private final static ObjectMapper mapper = JsonUtil.getMapper();

    public SsfAuthorizationToken() {
        this.claims = mapper.createObjectNode();
    }

    public SsfAuthorizationToken(String jsonClaims) throws InvalidJwtException, JsonProcessingException {
        this.claims = (ObjectNode) JsonUtil.getJsonTree(jsonClaims);

    }

    public SsfAuthorizationToken(JsonNode jsonToken) throws InvalidJwtException, JsonProcessingException {
        JwtClaims jwtClaims = JwtClaims.parse(jsonToken.toString());
        String jsonString = jwtClaims.toJson();
        this.claims = (ObjectNode) JsonUtil.getJsonTree(jsonString);
    }

    public SsfAuthorizationToken(String tokenString, Key issPubKey, Key audPrivKey) throws InvalidJwtException, JoseException, JsonProcessingException {
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

    @JsonSetter("sid")
    public void setStreamIds(String[] sids) {
        int nSids = sids.length;
        if (nSids > 0) {
            ArrayNode sidArray = this.claims.putArray("sid");
            for (String sid : sids)
                sidArray.add(sid);
        }
    }

    @JsonGetter("sid")
    public List<String> getSids() {
        JsonNode sidArray = this.claims.get("sid");
        if (sidArray == null)
            return new ArrayList<>();
        if (sidArray instanceof ArrayNode) {
            List<String> res = new ArrayList<>();
            sidArray.forEach(node -> res.add(node.asText()));
            return res;
        }
        return new ArrayList<>();
    }

    @JsonSetter("project_id")
    public void setProjectId(String id) {
        if (id != null)
            this.claims.put("project_id", id);
    }

    @JsonGetter("project_id")
    public String getProjectId() {
        JsonNode projectNode = this.claims.get("project_id");
        if (projectNode == null) return null;
        return projectNode.asText();
    }

    @JsonSetter("client_id")
    public void setClientId(String id) {
        if (id != null)
            this.claims.put("client_id", id);
    }

    @JsonGetter("client_id")
    public String getClientId() {
        JsonNode clientNode = this.claims.get("client_id");
        if (clientNode == null) return null;
        return clientNode.asText();
    }

    @JsonSetter("roles")
    public void setScopes(String[] scopes) {
        if (scopes.length > 0) {
            ArrayNode sidArray = this.claims.putArray("roles");
            for (String role : scopes)
                sidArray.add(role);
        }
    }

    @JsonGetter("roles")
    public List<String> getScopes() {
        JsonNode roleNode = this.claims.get("roles");
        if (roleNode == null)
            return null;
        if (roleNode instanceof ArrayNode) {
            List<String> res = new ArrayList<>();
            roleNode.forEach(node -> res.add(node.asText()));
            return res;
        }
        return null;
    }

    public boolean isScopeMatch(String[] scopesAccepted) {
        List<String> tokenScopes = this.getScopes();
        for (String accept : scopesAccepted) {
            for (String matchScope : tokenScopes) {
                if (matchScope.equalsIgnoreCase("root"))
                    return true;
                if (accept.equalsIgnoreCase(matchScope))
                    return true;
            }
        }
        return false;
    }

    @JsonSetter("jti")
    public void setJti(String jtiVal) {
        this.claims.put("jti", jtiVal);
    }

    @JsonSetter("jti")
    public String getJti() {
        return this.claims.get("jti").asText();
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
        JsonNode issNode = this.claims.get("iss");
        if (issNode == null) return null;
        return issNode.asText();
    }

    public void setAud(String aud) {
        if (aud.contains(",")) {
            String[] audienceArray = aud.split(",");
            List<String> audList = new ArrayList<>(Arrays.asList(audienceArray));
            this.setAud(audList);
            return;
        }
        ArrayNode anode = mapper.createArrayNode();
        anode.add(aud);
        this.claims.set("aud", anode);
    }

    @JsonSetter("aud")
    public void setAud(List<String> aud) {
        ArrayNode anode = mapper.createArrayNode();
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

    public void setExpDays(int days) {
        this.claims.put("exp", offsetFromNow(90).getValue());
    }

    public NumericDate getExp() {
        JsonNode node = this.claims.get("exp");
        if (node == null) return null;
        return NumericDate.fromSeconds(node.asLong());
    }

    private NumericDate offsetFromNow(int days) {
        NumericDate retDate = NumericDate.now();
        long seconds = days * 86400L;
        retDate.addSeconds(seconds);
        return retDate;
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
        this.setIssuer(issuer);
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

    public boolean isAuthorized(String sid, String[] scopesAccepted) {
        boolean socpeMatch = this.isScopeMatch(scopesAccepted);
        if (sid == null || sid.isEmpty())
            return socpeMatch;

        List<String> sids = this.getSids();
        if (sids.isEmpty())
            return socpeMatch;

        for (String matchSid : sids) {
            if (matchSid.equalsIgnoreCase(sid) || matchSid.equalsIgnoreCase("any"))
                return socpeMatch;
        }
        return false;
    }


    public static class AuthContext {
        public String streamId;
        public String projectId;
        public SsfAuthorizationToken eat;
    }

    public static class AuthIssuer {
        public String issuer;
        Key privateKey;
        PublicKey publicKey;

        public AuthIssuer(String issuer, Key issuerKey, PublicKey publicKey) {
            this.issuer = issuer;
            this.privateKey = issuerKey;
            this.publicKey = publicKey;
        }

        public String IssueProjectIat(AuthContext authContext) throws MalformedClaimException, JoseException {

            SsfAuthorizationToken eat = new SsfAuthorizationToken();
            eat.setExpDays(90);

            String projId = genProject();
            if (authContext != null && !authContext.projectId.isBlank())
                projId = authContext.projectId;
            eat.setProjectId(projId);
            eat.setIssuedAt(NumericDate.now());
            eat.setAud(this.issuer);
            eat.setJti(UUID.randomUUID().toString());

            return eat.JWS(this.privateKey);
        }

        public String IssuerClientToken(String clientId, String projectId, boolean admin) throws MalformedClaimException, JoseException {
            SsfAuthorizationToken eat = new SsfAuthorizationToken();
            eat.setIssuer(this.issuer);
            eat.setExpDays(90);
            if (clientId != null)
                eat.setClientId(clientId);
            if (projectId != null)
                eat.setProjectId(projectId);
            eat.setIssuedAt(NumericDate.now());
            eat.setAud(this.issuer);
            eat.setJti(UUID.randomUUID().toString());

            return eat.JWS(this.privateKey);
        }

        public AuthContext ValidateAuthorization(HttpServletRequest req, String[] acceptedScopes) throws UnauthorizedException {

            String authorization = req.getHeader("Authorization");

            if (authorization == null) return null;

            String[] parts = authorization.split(" ");
            if (parts.length < 2 || !parts[0].equalsIgnoreCase("Bearer"))
                throw new UnauthorizedException("Invalid authorization format");

            try {
                SsfAuthorizationToken token = new SsfAuthorizationToken(parts[1], publicKey, null);
                AuthContext ctx = new AuthContext();
                ctx.projectId = token.getProjectId();
                ctx.streamId = "";
                ctx.eat = token;
                return ctx;
            } catch (InvalidJwtException e) {
                throw new UnauthorizedException("Invalid JWT");
            } catch (JoseException e) {
                SsfAuthorizationToken.logger.error("Jose exception: " + e.getMessage());
                throw new UnauthorizedException("Jose error", e);
            } catch (JsonProcessingException e) {
                SsfAuthorizationToken.logger.error("JSON exception: " + e.getMessage());
                throw new UnauthorizedException("JSON error", e);
            }
        }

        private String genProject() {
            int length = 4;
            boolean useLetters = true;
            boolean useNumbers = true;
            return RandomStringUtils.random(length, useLetters, useNumbers);
        }
    }
}
