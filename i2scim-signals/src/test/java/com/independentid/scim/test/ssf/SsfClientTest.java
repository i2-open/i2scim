package com.independentid.scim.test.ssf;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.auth.SsfAuthorizationToken;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.op.Operation;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.events.MockSignalsServer;
import com.independentid.set.SecurityEventToken;
import com.independentid.signals.SsfHandler;
import com.independentid.signals.StreamConfigProps;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.independentid.signals.StreamConfigProps.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(SignalsSsfTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SsfClientTest {
    private final static Logger logger = LoggerFactory.getLogger(SsfClientTest.class);

    private static final String testUserFile1 = "classpath:/data/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/data/TestUser-jsmith.json";

    @TestHTTPEndpoint(MockSignalsServer.class)
    @TestHTTPResource()
    URL ssfUrl;

    String iatPath;
    String regPath;

    @Inject
    StreamConfigProps configProps;

    @Inject
    ConfigMgr configMgr;

    CloseableHttpClient client = HttpClients.createDefault();
    // The following is a copy of StreamHandler

    Key issuerKey;
    PublicKey publicKey;
    protected SsfAuthorizationToken.AuthIssuer authIssuer;

    static SsfHandler ssfClient;

    @Test
    public void a_CheckMockServer() {
        resetConfigFile();

        Operation.initialize(configMgr);
        issuerKey = configProps.getIssuerPrivateKey();
        publicKey = configProps.getIssuerPublicKey();
        this.authIssuer = new SsfAuthorizationToken.AuthIssuer(configProps.pubIssuer, issuerKey, publicKey);

        String iat = null;
        try {
            iat = authIssuer.IssueProjectIat(null);
        } catch (MalformedClaimException | JoseException e) {
            fail("Unexpected error generating iat: " + e, e);
        }
        SsfAuthorizationToken token = null;
        try {
            token = new SsfAuthorizationToken(iat, this.publicKey, null);
        } catch (InvalidJwtException | JoseException | JsonProcessingException e) {
            fail("Unexpected error validating iat: " + e, e);
        }
        assertThat(token).isNotNull();
    }

    /*
    SsFManualTest tests that the SsfHandler can start using basic properties only.
     */
    @Test
    public void b_SsfManualTest() {
        logger.info("Starting *manual* configuration test...");
        resetConfigFile();
        assertThat(configProps.getMode()).isEqualTo(Mode_Manual);
        assertThat(configProps.getConfigFile().exists()).isFalse();
        String checksum;
        try {
            ssfClient = SsfHandler.Open(configProps);
            assertThat(ssfClient.validateConfiguration()).isTrue();
            File configFile = configProps.getConfigFile();
            assertThat(configFile.exists()).isTrue();
            byte[] data = Files.readAllBytes(configFile.toPath());
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            checksum = new BigInteger(1, hash).toString(16);
            long lastModDate = configFile.lastModified();
            logger.info(("Config checksum is: " + checksum));

            // If we load a second time, it should be from the configuration file.
            SsfHandler client2 = SsfHandler.Open(configProps);
            assertThat(client2.validateConfiguration()).as("Check configuration is valid").isTrue();
            File configFile2 = configProps.getConfigFile();
            assertThat(lastModDate).as("Check file not modified").isEqualTo(configFile2.lastModified());

        } catch (IOException e) {
            fail("Failed to start SSF Manual: " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            fail("Unexpected error: " + e.getMessage(), e);
        }
        logger.info("Manual configuration test complete.");
    }

    /*
    SsfPreConfigTest uses json files with stream configs to initialize
     */
    @Test
    public void c_SsfPreConfigTest() {
        logger.info("Starting *Stream File* configuration test...");
        resetConfigFile();

        StreamConfigProps props2 = configProps;

        props2.pubConfigFile = "/data/pubStream.json";
        props2.rcvConfigFile = "/data/receiveStream.json";

        assertThat(props2.getMode()).isEqualTo(Mode_PreConfig);

        try {
            SsfHandler client = SsfHandler.Open(props2);
            boolean valid = client.validateConfiguration();
            assertThat(valid).isTrue();

        } catch (IOException e) {
            fail("Failed to start SSF Manual: " + e.getMessage(), e);
        }
        logger.info("Pre-config test complete.");

    }

    /*
    SsfAutoTest tests the SsfHandler to ensure it can auto register and autoconfigure
     */
    @Test
    public void d_SsfAutoTest() throws IOException {
        logger.info("Starting *SSF auto* configuration test...");
        resetConfigFile();

        this.iatPath = ssfUrl + SsfHandler.SSF_IAT;
        this.regPath = ssfUrl + SsfHandler.SSF_REG;
        configProps.ssfUrl = ssfUrl.toString();

        assertThat(configProps.getMode()).isEqualTo(Mode_SsfAuto);

        HttpGet req = new HttpGet(this.iatPath);
        CloseableHttpResponse resp = client.execute(req);
        assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(200);

        HttpEntity entity = resp.getEntity();

        JsonNode rootNode = JsonUtil.getJsonTree(entity.getContent());
        assertThat(resp).isNotNull();
        JsonNode tokenNode = rootNode.get("token");
        resp.close();

        configProps.ssfUrl = ssfUrl.toString();
        configProps.ssfAuthorization = "Bearer " + tokenNode.asText();

        ssfClient = SsfHandler.Open(configProps);
        assertThat(ssfClient).isNotNull();
        assertThat(ssfClient.ssfConfig).isNotNull();

        // Check that a client token was obtained
        assertThat(ssfClient.validateConfiguration()).isTrue();

        assertThat(ssfClient.getPushStream()).isNotNull();
        assertThat(ssfClient.getPushStream().enabled).isTrue();

        assertThat(ssfClient.getPollStream()).isNotNull();
        assertThat(ssfClient.getPollStream().enabled).isTrue();

        logger.info("Opening 2nd copy which should be read file");
        SsfHandler clientCopy = SsfHandler.Open(configProps);

        assertThat(clientCopy).isNotNull();
        assertThat(clientCopy.validateConfiguration()).isTrue();

        assertThat(ssfClient.clientToken).isEqualTo(clientCopy.clientToken);
    }

    /*
      c_MockPushEvent tests that the Mock Signals Server can receive events using RFC8935. Note: authentication
      is not tested.
       */
    @Test
    public void e_TestMockPushEvent() throws IOException {
        SecurityEventToken token = new SecurityEventToken();

        token.setJti("123457890");
        token.setTxn("1234");
        token.setAud("example.com");
        token.setIssuer(ssfClient.getPushStream().iss);

        String tokenJsonString = token.toPrettyString();
        logger.info("Token: \n" + tokenJsonString);

        String signed = null;
        try {
            signed = token.JWS(ssfClient.getPushStream().issuerKey);
            logger.info("Signed token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed: " + e.getMessage());
        }
        assertThat(signed).isNotNull();

        StringEntity bodyEntity = new StringEntity(signed, ContentType.create("application/secevent+jwt"));
        CloseableHttpClient client = HttpClients.createDefault();

        HttpPost req = new HttpPost(ssfClient.getPushStream().endpointUrl);
        req.setHeader("Authorization", ssfClient.getPushStream().authorization);
        req.setEntity(bodyEntity);

        try {
            CloseableHttpResponse resp = client.execute(req);

            assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
            resp.close();
            assertThat(MockSignalsServer.getReceived()).isEqualTo(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // unsigned test
        String tokenString = "";
        try {
            tokenString = token.JWS(null);
            logger.info("Unsigned token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed: " + e.getMessage());
        }

        bodyEntity = new StringEntity(tokenString, ContentType.create("application/secevent+jwt"));
        req = new HttpPost(ssfClient.getPushStream().endpointUrl);
        req.setHeader("Authorization", ssfClient.getPushStream().authorization);
        req.setEntity(bodyEntity);

        try {
            CloseableHttpResponse resp = client.execute(req);
            assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
            assertThat(MockSignalsServer.getJwtErrs()).isEqualTo(1);
            assertThat(MockSignalsServer.getTotalReceivedEvents()).isEqualTo(2);
            resp.close();
        } catch (IOException e) {
            fail(e.getMessage());
        }
        client.close();
    }


    /*
    d_MockPollEvent tests the Mock server can be polled for event (RFC8936). Authentication is not tested.
     */
    @Test
    public void f_TestMockPollEvent() {
        CloseableHttpClient client = HttpClients.createDefault();

        assertThat(MockSignalsServer.getPendingPollCnt()).isEqualTo(1);

        PublicKey issuerPublicKey = ssfClient.getPollStream().issuerKey;
        assertThat(issuerPublicKey).isNotNull();

        ObjectNode reqNode = JsonUtil.getMapper().createObjectNode();
        reqNode.put("maxEvents", 5);
        reqNode.put("returnImmediately", true);

        try {
            StringEntity body = new StringEntity(reqNode.toPrettyString(), ContentType.APPLICATION_JSON);
            HttpPost pollRequest = new HttpPost(ssfClient.getPollStream().endpointUrl);
            pollRequest.setHeader("Authorization", ssfClient.getPollStream().authorization);
            pollRequest.setEntity(body);

            Instant start = Instant.now();
            CloseableHttpResponse resp = client.execute(pollRequest);
            Instant finish = Instant.now();
            long msecs = finish.toEpochMilli() - start.toEpochMilli();
            assertThat(msecs).as("Check return immediately").isLessThan(500);

            HttpEntity respEntity = resp.getEntity();
            byte[] respBytes = respEntity.getContent().readAllBytes();
            JsonNode respNode = JsonUtil.getJsonTree(respBytes);
            JsonNode setNode = respNode.get("sets");
            assertThat(setNode).isNotNull();
            assertThat(setNode.isObject()).isTrue();
            assertThat(setNode.size()).as("Should be no sets").isEqualTo(1);
            JsonNode moreNode = respNode.get("moreAvailable");
            assertThat(moreNode).as("No more events available - moreAvailable node null").isNull();
            // The event should still be pending until acknowledged.
            assertThat(MockSignalsServer.getPendingPollCnt()).as("Should still be a pending unacknowledged event").isEqualTo(1);

            ArrayNode acks = reqNode.putArray("ack");
            for (JsonNode item : setNode) {
                String value = item.textValue();
                try {
                    SecurityEventToken token = new SecurityEventToken(value, issuerPublicKey, null);
                    acks.add(token.getJti());
                    logger.info("Token received: \n" + token.toPrettyString());
                } catch (InvalidJwtException | JoseException e) {
                    fail("Failed to validate received token: " + e.getMessage());
                }
            }

            logger.info("Acknowledging event");

            body = new StringEntity(reqNode.toPrettyString(), ContentType.APPLICATION_JSON);
            pollRequest = new HttpPost(ssfClient.getPollStream().endpointUrl);
            pollRequest.setHeader("Authorization", ssfClient.getPollStream().authorization);
            pollRequest.setEntity(body);
            resp = client.execute(pollRequest);
            assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(200);
            assertThat(MockSignalsServer.getPendingPollCnt()).isEqualTo(0);

            respEntity = resp.getEntity();
            respBytes = respEntity.getContent().readAllBytes();
            respNode = JsonUtil.getJsonTree(respBytes);
            setNode = respNode.get("sets");
            assertThat(setNode.size()).as("Should be no new sets").isEqualTo(0);
            logger.info("All events acked and received");

            client.close();
        } catch (UnsupportedEncodingException ignored) {
        } catch (IOException e) {
            fail(e.getMessage());
        }

    }

    /*
    e_TestStreamConfigPushPullEvents Tests the StreamHandler class to confirm it works
     */
    @Test
    public void g_TestStreamHandlerPushPullEvents() throws IOException {
        List<SecurityEventToken> events = generateEvents();

        boolean success = ssfClient.getPushStream().pushEvent(events.get(0));
        assertThat(success).as("Event was sent").isTrue();
        success = ssfClient.getPushStream().pushEvent(events.get(1));
        assertThat(success).as("Event was sent").isTrue();

        assertThat(MockSignalsServer.getReceived()).isEqualTo(3);

        // The two events should be ready for polling....
        assertThat(MockSignalsServer.getPendingPollCnt()).isEqualTo(2);

        Map<String, SecurityEventToken> eventMap = ssfClient.getPollStream().pollEvents(new ArrayList<>(), false);
        assertThat(eventMap).isNotNull();
        assertThat(eventMap.size()).as("Two events received by poll").isEqualTo(2);

        Set<String> ackJtis = eventMap.keySet();
        List<String> acks = new ArrayList<>(ackJtis);
        Map<String, SecurityEventToken> eventMap2 = ssfClient.getPollStream().pollEvents(acks, false);
        assertThat(eventMap2).isNotNull();
        assertThat(eventMap2.size()).isEqualTo(0);
        assertThat(MockSignalsServer.getPendingPollCnt()).isEqualTo(0);
        assertThat(MockSignalsServer.getAckedCnt()).isEqualTo(3);
    }

    protected List<SecurityEventToken> generateEvents() throws IOException {
        List<SecurityEventToken> events = new ArrayList<>();

        InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
        assertThat(userStream).isNotNull();
        JsonNode node1 = JsonUtil.getJsonTree(userStream);

        SecurityEventToken token = mapEvent(node1, "001");
        events.add(token);

        userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
        assertThat(userStream).isNotNull();

        JsonNode node2 = JsonUtil.getJsonTree(userStream);

        SecurityEventToken token2 = mapEvent(node2, "002");
        events.add(token2);

        return events;
    }

    private SecurityEventToken mapEvent(JsonNode resourceNode, String txn) {
        SecurityEventToken event = new SecurityEventToken();
        event.setTxn(txn);
        event.setJti(txn);
        event.setToe(NumericDate.now());

        ObjectNode payload = JsonUtil.getMapper().createObjectNode();
        payload.set("data", resourceNode);
        event.AddEventPayload("urn:ietf:params:SCIM:event:prov:create:full", payload);
        event.setAud("example.com");
        event.setIssuer("myissuer.io");
        return event;
    }

    private void resetConfigFile() {
        File configFile = configProps.getConfigFile();
        if (configFile.exists()) {
            logger.info("Resetting configuraiton file: " + configFile);
            configFile.delete();
        }
    }
}
