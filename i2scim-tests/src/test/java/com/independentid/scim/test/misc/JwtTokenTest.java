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

package com.independentid.scim.test.misc;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.sub.ScimSubComponentTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.ErrorCodes;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
public class JwtTokenTest {
    @Inject
    TestUtils utils;

    JsonWebKey vkey;

    @Test
    public void aTest() {

        try {
            loadVerifyKey();
        } catch (JoseException | IOException e) {
            fail("Error loading verify key: " + e.getMessage(), e);
        }
        try {
            String bearer1 = utils.getAuthToken("sallyrider", false);

            String bearer2 = utils.getAuthToken("admin", true);
            System.out.println("#1: " + bearer1);
            validateAuth(bearer1);
            System.out.println("#2: " + bearer2);

            validateAuth(bearer2);

            System.out.println("Test pass!");
        } catch (MalformedClaimException e) {
            fail("Unexpected malformed claim exception: "+e.getMessage(),e);
        }

    }

    public void loadVerifyKey() throws IOException, JoseException {
        InputStream input = ConfigMgr.findClassLoaderResource(TestUtils.VALIDATE_KEY);
        JsonNode node = JsonUtil.getJsonTree(input);
        String jsonKey = node.toString();
        JsonWebKeySet set = new JsonWebKeySet(jsonKey);
        Iterator<JsonWebKey> iter = set.getJsonWebKeys().iterator();
        vkey = iter.next();

    }

    public void validateAuth(String authz) throws MalformedClaimException {
        if (!authz.startsWith("Bearer "))
            fail("Token provided did not start with 'Bearer '");
        String jwt = authz.substring(7);

        JwtConsumer jwtConsumer = new JwtConsumerBuilder()
                .setRequireExpirationTime() // the JWT must have an expiration time
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setExpectedIssuer(TestUtils.TEST_ISSUER) // whom the JWT needs to have been issued by
                .setExpectedAudience(TestUtils.TEST_AUDIENCE) // to whom the JWT is intended for
                .setVerificationKey(vkey.getKey()) // verify the signature with the public key
                .build(); // create the JwtConsumer instance

        try {
            //  Validate the JWT and process it to the Claims
            JwtClaims jwtClaims = jwtConsumer.processToClaims(jwt);
            System.out.println("JWT validation succeeded! " + jwtClaims);
        } catch (InvalidJwtException e) {
            // InvalidJwtException will be thrown, if the JWT failed processing or validation in any way.
            // Hopefully with meaningful explanations(s) about what went wrong.
            System.out.println("Invalid JWT! " + e);

            // Programmatic access to (some) specific reasons for JWT invalidity is also possible
            // should you want different error handling behavior for certain conditions.

            // JWT has expired being one common reason for invalidity
            if (e.hasExpired()) {
                fail("JWT expired at " + e.getJwtContext().getJwtClaims().getExpirationTime());
            }

            // Or maybe the audience was invalid
            if (e.hasErrorCode(ErrorCodes.AUDIENCE_INVALID)) {
                fail("JWT had wrong audience: " + e.getJwtContext().getJwtClaims().getAudience());
            }
        }

    }

}
