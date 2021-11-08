/*
 * Copyright (c) 2021.  Independent Identity Incorporated
 * <P/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <P/>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <P/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.test.misc;

import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Scanner;

public class CycleTestKeys {

    static final String PUB_KEY = "./i2scim-tests/src/test/resources/certs/jwks-certs.json";
    static final String PRIV_KEY = "./i2scim-tests/src/test/resources/data/jwk-sign.json";

    public static void main(String[] args) throws IOException {

        System.out.println("This utility will generate a new JWT signing key for unit testing purposes.");

        System.out.println("Existing keys in the source resource tree will be overwritten!");
        System.out.print("Enter \"Y\" to continue: ");
        Scanner scanner = new Scanner(System.in);
        String response = scanner.nextLine();
        scanner.close();
        if (response.toLowerCase().startsWith("y"))
            initKeyPair();
    }

    private static void initKeyPair() {
        try {

            System.out.println("\nGenerating RSA key pair (this may take a while)...");

            KeyPairGenerator keygen = KeyPairGenerator.getInstance("RSA");
            KeyPair keypair = keygen.generateKeyPair();
            PrivateKey signKey = keypair.getPrivate();

            System.out.println("\nConverting to JWK...");

            RsaJsonWebKey rsaJwk = (RsaJsonWebKey) PublicJsonWebKey.Factory.newPublicJwk(keypair.getPublic());
            rsaJwk.setPrivateKey(signKey);
            rsaJwk.setUse("Key used for i2scim testing purposes ONLY!");
            rsaJwk.setKeyId("i2scim");
            JsonWebKeySet set = new JsonWebKeySet();
            set.addJsonWebKey(rsaJwk);

            // Write the public key so the server can validate certs
            String jsonString = set.toJson();
            File pubFile = new File(PUB_KEY);
            FileWriter writer = new FileWriter(pubFile);
            writer.write(jsonString);
            writer.close();
            System.out.println("...wrote JWKset to resources/certs folder");

            // Write out the signing key
            File privateFile = new File(PRIV_KEY);
            jsonString = rsaJwk.toJson(JsonWebKey.OutputControlLevel.INCLUDE_PRIVATE);
            writer = new FileWriter(privateFile);
            writer.write(jsonString);
            writer.close();
            System.out.println("...wrote JWK signing key to resources/data");

            System.out.println("\nKey generation complete!");

        } catch (NoSuchAlgorithmException | JoseException | IOException e) {
            e.printStackTrace();
        }
    }
}
