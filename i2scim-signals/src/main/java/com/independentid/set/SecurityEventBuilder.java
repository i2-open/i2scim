package com.independentid.set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.serializer.JsonUtil;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.*;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.ErrorCodes;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import javax.crypto.SecretKey;
import java.security.Key;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class SecurityEventBuilder implements JwtClaimsBuilder  {
    ObjectMapper mapper = JsonUtil.getMapper();
    JwtClaimsBuilder builder;
    ArrayNode events;

    public static  SecurityEventBuilder Claims() {
        SecurityEventBuilder builder = new SecurityEventBuilder();
        builder.builder = Jwt.claims();
        builder.events = builder.mapper.createArrayNode();
        return builder;
    }

    public JwtClaims Parse(String tokenString, Key key,String issuer,String aud) {
        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setExpectedIssuer(issuer) // whom the JWT needs to have been issued by
                .setExpectedAudience(aud) // to whom the JWT is intended for
                .setVerificationKey(key) // verify the signature with the public key
                .build(); // create the JwtConsumer instance

        JwtClaims jwtClaims = null;
        try
        {
            //  Validate the JWT and process it to the Claims
            jwtClaims = jwtConsumer.processToClaims(tokenString);
            System.out.println("JWT validation succeeded! " + jwtClaims);
        } catch (InvalidJwtException e)
        {
            // InvalidJwtException will be thrown, if the JWT failed processing or validation in anyway.
            // Hopefully with meaningful explanations(s) about what went wrong.
            System.out.println("Invalid JWT! " + e);

            // Programmatic access to (some) specific reasons for JWT invalidity is also possible
            // should you want different error handling behavior for certain conditions.


            // Or maybe the audience was invalid
            if (e.hasErrorCode(ErrorCodes.AUDIENCE_INVALID))
            {
                try {
                    System.out.println("JWT had wrong audience: " + e.getJwtContext().getJwtClaims().getAudience());
                } catch (MalformedClaimException me) {
                    System.out.println("JWT had invalid audience (with error parsing)");
                }
            }
        }
        return jwtClaims;
    }

    public SecurityEventBuilder AddEventPayload(String eventId, JsonNode node) {
        ObjectNode event = mapper.createObjectNode();
        event.set(eventId,node);
        this.events.add(event);
        this.builder = this.builder.claim("events",this.events);
        return this;
    }

    /**
     * Set an issuer 'iss' claim
     *
     * @param issuer the issuer
     * @return JwtClaimsBuilder
     */
    @Override
    public SecurityEventBuilder issuer(String issuer) {
        this.builder = this.builder.issuer(issuer);
        return this;
    }

    /**
     * Set a subject 'sub' claim
     *
     * @param subject the subject
     * @return JwtClaimsBuilder
     */
    @Override
    public SecurityEventBuilder subject(String subject) {
        this.builder = this.builder.subject(subject);
        return this;
    }

    /**
     * Set a 'upn' claim
     *
     * @param upn the upn
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder upn(String upn) {
        this.builder = this.builder.upn(upn);
        return this;
    }

    /**
     * Set a preferred user name 'preferred_username' claim
     *
     * @param preferredUserName the preferred user name
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder preferredUserName(String preferredUserName) {
        this.builder = this.builder.preferredUserName(preferredUserName);
        return this;
    }

    /**
     * Set an issuedAt 'iat' claim
     *
     * @param issuedAt the issuedAt time in seconds
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder issuedAt(long issuedAt) {
        this.builder = this.builder.issuedAt(issuedAt);
        return this;
    }

    /**
     * Set an expiry 'exp' claim
     *
     * @param expiresAt the absolute expiry time in seconds
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder expiresAt(long expiresAt) {
        this.builder = this.builder.expiresAt(expiresAt);
        return this;
    }

    /**
     * Set a relative expiry time.
     *
     * @param expiresIn the relative expiry time in seconds which will be added to the 'iat' (issued at) claim value
     *                  to calculate the value of the 'exp' (expires at) claim.
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder expiresIn(long expiresIn) {
        this.builder = this.builder.expiresIn(expiresIn);
        return this;
    }

    /**
     * Set a single value 'groups' claim
     *
     * @param group the groups
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder groups(String group) {
        this.builder = this.builder.groups(group);
        return this;
    }

    /**
     * Set a multiple value 'groups' claim
     *
     * @param groups the groups
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder groups(Set<String> groups) {
        this.builder = this.builder.groups(groups);
        return this;
    }

    /**
     * Set a single value audience 'aud' claim
     *
     * @param audience the audience
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder audience(String audience) {
        this.builder = this.builder.audience(audience);
        return this;
    }

    /**
     * Set a multiple value audience 'aud' claim
     *
     * @param audiences the audiences
     * @return JwtClaimsBuilder
     */
    @Override
    public JwtClaimsBuilder audience(Set<String> audiences) {
        this.builder = this.builder.audience(audiences);
        return this;
    }

    /**
     * Set a claim.
     * <p>
     * Simple claim value are converted to {@link String} unless it is an instance of {@link Boolean}, {@link Number} or
     * {@link Instant}. {@link Instant} values have their number of seconds from the epoch converted to long.
     * <p>
     * Array claims can be set as {@link Collection} or {@link JsonArray}, complex claims can be set as {@link Map} or
     * {@link JsonObject}. The members of the array claims can be complex claims.
     * <p>
     * Types of the claims directly supported by this builder are enforced.
     * The 'iss' (issuer), 'sub' (subject), 'upn', 'preferred_username' and 'jti' (token identifier) claims must be of
     * {@link String} type.
     * The 'aud' (audience) and 'groups' claims must be either of {@link String} or {@link Collection} of {@link String} type.
     * The 'iat' (issued at) and 'exp' (expires at) claims must be either of long or {@link Instant} type.
     *
     * @param name  the claim name
     * @param value the claim value
     * @return JwtClaimsBuilder
     * @throws IllegalArgumentException - if the type of the claim directly supported by this builder is wrong
     */
    @Override
    public JwtClaimsBuilder claim(String name, Object value) {
        this.builder = this.builder.claim(name,value);
        return this;
    }

    /**
     * Set JsonWebSignature headers and sign the claims by moving to {@link JwtSignatureBuilder}
     *
     * @return JwtSignatureBuilder
     */
    @Override
    public JwtSignatureBuilder jws() {
        return this.builder.jws();
    }

    /**
     * Set JsonWebEncryption headers and encrypt the claims by moving to {@link JwtEncryptionBuilder}
     *
     * @return JwtSignatureBuilder
     */
    @Override
    public JwtEncryptionBuilder jwe() {
        return this.builder.jwe();
    }

    /**
     * Sign the claims with {@link PrivateKey}.
     * <p>
     * 'RS256' algorithm will be used unless a different algorithm has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     * <p>
     * A key of size 2048 bits or larger MUST be used with the 'RS256' algorithm.
     *
     * @param signingKey the signing key
     * @return signed JWT token
     * @throws JwtSignatureException the exception if the signing operation has failed
     */
    @Override
    public String sign(PrivateKey signingKey) throws JwtSignatureException {
        return this.builder.sign(signingKey);
    }

    /**
     * Sign the claims with {@link SecretKey}
     * <p>
     * 'HS256' algorithm will be used unless a different algorithm has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     *
     * @param signingKey the signing key
     * @return signed JWT token
     * @throws JwtSignatureException the exception if the signing operation has failed
     */
    @Override
    public String sign(SecretKey signingKey) throws JwtSignatureException {
        return this.builder.sign(signingKey);
    }

    /**
     * Sign the claims with a private or secret key loaded from the custom location
     * which can point to a PEM, JWK or JWK set keys.
     * <p>
     * 'RS256' algorithm will be used unless a different algorithm has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     * A key of size 2048 bits or larger MUST be used with the 'RS256' algorithm.
     *
     * @param keyLocation the signing key location
     * @return signed JWT token
     * @throws JwtSignatureException the exception if the signing operation has failed
     */
    @Override
    public String sign(String keyLocation) throws JwtSignatureException {
        return this.builder.sign(keyLocation);
    }

    /**
     * Sign the claims with a key loaded from the location set with the "smallrye.jwt.sign.key.location" property
     * or the key content set with the "smallrye.jwt.sign.key" property. Keys in PEM, JWK and JWK formats are supported.
     * <p>
     * 'RS256' algorithm will be used unless a different algorithm has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     * A key of size 2048 bits or larger MUST be used with the 'RS256' algorithm.
     *
     * @return signed JWT token
     * @throws JwtSignatureException the exception if the signing operation has failed
     */
    @Override
    public String sign() throws JwtSignatureException {
        return this.builder.sign();
    }

    /**
     * Sign the claims with a secret key string.
     * <p>
     * 'HS256' algorithm will be used unless a different algorithm has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     *
     * @param secret the secret
     * @return signed JWT token
     * @throws JwtSignatureException the exception if the signing operation has failed
     */
    @Override
    public String signWithSecret(String secret) throws JwtSignatureException {
        return this.builder.signWithSecret(secret);
    }

    public String signAlgNone() throws JwtSignatureException {
        return this.builder.jws().algorithm(SignatureAlgorithm.fromAlgorithm(AlgorithmIdentifiers.NONE)).sign();
    }

    /**
     * Sign the claims with {@link PrivateKey} and encrypt the inner JWT by moving to {@link JwtEncryptionBuilder}.
     * 'RS256' algorithm will be used unless a different algorithm has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     * <p>
     * A key of size 2048 bits or larger MUST be used with the 'RS256' algorithm.
     *
     * @param signingKey the signing key
     * @return JwtEncryption
     * @throws JwtSignatureException the exception if the inner JWT signing operation has failed
     */
    @Override
    public JwtEncryptionBuilder innerSign(PrivateKey signingKey) throws JwtSignatureException {
        return this.builder.innerSign(signingKey);
    }

    /**
     * Sign the claims with {@link SecretKey} and encrypt the inner JWT by moving to {@link JwtEncryptionBuilder}.
     * <p>
     * 'HS256' algorithm will be used unless a different algorithm has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     *
     * @param signingKey the signing key
     * @return JwtEncryption
     * @throws JwtSignatureException the exception if the inner JWT signing operation has failed
     */
    @Override
    public JwtEncryptionBuilder innerSign(SecretKey signingKey) throws JwtSignatureException {
        return this.builder.innerSign(signingKey);
    }

    /**
     * Sign the claims with a private or secret key loaded from the custom location
     * which can point to a PEM, JWK or JWK set keys and encrypt the inner JWT by moving to {@link JwtEncryptionBuilder}.
     * <p>
     * 'RS256' algorithm will be used unless a different one has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     * A key of size 2048 bits or larger MUST be used with the 'RS256' algorithm.
     *
     * @param keyLocation the signing key location
     * @return JwtEncryption
     * @throws JwtSignatureException the exception if the inner JWT signing operation has failed
     */
    @Override
    public JwtEncryptionBuilder innerSign(String keyLocation) throws JwtSignatureException {
        return this.builder.innerSign(keyLocation);
    }

    /**
     * Sign the claims with a key loaded from the location set with the "smallrye.jwt.sign.key.location"
     * property or the key content set with the "smallrye.jwt.sign.key" property and encrypt the inner JWT
     * by moving to {@link JwtEncryptionBuilder}. Signing keys in PEM, JWK and JWK formats are supported.
     * <p>
     * A key of size 2048 bits or larger MUST be used with the 'RS256' algorithm or 'smallrye.jwt.new-token.signature-algorithm'
     * property.
     *
     * @return JwtEncryption
     * @throws JwtSignatureException the exception if the inner JWT signing operation has failed
     */
    @Override
    public JwtEncryptionBuilder innerSign() throws JwtSignatureException {
        return this.builder.innerSign();
    }

    /**
     * Sign the claims with a secret key string and encrypt the inner JWT by moving to {@link JwtEncryptionBuilder}.
     * 'HS256' algorithm will be used unless a different one has been set with {@code JwtSignatureBuilder} or
     * 'smallrye.jwt.new-token.signature-algorithm' property.
     *
     * @param secret the secret
     * @return signed JWT token
     * @throws JwtSignatureException the exception if the signing operation has failed
     */
    @Override
    public JwtEncryptionBuilder innerSignWithSecret(String secret) throws JwtSignatureException {
        return this.builder.innerSignWithSecret(secret);
    }
}
