package com.independentid.scim.test.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.signals.StreamHandler;
import io.quarkus.test.Mock;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mock
@Singleton
@Path("/signals")
//@JsonIgnoreProperties(ignoreUnknown = true)
public class MockSignalsServer {
    private final static Logger logger = LoggerFactory.getLogger(MockSignalsServer.class);

    @Inject
    StreamHandler streamHandler;

    static int received = 0;
    static int jwtErrs = 0;
    static int miscErrs = 0;
    static Map<String, SecurityEventToken> pollEvents = new HashMap<>();
    static int ackedCnt = 0;
    static int sent = 0;

    /*
    receive processes the event stream, validates the event and then stores it localling in pollEvents for a
    subsequent poll which then receives the event back.
     */
    @POST
    @Path("/events")
    @Consumes("application/secevent+jwt")
    @Produces(MediaType.APPLICATION_JSON)
    public Response receive(String eventString) {

        try {
            // For this test, the polling config has the correct keys to validate a pushed event
            SecurityEventToken token = new SecurityEventToken(eventString, streamHandler.getPollStream().issuerKey, streamHandler.getPollStream().receiverKey);
            logger.info("Received event:\n" + token.toPrettyString());
            pollEvents.put(token.getJti(), token);
            received++;
            return Response.accepted().build();
        } catch (InvalidJwtException | JoseException e) {
            String msg = "{\n \"err\": \"invalid_key\",\n \"description\": \"" + e.getMessage() + "\"\n}";
            jwtErrs++;
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(msg)
                    .build();
        } catch (JsonProcessingException e) {
            miscErrs++;
            logger.error("Error validating token: " + e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/poll")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response pollRequest(String request) {
        try {
            JsonNode pollJson = JsonUtil.getJsonTree(request);
            JsonNode ackNode = pollJson.get("ack");
            boolean hadAcks = false;
            if (ackNode != null && ackNode.isArray()) {
                ArrayNode array = (ArrayNode) ackNode;
                for (JsonNode node : array) {
                    String jti = node.asText();
                    // TODO should we check that the JTI is still unacked?
                    pollEvents.remove(jti);
                    ackedCnt++;
                    hadAcks = true;
                }
            }
            JsonNode maxEvents = pollJson.get("maxEvents");
            int maxItems = 1000;
            if (maxEvents != null)
                maxItems = maxEvents.asInt();
            JsonNode returnImmediately = pollJson.get("returnImmediately");
            boolean doWait = true;
            if (returnImmediately != null)
                doWait = !returnImmediately.asBoolean();
            if (hadAcks) doWait = false; // we don't want to wait if there were acks.
            int waitCnt = 0;
            while (pollEvents.size() == 0 && doWait && waitCnt < 5) {
                waitCnt++;
                logger.info("Waiting for events...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
            }
            if (pollEvents.size() == 0) {
                String body = "{\"sets\":{}}";
                return Response.ok(body, MediaType.TEXT_PLAIN_TYPE).build();
            }
            ObjectNode respNode = JsonUtil.getMapper().createObjectNode();
            ObjectNode setsNode = JsonUtil.getMapper().createObjectNode();

            int i = 0;
            for (String jti : pollEvents.keySet()) {
                SecurityEventToken token = pollEvents.get(jti);
                try {
                    // For this test, we use the same key as push stream to sign the event
                    String signed = token.JWS(streamHandler.getPushStream().issuerKey);
                    setsNode.put(jti, signed);
                } catch (JoseException | MalformedClaimException e) {
                    logger.error(e.getMessage());
                    continue;
                }
                sent++;
                i++;
                if (i == maxItems) break;
            }
            respNode.set("sets", setsNode);
            if (pollEvents.size() - i > 0) {
                respNode.put("moreAvailable", true);
            }

            StringEntity body = new StringEntity(respNode.toPrettyString(), ContentType.DEFAULT_TEXT);
            return Response.ok(respNode.toPrettyString(), MediaType.TEXT_PLAIN_TYPE).build();
            //.status(200).entity(body).build();


        } catch (JsonProcessingException e) {
            logger.error("Unexpected error at polling endpoint: " + e.getMessage(), e);
        }
        return Response.serverError().build();
    }

    // Used to diagnose the mock server is running.
    @Path("/hello")
    @GET
    public String helloThere() {
        return "Hello there";
    }

    // The following are accessors for stats purposes in testing
    public static int getReceived() {
        return received;
    }

    public static int getJwtErrs() {
        return jwtErrs;
    }

    public static int getMiscErrs() {
        return miscErrs;
    }

    public static int getTotalReceivedEvents() {
        return received + jwtErrs + miscErrs;
    }

    public static int getAckedCnt() {
        return ackedCnt;
    }

    public static int getPendingPollCnt() {
        return pollEvents.size();
    }

    public static void addPollEvents(List<SecurityEventToken> events) {
        for (SecurityEventToken event : events) {
            pollEvents.put(event.getJti(), event);
        }
    }

    public static int getSent() {
        return sent;
    }

}
