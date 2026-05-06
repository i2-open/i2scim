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
import java.net.URI;
import java.security.Key;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public int unauthorizedRetryMax = 10;
    public int unauthorizedRetryDelay = 15000;
    public int statusCheckInterval = 30000;

    @JsonIgnore
    public StatusEndpointResolver statusResolver;

    @JsonIgnore
    private final java.util.concurrent.atomic.AtomicBoolean preflightDone = new java.util.concurrent.atomic.AtomicBoolean(false);

    @JsonIgnore
    public CloseableHttpClient client;

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

        if (this.client == null)
            setSslContext(null);

        boolean shutdownMode = (retries == 0);

        // T2 — pre-flight status check before first poll after init/restart (skip in shutdown-mode)
        if (!shutdownMode && preflightDone.compareAndSet(false, true)) {
            if (probeRemoteAndTransitionIfNonEnabled("pre-flight")) {
                return eventMap;
            }
        }

        RetryStrategyConfig retryConfig = new RetryStrategyConfig(
                retries,
                Duration.ofMillis(this.initialDelay),
                Duration.ofMillis(this.maxDelay),
                this.unauthorizedRetryMax,
                Duration.ofMillis(this.unauthorizedRetryDelay)
        );

        int attempt = 0;

        while (!Thread.currentThread().isInterrupted()) {
            FailureClassification classification;
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
                    String reason = resp.getReasonPhrase();
                    HttpEntity respEntity = resp.getEntity();
                    byte[] body = (respEntity == null) ? null : respEntity.getContent().readAllBytes();
                    String retryAfter = headerValue(resp, "Retry-After");
                    classification = PollFailureClassifier.classify(statusCode, reason, body, retryAfter);

                    if (classification instanceof FailureClassification.Success) {
                        if (statusCode == HttpStatus.SC_OK && !acknowledgements.isEmpty()) {
                            logger.info("Updating acknowledgments");
                            for (String item : acknowledgements) {
                                SignalsEventHandler.acksPending.remove(item);
                            }
                        }
                        if (body != null) {
                            JsonNode respNode = JsonUtil.getJsonTree(body);
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
                                    }
                                }
                            }
                        }
                        return eventMap;
                    }
                    logger.warn("Poll response classified as " + classification.getClass().getSimpleName()
                            + " (code=" + statusCode + ")");
                }
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Polling aborted - thread interrupted during HTTP request");
                    return eventMap;
                }
                logTransportError(e, attempt);
                classification = PollFailureClassifier.classify(e);
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Polling aborted - thread interrupted during HTTP request");
                    return eventMap;
                }
                logger.error("Unexpected error during poll {} (attempt {}): [{}] {}",
                        this.endpointUrl, attempt + 1, e.getClass().getName(), e.getMessage(), e);
                classification = PollFailureClassifier.classify(new IOException(e));
            }

            if (shutdownMode) {
                return eventMap;
            }

            // T1 — interrogate transmitter /status before backoff on transport/5xx
            if (classification instanceof FailureClassification.Transport
                    || classification instanceof FailureClassification.Server5xx) {
                if (probeRemoteAndTransitionIfNonEnabled("on-failure")) {
                    return eventMap;
                }
            }

            RetryDecision decision = RetryStrategy.decide(classification, attempt, retryConfig);
            switch (decision) {
                case RetryDecision.Disable d -> {
                    logger.error("POLLING DISABLED: " + d.reason());
                    this.state.transitionTo(StreamStatus.DISABLED, d.reason());
                    return eventMap;
                }
                case RetryDecision.SleepThenRetry s -> {
                    if (!sleep(s.delay().toMillis())) return eventMap;
                    attempt++;
                }
                case RetryDecision.RetryNoCap n -> {
                    if (!sleep(n.delay().toMillis())) return eventMap;
                    attempt++;
                }
            }
        }
        return eventMap;
    }

    public void pausedRecheck() {
        if (this.state.getStatus() != StreamStatus.PAUSED || this.statusResolver == null) {
            return;
        }
        URI host = transmitterHost();
        if (host == null) return;
        Optional<URI> statusUrl = this.statusResolver.resolve(host, URI.create(this.endpointUrl));
        if (statusUrl.isEmpty()) return;
        RemoteStatus remote = RemoteStatusProbe.probe(this.client, statusUrl.get(), this.authorization);
        switch (remote) {
            case ENABLED -> this.state.transitionTo(StreamStatus.ENABLED, null);
            case DISABLED -> this.state.transitionTo(StreamStatus.DISABLED,
                    "Transmitter /status reports disabled (paused-recheck)");
            case PAUSED, UNKNOWN -> {}
        }
    }

    boolean probeRemoteAndTransitionIfNonEnabled(String context) {
        if (this.statusResolver == null) {
            return false;
        }
        URI host = transmitterHost();
        if (host == null) {
            return false;
        }
        Optional<URI> statusUrl = this.statusResolver.resolve(host, URI.create(this.endpointUrl));
        if (statusUrl.isEmpty()) {
            return false;
        }
        RemoteStatus remote = RemoteStatusProbe.probe(this.client, statusUrl.get(), this.authorization);
        switch (remote) {
            case PAUSED -> {
                this.state.transitionTo(StreamStatus.PAUSED,
                        "Transmitter /status reports paused (" + context + ")");
                return true;
            }
            case DISABLED -> {
                this.state.transitionTo(StreamStatus.DISABLED,
                        "Transmitter /status reports disabled (" + context + ")");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    URI transmitterHost() {
        try {
            URI endpoint = URI.create(this.endpointUrl);
            return new URI(endpoint.getScheme(), endpoint.getAuthority(), null, null, null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean sleep(long ms) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        try {
            logger.info("Retrying in " + ms + "ms...");
            Thread.sleep(ms);
            return !Thread.currentThread().isInterrupted();
        } catch (InterruptedException ie) {
            logger.info("Interrupted while waiting for retry");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void logTransportError(IOException e, int attempt) {
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
    }

    private static String headerValue(CloseableHttpResponse resp, String name) {
        var header = resp.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    public void Close() throws IOException {
        if (this.client != null)
            this.client.close();
    }
}
