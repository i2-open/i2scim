package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
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
    public String issJwksUrl;

    @JsonUnwrapped
    public StreamStateHolder state = new StreamStateHolder();

    public int maxRetries = 10;
    public int initialDelay = 2000;
    public int maxDelay = 300000;

    @JsonIgnore
    CloseableHttpClient client;

    public void setSslContext(javax.net.ssl.SSLContext sslContext) {
        if (sslContext != null) {
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            this.client = HttpClients.custom()
                    .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(sslsf)
                            .build())
                    .build();
        } else {
            this.client = HttpClients.createDefault();
        }
    }

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
                "ReturnImmed:\t" + returnImmediately + "\n" +
                "RetryMax:\t" + maxRetries + "\n" +
                "RetryInterval:\t" + initialDelay + "\n" +
                "RetryMaxInterval:\t" + maxDelay + "\n";
    }

    public Map<String, SecurityEventToken> pollEvents(List<String> acknowledgements, boolean ackOnly) {
        return pollEvents(acknowledgements, ackOnly, this.maxRetries);
    }

    public Map<String, SecurityEventToken> pollEvents(List<String> acknowledgements, boolean ackOnly, int retries) {
        Map<String, SecurityEventToken> eventMap = new HashMap<>();

        // Check for interruption at the start
        if (Thread.currentThread().isInterrupted()) {
            logger.info("Polling aborted - thread interrupted");
            return eventMap;
        }

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
            while (this.endpointUrl.equals("NONE") && !Thread.currentThread().isInterrupted()) {
                i++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.info("Interrupted while waiting for endpoint configuration");
                    Thread.currentThread().interrupt();
                    return eventMap;
                }
                if (i == 30) {
                    logger.error("Continuing to wait for polling endpoint configuration...");
                    i = 0;
                }
            }
            if (Thread.currentThread().isInterrupted()) {
                return eventMap;
            }
            logger.info("Polling endpoint set to: " + this.endpointUrl);
        }

        ArrayNode ackNode = reqNode.putArray("ack");
        for (String item : acknowledgements) {
            logger.info("POLLING: Acknowledging: " + item);
            ackNode.add(item);
        }

        int attempt = 0;
        long delay = this.initialDelay;

        if (this.client == null)
            setSslContext(null);

        while (attempt <= retries && !Thread.currentThread().isInterrupted()) {
            // Check for interruption before each attempt
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Polling aborted - thread interrupted before attempt " + (attempt + 1));
                return eventMap;
            }

            try {
                if (attempt > 0)
                    logger.info("Polling " + this.endpointUrl + " (Attempt " + (attempt + 1) + ")");
                else
                    logger.info("Polling " + this.endpointUrl + " Acks:" + acknowledgements.size());

                HttpPost pollRequest = new HttpPost(this.endpointUrl);
                if (!this.authorization.equals("NONE")) {
                    pollRequest.setHeader("Authorization", this.authorization);
                }

                StringEntity bodyEntity = new StringEntity(reqNode.toPrettyString(), ContentType.APPLICATION_JSON);
                pollRequest.setEntity(bodyEntity);

                try (CloseableHttpResponse resp = client.execute(pollRequest)) {
                    int statusCode = resp.getCode();
                    if (statusCode >= 400) {
                        if (statusCode == 429 || statusCode >= 500) {
                            logger.warn("Retryable error response: " + statusCode + " " + resp.getReasonPhrase());
                            // Fall through to retry logic below
                        } else {
                            // Fatal error
                            switch (statusCode) {
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
                                    logger.error("Error response: " + statusCode + " " + resp.getReasonPhrase());
                            }
                            logger.error("POLLING DISABLED.");
                            this.state.transitionTo(StreamStatus.DISABLED, statusCode + " " + resp.getReasonPhrase());
                            return eventMap;
                        }
                    } else {
                        // Success path
                        // Update the acks pending list
                        if (statusCode == HttpStatus.SC_OK && !acknowledgements.isEmpty()) {
                            logger.info("Updating acknowledgments");
                            for (String item : acknowledgements) {
                                SignalsEventHandler.acksPending.remove(item);
                            }
                        }
                        HttpEntity respEntity = resp.getEntity();
                        if (respEntity != null) {
                            byte[] respBytes = respEntity.getContent().readAllBytes();
                            JsonNode respNode = JsonUtil.getJsonTree(respBytes);
                            JsonNode setNode = respNode.get("sets");

                            if (setNode != null && setNode.isObject()) {
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
                            }
                        }
                        return eventMap;
                    }
                }
            } catch (IOException e) {
                // Check if this was caused by interruption
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Polling aborted - thread interrupted during HTTP request");
                    return eventMap;
                }
                // Walk the cause chain to find TLS/SSL root cause
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                boolean isTls = e instanceof javax.net.ssl.SSLException
                        || root instanceof javax.net.ssl.SSLException
                        || e.getClass().getName().contains("SSL")
                        || root.getClass().getName().contains("SSL");
                if (isTls) {
                    logger.warn("TLS/SSL error while polling {} (attempt {}): [{}] {}{}",
                            this.endpointUrl, attempt + 1,
                            e.getClass().getSimpleName(), e.getMessage(),
                            root != e ? " caused by [" + root.getClass().getSimpleName() + "] " + root.getMessage() : "",
                            e);
                } else {
                    logger.warn("Communications error while polling {} (attempt {}): [{}] {}",
                            this.endpointUrl, attempt + 1,
                            e.getClass().getSimpleName(), e.getMessage(), e);
                }
            } catch (Exception e) {
                // Catch unexpected runtime exceptions (e.g. NPE, IllegalStateException from stale SSLContext)
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Polling aborted - thread interrupted during HTTP request");
                    return eventMap;
                }
                logger.error("Unexpected error during poll {} (attempt {}): [{}] {}",
                        this.endpointUrl, attempt + 1,
                        e.getClass().getName(), e.getMessage(), e);
                // Treat as retryable — fall through to retry logic
            }

            attempt++;
            if (attempt > retries) {
                if (retries > 0) {
                    logger.error("Max retries reached. POLLING DISABLED.");
                    this.state.transitionTo(StreamStatus.DISABLED, "Max retries reached after " + retries + " attempts");
                }
                break;
            }

            // Check for interruption before sleeping
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Polling aborted - thread interrupted before retry delay");
                return eventMap;
            }

            try {
                logger.info("Retrying in " + delay + "ms...");
                Thread.sleep(delay);
                delay = Math.min(delay * 2, maxDelay);
            } catch (InterruptedException ie) {
                logger.info("Interrupted while waiting for retry");
                Thread.currentThread().interrupt();
                break;
            }
        }
        return eventMap;
    }

    public void Close() throws IOException {
        if (this.client != null)
            this.client.close();
    }
}
