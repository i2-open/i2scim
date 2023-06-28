package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.signals.StreamHandler;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.Key;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(SignalsStreamTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SignalsHandlerTest {
    private final static Logger logger = LoggerFactory.getLogger(SignalsHandlerTest.class);
    private static final String testUserFile1 = "classpath:/data/TestUser-bjensen.json";
    private static final String testUserFile2 = "classpath:/data/TestUser-jsmith.json";

    @TestHTTPEndpoint(MockSignalsServer.class)
    @TestHTTPResource()
    URL url;

    static String eventPath;
    static String pollPath;

    @Inject
    TestUtils utils;

    @Inject
    StreamHandler streamHandler;

    @Inject
    ConfigMgr configMgr;

    @Test
    public void a_InitTest() {
        logger.info("Resetting provider");
        utils.resetMemDirectory();

        eventPath = url + "/events";
        pollPath = url + "/poll";

        Operation.initialize(configMgr);

    }

    /*
    b_MockPushEvent tests that the Mock Signals Server can receive events using RFC8935. Note: authentication
    is not tested.
     */
    @Test
    public void b_TestMockPushEvent() throws IOException {
        SecurityEventToken token = new SecurityEventToken();

        token.setJti("123457890");
        token.setTxn("1234");
        token.setAud("example.com");
        token.setIssuer(streamHandler.getPushStream().iss);

        String tokenJsonString = token.toPrettyString();
        logger.info("Token: \n" + tokenJsonString);

        String signed = null;
        try {
            signed = token.JWS(streamHandler.getPushStream().issuerKey);
            logger.info("Signed token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed: " + e.getMessage());
        }
        assertThat(signed).isNotNull();

        StringEntity bodyEntity = new StringEntity(signed, ContentType.create("application/secevent+jwt"));
        CloseableHttpClient client = HttpClients.createDefault();

        HttpPost req = new HttpPost(eventPath);
        req.setEntity(bodyEntity);

        try {
            CloseableHttpResponse resp = client.execute(req);

            assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
            resp.close();
            assertThat(MockSignalsServer.getReceived()).isEqualTo(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            signed = token.JWS(null);
            logger.info("Unsigned token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed: " + e.getMessage());
        }

        bodyEntity = new StringEntity(signed, ContentType.create("application/secevent+jwt"));
        req = new HttpPost(eventPath);
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
    c_MockPollEvent tests the Mock server can be polled for event (RFC8936). Authentication is not tested.
     */
    @Test
    public void c_TestMockPollEvent() {
        CloseableHttpClient client = HttpClients.createDefault();

        assertThat(MockSignalsServer.getPendingPollCnt()).isEqualTo(1);

        Key issuerPublicKey = streamHandler.getPollStream().issuerKey;
        assertThat(issuerPublicKey).isNotNull();

        ObjectNode reqNode = JsonUtil.getMapper().createObjectNode();
        reqNode.put("maxEvents", 5);
        reqNode.put("returnImmediately", true);

        try {
            StringEntity body = new StringEntity(reqNode.toPrettyString(), ContentType.APPLICATION_JSON);
            HttpPost pollRequest = new HttpPost(pollPath);
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
            pollRequest = new HttpPost(pollPath);
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

        } catch (UnsupportedEncodingException ignored) {
        } catch (IOException e) {
            fail(e.getMessage());
        }

    }

    /*
    d_TestStreamConfigPushPullEvents Tests the StreamHandler class to confirm it works
     */
    @Test
    public void d_TestStreamHandlerPushPullEvents() throws ScimException, IOException {
        List<SecurityEventToken> events = generateEvents();

        boolean success = streamHandler.getPushStream().pushEvent(events.get(0));
        assertThat(success).as("Event was sent").isTrue();
        success = streamHandler.getPushStream().pushEvent(events.get(1));
        assertThat(success).as("Event was sent").isTrue();

        assertThat(MockSignalsServer.getReceived()).isEqualTo(3);

        // The two events should be ready for polling....
        assertThat(MockSignalsServer.getPendingPollCnt()).isEqualTo(2);

        Map<String, SecurityEventToken> eventMap = streamHandler.getPollStream().pollEvents(new ArrayList<String>(), false);
        assertThat(eventMap).isNotNull();
        assertThat(eventMap.size()).as("Two events received by poll").isEqualTo(2);

        Set<String> ackJtis = eventMap.keySet();
        List<String> acks = new ArrayList<>(ackJtis);
        Map<String, SecurityEventToken> eventMap2 = streamHandler.getPollStream().pollEvents(acks, false);
        assertThat(eventMap2).isNotNull();
        assertThat(eventMap2.size()).isEqualTo(0);
        assertThat(MockSignalsServer.getPendingPollCnt()).isEqualTo(0);
        assertThat(MockSignalsServer.getAckedCnt()).isEqualTo(3);
    }

    protected List<SecurityEventToken> generateEvents() throws IOException, ScimException {
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
        event.AddEventPayload("urn:ietf:params:event:SCIM:prov:create", payload);
        event.setAud("example.com");
        event.setIssuer("myissuer.io");
        return event;
    }

}
