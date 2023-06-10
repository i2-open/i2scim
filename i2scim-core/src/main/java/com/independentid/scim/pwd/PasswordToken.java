/*
 * Copyright 2021.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.pwd;

import com.independentid.scim.resource.Meta;
import com.independentid.scim.resource.ScimResource;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Date;
import java.util.Map;

public class PasswordToken {
    private final static Logger logger = LoggerFactory.getLogger(PasswordToken.class);

    public final static String ALG_PBKDF2 = "PBKDF2WithHmacSHA1";

    public final static String PREFIX_TOKEN = "{i2sp}";

    static final java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
    static final java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();

    public static JWTParser parser = null;
    public static JWTAuthContextInfo jwtAuthContextInfo = null;
    public static int defaultIterations = 10000;
    public static String defaultHashAlg = ALG_PBKDF2;

    public static SecretKey encKey;

    static String tknIssuer;
    String sub = null;
    byte[] salt;
    String alg;
    byte[] hash;
    int iter;
    int failCnt;
    String iss;

    ScimResource resource;
    Date lastSuccess;
    SecretKeyFactory factory;

    public PasswordToken(String sub, String pwdVal) throws NoSuchAlgorithmException, java.text.ParseException, ParseException {
        this((ScimResource) null, pwdVal);
        this.sub = sub;
    }

    public PasswordToken(ScimResource parent, String pwdVal) throws NoSuchAlgorithmException, java.text.ParseException, ParseException {

        this.resource = parent;
        if (pwdVal.startsWith(PREFIX_TOKEN))
            parseJwt(pwdVal.substring(PREFIX_TOKEN.length()));
        else {
            genSalt();
            //this.iss = iss;
            //this.sub = "dummy";
            this.iter = defaultIterations;
            this.alg = defaultHashAlg;

            this.failCnt = 0;

            this.lastSuccess = new Date();
            factory = SecretKeyFactory.getInstance(alg);
            this.hash = performHash(pwdVal.toCharArray());
        }
    }

    private void parseJwt(String jwtVal) throws ParseException, java.text.ParseException {

        JsonWebEncryption jwe = new JsonWebEncryption();
        AlgorithmConstraints algConstraints = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, KeyManagementAlgorithmIdentifiers.DIRECT);
        jwe.setAlgorithmConstraints(algConstraints);
        AlgorithmConstraints encConstraints = new AlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
        jwe.setContentEncryptionAlgorithmConstraints(encConstraints);

        String payload;
        try {
            jwe.setCompactSerialization(jwtVal);
            jwe.setKey(encKey);
            payload = jwe.getPlaintextString();
        } catch (JoseException e) {
           throw new ParseException(e.getMessage(),e);
        }

        try {
            JwtClaims token = JwtClaims.parse(payload);
            //JsonWebToken token = parser.
            // JsonWebToken token = parser.parse(jwtVal,ctx);
            iss = token.getIssuer();
            sub = token.getSubject();
            Map<String,Object> claims = token.getClaimsMap();
            String val = (String) claims.get("salt");
            this.salt = decoder.decode(val);
            this.alg = (String) claims.get("alg");
            val = (String) claims.get("hash");
            this.hash = decoder.decode(val);
            val = (String) claims.get("iter");
            this.iter = Integer.parseInt(val);
            val = (String) claims.get("fails");
            this.failCnt = Integer.parseInt(val);
            val = (String) claims.get("lastMatch");
            this.lastSuccess = Meta.ScimDateFormat.parse(val);
            factory = SecretKeyFactory.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            logger.error("No such algorithm error for existing password value: " + e.getMessage(), e);
            throw new ParseException(e.getMessage(),e);
        } catch (MalformedClaimException e) {
            logger.error("Malformed claim error: " + e.getMessage(), e);
            throw new ParseException(e.getMessage(),e);
        } catch (InvalidJwtException e) {
            e.printStackTrace();
            throw new ParseException(e.getMessage(),e);
        }
    }


    /**
     * This method is provided to allow default alg and iterations to be changed over time. Changing the values will not
     * break exisitng password values that have already been processed. It simply changes what new hashes will have.
     * @param jwtParser   A handle to a JSON Web Token Parser {@link JWTParser} instance.
     * @param tokenSecret The secret used to encrypt password tokens (must be same on all nodes)
     * @param issuer      The node that encoded the password token (e.g. current node).
     * @param iter        The number of hashing iterations to use (e.g. 10000)
     * @param hashAlg     The hashing algorithm to use. See {@link SecretKeyFactory#generateSecret(KeySpec)}.
     */
    public static void init(JWTParser jwtParser, String tokenSecret, String issuer, int iter, String hashAlg) {
        logger.info("PasswordToken init called: " + issuer);
        parser = jwtParser;
        defaultIterations = iter;
        defaultHashAlg = hashAlg;

        encKey = new SecretKeySpec(tokenSecret.getBytes(),"AES"); //"AES"
        tknIssuer = issuer;
    }

    private void genSalt() {
        SecureRandom random = new SecureRandom();
        salt = new byte[16];
        random.nextBytes(salt);
    }

    public String getRawValue() {
        return PREFIX_TOKEN + getToken();
    }

    public String getToken() {

        JwtClaims claims = new JwtClaims();
        if (sub == null)
            if (resource == null)
                sub = "anonymous";
            else
                sub = resource.getId();  // this is done so that Id can be picked up late binding since the current resource may not yet have been saved.
        claims.setSubject(sub);
        claims.setIssuer(tknIssuer);
        claims.setClaim("salt", encoder.encodeToString(salt));
        claims.setClaim("alg", alg);
        claims.setClaim("fails", "" + failCnt);
        claims.setClaim("lastMatch", Meta.ScimDateFormat.format(lastSuccess));
        claims.setClaim("iter",""+iter);
        claims.setClaim("hash",encoder.encodeToString(hash));
        claims.setIssuedAtToNow();

        try {
            JsonWebEncryption jwe = new JsonWebEncryption();
            jwe.setPayload(claims.toJson());
            jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.DIRECT);
            jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_128_CBC_HMAC_SHA_256);
            jwe.setKey(encKey);
            return jwe.getCompactSerialization();

        } catch (Exception e) {
            System.err.println("Error encrypting password token: " + e.getMessage());
        }
        return null;
    }

    private byte[] performHash(char[] pwd) {

        //PasswordUtil.length = length;

        KeySpec spec = new PBEKeySpec(pwd, salt, iter, 128);
        byte[] hash = new byte[0];
        try {
            hash = factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return hash;
    }

    public byte[] getMatchHash() {
        return hash;
    }

    public boolean validatePassword(char[] pwd) throws NoSuchAlgorithmException {
        byte[] matchHash = performHash(pwd);
        if (matchHash.length != hash.length)
            return false;
        for (int i = 0; i < hash.length; i++)
            if (hash[i] != matchHash[i])
                return false;

        return true;
    }

    public byte[] getSalt() {
        return salt;
    }
}
