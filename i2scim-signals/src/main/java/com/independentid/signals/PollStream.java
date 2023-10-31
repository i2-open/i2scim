package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PollStream {
    private final static Logger logger = LoggerFactory.getLogger(PollStream.class);

    public String streamId;
    public boolean enabled = false;
    public String endpointUrl;
    public String authorization;
    @JsonIgnore
    public PublicKey issuerKey;
    @JsonIgnore
    public Key receiverKey;
    boolean isUnencrypted;
    public String iss;
    public String aud;
    int timeOutSecs = 3600; // 1 hour by default
    int maxEvents = 1000;
    boolean returnImmediately = false; // long polling
    boolean errorState = false;
    public String issJwksUrl;

    @JsonIgnore
    CloseableHttpClient client = HttpClients.createDefault();

    public String toString() {
        if (endpointUrl == null || endpointUrl.isEmpty())
            return "<undefined>";

        return "StreamId:\t" + streamId + "\n" +
                "EndpointUrl:\t" + endpointUrl + "\n" +
                "Authorization:\t" + authorization.replaceAll(".", "*") + "\n" +
                "IssuerKey:\t" + (issuerKey != null) + "\n" +
                "ReceiverKey:\t" + (receiverKey != null) + "\n" +
                "Unencrypted:\t" + isUnencrypted + "\n" +
                "Issuer:   \t" + iss + "\n" +
                "Audience: \t" + aud + "\n" +
                "TimeoutSecs:\t" + timeOutSecs + "\n" +
                "MaxEvents:\t" + maxEvents + "\n" +
                "ReturnImmed:\t" + returnImmediately + "\n";
    }

    public Map<String, SecurityEventToken> pollEvents(List<String> acknowledgements, boolean ackOnly) {
        Map<String, SecurityEventToken> eventMap = new HashMap<>();
        ObjectNode reqNode = JsonUtil.getMapper().createObjectNode();
        if (ackOnly) {
            reqNode.put("maxEvents", 0);
            reqNode.put("returnImmediately", true);
        } else {
            reqNode.put("maxEvents", this.maxEvents);
            reqNode.put("returnImmediately", this.returnImmediately);
        }

        if (this.endpointUrl.equals("NONE")) {
            logger.error("Polling endpoint is not yet set. Waiting...");
            int i = 0;
            while (this.endpointUrl.equals("NONE")) {
                i++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                }
                if (i == 30) {
                    logger.error("Continuing to wait for polling endpoint configuration...");
                    i = 0;
                }
            }
            logger.info("Polling endpoint set to: " + this.endpointUrl);
        }
        try {
            logger.info("Polling " + this.endpointUrl + " Acks:" + acknowledgements.size());
            HttpPost pollRequest = new HttpPost(this.endpointUrl);
            if (!this.authorization.equals("NONE")) {
                pollRequest.setHeader("Authorization", this.authorization);
            }
            List<String> requestedAck = new ArrayList<>();
            ArrayNode ackNode = reqNode.putArray("ack");
            for (String item : acknowledgements) {
                logger.info("POLLING: Acknowledging: " + item);
                ackNode.add(item);
                requestedAck.add(item);
            }

            StringEntity bodyEntity = new StringEntity(reqNode.toPrettyString(), ContentType.APPLICATION_JSON);

            pollRequest.setEntity(bodyEntity);
            CloseableHttpResponse resp = client.execute(pollRequest);
            if (resp.getStatusLine().getStatusCode() >= 400) {
                switch (resp.getStatusLine().getStatusCode()) {
                    case HttpStatus.SC_UNAUTHORIZED:
                        logger.error("Poll response was an Authorization Error. Check poll authorization configuration.");
                        break;
                    case HttpStatus.SC_BAD_REQUEST:
                        logger.error("Received BAD request response.");
                        HttpEntity respEntity = resp.getEntity();
                        if (respEntity != null) {
                            byte[] respBytes = respEntity.getContent().readAllBytes();
                            String msg = new String(respBytes);
                            logger.error("\n" + msg);
                        }
                        break;
                    default:
                        logger.error("Error response: " + resp.getStatusLine().getStatusCode() + " " + resp.getStatusLine().getReasonPhrase());
                }
                logger.error("POLLING DISABLED.");
                this.errorState = true;
                return eventMap;
            }
            // Update the acks pending list
            if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK && !requestedAck.isEmpty()) {
                logger.info("Updating acknowledgments");
                for (String item : requestedAck) {
                    SignalsEventHandler.acksPending.remove(item);
                }
            }
            HttpEntity respEntity = resp.getEntity();
            byte[] respBytes = respEntity.getContent().readAllBytes();
            JsonNode respNode = JsonUtil.getJsonTree(respBytes);
            JsonNode setNode = respNode.get("sets");

            for (JsonNode item : setNode) {
                String tokenEncoded = item.textValue();
                try {
                    SecurityEventToken token = new SecurityEventToken(tokenEncoded, this.issuerKey, this.receiverKey);
                    eventMap.put(token.getJti(), token);
                    logger.info("Received Event: " + token.getJti());
                } catch (InvalidJwtException | JoseException e) {
                    logger.error("Invalid token received: " + e.getMessage());
                    // TODO Need to respond with error ack
                }
            }
        } catch (UnsupportedEncodingException e) {
            logger.error("Unsupported encoding exception while polling: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Communications error while polling: " + e.getMessage());
        }
        return eventMap;
    }

    public void Close() throws IOException {
        if (this.client != null)
            this.client.close();
    }
}
