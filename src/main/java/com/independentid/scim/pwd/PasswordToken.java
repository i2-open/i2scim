/*
 * Copyright (c) 2021.
 *
 * Confidential and Proprietary
 *
 * This unpublished source code may not be distributed outside
 * “Independent Identity Org”. without express written permission of
 * Phillip Hunt.
 *
 * People at companies that have signed necessary non-disclosure
 * agreements may only distribute to others in the company that are
 * bound by the same confidentiality agreement and distribution is
 * subject to the terms of such agreement.
 */

package com.independentid.scim.pwd;

import com.independentid.scim.resource.Meta;
import com.independentid.scim.resource.ScimResource;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
import io.smallrye.jwt.build.JwtEncryptionBuilder;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Date;

public class PasswordToken {
    private final static Logger logger = LoggerFactory.getLogger(PasswordToken.class);

    public final static String ALG_PBKDF2 = "PBKDF2WithHmacSHA1";
    public final static String PREFIX_TOKEN = "{i2sp}";

    static final java.util.Base64.Encoder encoder = java.util.Base64.getEncoder();
    static final java.util.Base64.Decoder decoder = java.util.Base64.getDecoder();

    public static JWTParser parser = null;
    public static int defaultIterations = 10000;
    public static String defaultAlg = ALG_PBKDF2;
    public static String tknKey = null;

    static String iss;
    String sub = null;
    byte[] salt;
    String alg;
    byte[] hash;
    int iter;
    int failCnt;

    ScimResource resource;
    Date lastSuccess;
    SecretKeyFactory factory;

    public PasswordToken(String sub, String pwdVal) throws NoSuchAlgorithmException, java.text.ParseException, ParseException {
      this((ScimResource) null,pwdVal);
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
            this.alg = defaultAlg;

            this.failCnt = 0;

            this.lastSuccess = new Date();
            factory = SecretKeyFactory.getInstance(alg);
            this.hash = performHash(pwdVal.toCharArray());
        }
    }

    private void parseJwt(String jwtVal) throws java.text.ParseException, ParseException {

        JsonWebToken token = parser.decrypt(jwtVal,tknKey);
        iss = token.getIssuer();
        sub = token.getSubject();
        String val = token.getClaim("salt");
        this.salt = decoder.decode(val);
        this.alg = token.getClaim("alg");
        val = token.getClaim("hash");
        this.hash = decoder.decode(val);
        val = token.getClaim("iter");
        this.iter = Integer.parseInt(val);
        val = token.getClaim("fails");
        this.failCnt = Integer.parseInt(val);
        val = token.getClaim("lastMatch");

        this.lastSuccess = Meta.ScimDateFormat.parse(val);

        try {
            factory = SecretKeyFactory.getInstance(alg);
        } catch (NoSuchAlgorithmException e) {
            logger.error("No such algorithm error for existing password value: "+e.getMessage(),e);
        }
    }


    /**
     * This method is provided to allow default alg and iterations to be changed over time. Changing the values will
     * not break exisitng password values that have already been processed. It simply changes what new hashes will have.
     * @param tokenSecret The secret used to encrypt password tokens (must be same on all nodes)
     * @param iter The number of hashing iterations to use (e.g. 10000)
     * @param alg The hashing algorithm to use. See {@link SecretKeyFactory#generateSecret(KeySpec)}.
     */
    public static void init(JWTParser jwtParser, String tokenSecret, String issuer, int iter,String alg) {
        logger.info("PasswordToken init called: "+issuer);
        parser = jwtParser;
        defaultIterations = iter;
        defaultAlg = alg;
        tknKey = tokenSecret;
        iss = issuer;
    }

    private void genSalt() {
        SecureRandom random = new SecureRandom();
        salt = new byte[16];
        random.nextBytes(salt);
    }

    public String getRawValue() {
        return PREFIX_TOKEN+getJwt();
    }

    public String getJwt()  {

        JwtClaimsBuilder builder = Jwt.claims();

        if (sub == null)
            sub = resource.getId();  // this is done so that Id can be picked up late binding since the current resource may not yet have been saved.

        builder.issuer(iss).subject(sub);
        builder
                .claim("salt",encoder.encodeToString(salt))
                .claim("alg",alg)
                .claim("fails",""+failCnt)
                .claim("lastMatch", Meta.ScimDateFormat.format(lastSuccess))
                .claim("iter",""+iter)

                .claim("hash",encoder.encodeToString(hash));
        try {
            JwtEncryptionBuilder ebuilder = builder.jwe();
            return ebuilder.encryptWithSecret(tknKey);
        } catch (Exception e) {
            System.err.println("Error encrypting password token: "+e.getMessage());
        }
        return null;
    }

    private byte[] performHash(char[] pwd) {

        //PasswordUtil.length = length;

        KeySpec spec = new PBEKeySpec(pwd,salt, iter,128);
        byte[] hash = new byte[0];
        try {
            hash = factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return hash;
    }

    public byte[] getMatchHash() { return hash; }

    public boolean validatePassword(char[] pwd) throws NoSuchAlgorithmException {
        byte[] matchHash = performHash(pwd);
        if (matchHash.length != hash.length)
            return false;
        for(int i=0; i < hash.length; i++)
            if (hash[i] != matchHash[i])
                return false;

        return true;
    }

    public byte[] getSalt() {
        return salt;
    }
}
