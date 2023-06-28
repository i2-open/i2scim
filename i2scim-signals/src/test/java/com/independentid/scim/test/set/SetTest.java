package com.independentid.scim.test.set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.set.SubjectIdentifier;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.keys.RsaKeyUtil;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(SignalsSetTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SetTest {
    private final static Logger logger = LoggerFactory.getLogger(SetTest.class);

    static Key issPub, issSign, audPub, audSign;

    static {
        try {
            RsaKeyUtil util = new RsaKeyUtil();

            KeyPair key = util.generateKeyPair(2048);
            issSign = key.getPrivate();
            issPub = key.getPublic();


            KeyPair audKey = util.generateKeyPair(2048);
            audSign = audKey.getPrivate();
            audPub = audKey.getPublic();

        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void a_CreateSetTests() {
        SecurityEventToken token = new SecurityEventToken();

        token.setAud("abc");
        token.setIssuer("DEFAULT");

        token.SetScimSubjectIdSimple("2937507", "sride");
        String tokenJsonString = token.toJsonString();

        logger.info("Token: \n" + tokenJsonString);

        assertThat(tokenJsonString).isEqualTo("{\"aud\":[\"abc\"],\"iss\":\"DEFAULT\",\"sub_id\":{\"format\":\"scim\",\"id\":\"2937507\",\"externalId\":\"sride\"}}");

        ObjectMapper mapper = JsonUtil.getMapper();
        ObjectNode eventObj = mapper.createObjectNode();
        eventObj.put("a", "abc");
        eventObj.put("username", "sallyride");


        token.AddEventPayload("uri:testevent", eventObj);

        String eventString = token.toPrettyString();

        try {
            token = new SecurityEventToken(eventString);

            assertThat(token.getIssuer())
                    .as("Get Issuer matches")
                    .isEqualTo("DEFAULT");

            SubjectIdentifier subjectIdentifier = token.getSubjectIdentifier();
            assertThat(subjectIdentifier.id).isEqualTo("2937507");

            JsonNode event = token.GetEvents();
            JsonNode eventTest = token.GetEvent("uri:testevent");
            String username = eventTest.get("username").asText();
            assertThat(username)
                    .as("Event payload parsed")
                    .isEqualTo("sallyride");
            logger.info("Event info received\n" + event.toPrettyString());
        } catch (InvalidJwtException e) {
            fail("Token was not parsed");
        } catch (MalformedClaimException | JsonProcessingException e) {
            fail("Received a malformed claim: " + e.getMessage());
        }

    }

    @Test
    public void b_UnsignedSignatureTests() {
        SecurityEventToken token = new SecurityEventToken();

        token.setJti("123457890");
        token.setTxn("1234");
        token.setAud("abc");
        token.setIssuer("DEFAULT");

        String tokenJsonString = token.toPrettyString();
        logger.info("Token: \n" + tokenJsonString);

        String signed = null;
        try {
            signed = token.JWS(null);
            logger.info("Signed token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed");
        }
        assertThat(signed).isNotNull();

        try {
            SecurityEventToken newToken = new SecurityEventToken(signed, null, null);
            assertThat(newToken.getIssuer())
                    .as("Issuer after parsing signed is DEFAULT")
                    .isEqualTo("DEFAULT");
        } catch (InvalidJwtException | JoseException | JsonProcessingException e) {
            fail("Error parsing signed token: " + e.getMessage());
        }
    }

    @Test
    public void c_SignedSignatureTest() {
        SecurityEventToken token = new SecurityEventToken();

        token.setJti("123457890");
        token.setTxn("1234");
        token.setAud("abc");
        token.setIssuer("DEFAULT");

        String tokenJsonString = token.toPrettyString();
        logger.info("Token: \n" + tokenJsonString);

        String signed = null;
        try {
            signed = token.JWS(issSign);
            logger.info("Signed token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed: " + e.getMessage());
        }
        assertThat(signed).isNotNull();

        try {
            SecurityEventToken newToken = new SecurityEventToken(signed, issPub, null);
            assertThat(newToken.getIssuer())
                    .as("Issuer after parsing signed is DEFAULT")
                    .isEqualTo("DEFAULT");
        } catch (InvalidJwtException | JoseException | JsonProcessingException e) {
            fail("Error parsing signed token: " + e.getMessage());
        }
    }

    @Test
    public void d_EncryptedTest() {
        SecurityEventToken token = new SecurityEventToken();

        token.setJti("123457890");
        token.setTxn("1234");
        token.setAud("abc");
        token.setIssuer("DEFAULT");

        String tokenJsonString = token.toPrettyString();
        logger.info("Token: \n" + tokenJsonString);

        String encrypted = null;
        try {

            encrypted = token.JWE("DEFAULT", issSign, audPub);
            logger.info("Encrypted token:\n" + encrypted);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not encrypted: " + e.getMessage());
        }
        assertThat(encrypted).isNotNull();

        try {
            SecurityEventToken newToken = new SecurityEventToken(encrypted, issPub, audSign);
            assertThat(newToken.getIssuer())
                    .as("Issuer after parsing token is DEFAULT (and was parsable)")
                    .isEqualTo("DEFAULT");
        } catch (InvalidJwtException | JoseException | JsonProcessingException e) {
            fail("Error parsing signed token: " + e.getMessage());
        }
    }


}
