package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.independentid.set.SecurityEventToken;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.security.Key;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PushStream {
    private final static Logger logger = LoggerFactory.getLogger(PushStream.class);

    public String streamId;
    public boolean enabled = false;
    public String endpointUrl;
    public String authorization;
    @JsonIgnore
    public Key issuerKey;
    @JsonIgnore
    public PublicKey receiverKey;
    public boolean isUnencrypted;
    public String iss;
    public String aud;
    public String issJwksUrl;

    @JsonUnwrapped
    public StreamStateHolder state = new StreamStateHolder();

    public int maxRetries = 10;
    public int initialDelay = 2000;
    public int maxDelay = 300000;
    public int unauthorizedRetryMax = 10;
    public int unauthorizedRetryDelay = 15000;
    public int statusCheckInterval = 30000;
    public int idleVerifyInterval = 300000;

    @JsonIgnore
    public StatusEndpointResolver statusResolver;

    @JsonIgnore
    public java.util.function.Supplier<Key> issuerKeyReloader;

    @JsonIgnore
    private final AtomicBoolean preflightDone = new AtomicBoolean(false);

    @JsonIgnore
    private final AtomicReference<Instant> lastSuccessfulPush = new AtomicReference<>(Instant.now());

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

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public String toString() {
        if (endpointUrl == null || endpointUrl.isEmpty())
            return "<undefined>";
        return "StreamId:\t" + streamId + "\n" +
                "EndpointUrl:\t" + endpointUrl + "\n" +
                "Authorization:\t" + authorization.replaceAll(".", "*") + "\n" +
                "IssuerKey: \t" + (issuerKey != null) + "\n" +
                "ReceiverKey:\t" + (receiverKey != null) + "\n" +
                "Unencrypted:\t" + isUnencrypted + "\n" +
                "Issuer:    \t" + iss + "\n" +
                "Audience:  \t" + aud + "\n" +
                "RetryMax:\t" + maxRetries + "\n" +
                "RetryInterval:\t" + initialDelay + "\n" +
                "RetryMaxInterval:\t" + maxDelay + "\n";
    }

    public boolean pushEvent(SecurityEventToken event) {
        if (this.state.getStatus() != StreamStatus.ENABLED || this.shuttingDown.get())
            return false;
        if (this.aud != null)
            event.setAud(this.aud);
        event.setIssuer(this.iss);

        if (this.endpointUrl.equals("NONE")) {
            logger.error("Push endpoint is not yet set. Waiting...");
            int i = 0;
            while (this.endpointUrl.equals("NONE") && !this.shuttingDown.get()) {
                i++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for push endpoint configuration");
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (i == 30) {
                    logger.error("Continuing to wait for push endpoint configuration...");
                    i = 0;
                }
            }
            if (this.shuttingDown.get()) {
                logger.info("Push stream shutting down, aborting event push");
                return false;
            }
            logger.info("SET Push endpoint set to: " + this.endpointUrl);
        }

        String signed;
        try {
            signed = event.JWS(issuerKey);
            logger.info("Signed token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            logger.error("Event signing error: " + e.getMessage());
            return false;
        }
        if (this.client == null)
            setSslContext(null);

        // T2 — pre-flight status check before first send after init/restart
        if (preflightDone.compareAndSet(false, true)) {
            if (probeRemoteAndTransitionIfNonEnabled("pre-flight")) {
                return false;
            }
        }

        RetryStrategyConfig retryConfig = new RetryStrategyConfig(
                this.maxRetries,
                Duration.ofMillis(this.initialDelay),
                Duration.ofMillis(this.maxDelay),
                this.unauthorizedRetryMax,
                Duration.ofMillis(this.unauthorizedRetryDelay)
        );

        int attempt = 0;
        boolean jwsRetryAttempted = false;

        while (!this.shuttingDown.get()) {
            FailureClassification classification;
            try {
                if (attempt > 0)
                    logger.info("Pushing event to " + this.endpointUrl + " (Attempt " + (attempt + 1) + ")");

                StringEntity bodyEntity = new StringEntity(signed, ContentType.create("application/secevent+jwt"));
                HttpPost req = new HttpPost(this.endpointUrl);
                req.setEntity(bodyEntity);
                if (!this.authorization.equals("NONE")) {
                    req.setHeader("Authorization", this.authorization);
                }

                try (CloseableHttpResponse resp = client.execute(req)) {
                    int code = resp.getCode();
                    String reason = resp.getReasonPhrase();
                    byte[] body = readBody(resp);
                    String retryAfterHeader = headerValue(resp, "Retry-After");
                    classification = PushFailureClassifier.classify(code, reason, body, retryAfterHeader);

                    if (classification instanceof FailureClassification.Success) {
                        this.lastSuccessfulPush.set(Instant.now());
                        if (this.state.getStatus() != StreamStatus.ENABLED) {
                            this.state.transitionTo(StreamStatus.ENABLED, null);
                        }
                        return true;
                    }
                    logger.warn("Push response classified as " + classification.getClass().getSimpleName()
                            + " (code=" + code + ")");
                }
            } catch (IOException e) {
                if (this.shuttingDown.get()) {
                    logger.info("Push stream shutting down, aborting event push");
                    return false;
                }
                logger.warn("Communications error while pushing event (attempt " + (attempt + 1) + "): " + e.getMessage());
                classification = PushFailureClassifier.classify(e);
            }

            // RFC8935 §2.4 jws_signature_failed carve-out: reload PEM and retry once
            if (classification instanceof FailureClassification.Rfc8935 rfcErr
                    && Rfc8935Error.JWS_SIGNATURE_FAILED.equals(rfcErr.error().code())
                    && !this.isUnencrypted
                    && !jwsRetryAttempted
                    && this.issuerKeyReloader != null) {
                jwsRetryAttempted = true;
                this.issuerKey = this.issuerKeyReloader.get();
                try {
                    signed = event.JWS(this.issuerKey);
                    logger.info("Reloaded issuer PEM and re-signed SET; retrying once");
                    continue;
                } catch (JoseException | MalformedClaimException e) {
                    this.state.transitionTo(StreamStatus.DISABLED,
                            "Failed to re-sign after PEM reload: " + e.getMessage());
                    return false;
                }
            }

            // T1 — interrogate remote /status before backoff on transport/5xx
            if (classification instanceof FailureClassification.Transport
                    || classification instanceof FailureClassification.Server5xx) {
                if (probeRemoteAndTransitionIfNonEnabled("on-failure")) {
                    return false;
                }
            }

            RetryDecision decision = RetryStrategy.decide(classification, attempt, retryConfig);
            switch (decision) {
                case RetryDecision.Disable d -> {
                    logger.error("Push stream DISABLED: " + d.reason());
                    this.state.transitionTo(StreamStatus.DISABLED, d.reason());
                    return false;
                }
                case RetryDecision.SleepThenRetry s -> {
                    if (!sleep(s.delay().toMillis())) return false;
                    attempt++;
                }
                case RetryDecision.RetryNoCap n -> {
                    if (!sleep(n.delay().toMillis())) return false;
                    attempt++;
                }
            }
        }
        return false;
    }

    /**
     * Probe the receiver's /status endpoint and transition local state if remote reports
     * non-ENABLED. Returns true if the caller should abort (state transitioned to PAUSED
     * or DISABLED), false otherwise (continue normal retry/backoff).
     */
    public void pausedRecheck() {
        if (this.state.getStatus() != StreamStatus.PAUSED || this.statusResolver == null) {
            return;
        }
        URI host = receiverHost();
        if (host == null) return;
        Optional<URI> statusUrl = this.statusResolver.resolve(host, URI.create(this.endpointUrl));
        if (statusUrl.isEmpty()) return;
        RemoteStatus remote = RemoteStatusProbe.probe(this.client, statusUrl.get(), this.authorization);
        switch (remote) {
            case ENABLED -> this.state.transitionTo(StreamStatus.ENABLED, null);
            case DISABLED -> this.state.transitionTo(StreamStatus.DISABLED,
                    "Remote /status reports disabled (paused-recheck)");
            case PAUSED, UNKNOWN -> {}
        }
    }

    boolean probeRemoteAndTransitionIfNonEnabled(String context) {
        if (this.statusResolver == null) {
            return false;
        }
        URI receiverHost = receiverHost();
        if (receiverHost == null) {
            return false;
        }
        Optional<URI> statusUrl = this.statusResolver.resolve(receiverHost, URI.create(this.endpointUrl));
        if (statusUrl.isEmpty()) {
            return false;
        }
        RemoteStatus remote = RemoteStatusProbe.probe(this.client, statusUrl.get(), this.authorization);
        switch (remote) {
            case PAUSED -> {
                this.state.transitionTo(StreamStatus.PAUSED,
                        "Remote /status reports paused (" + context + ")");
                return true;
            }
            case DISABLED -> {
                this.state.transitionTo(StreamStatus.DISABLED,
                        "Remote /status reports disabled (" + context + ")");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public Instant getLastSuccessfulPush() {
        return this.lastSuccessfulPush.get();
    }

    URI receiverHost() {
        try {
            URI endpoint = URI.create(this.endpointUrl);
            return new URI(endpoint.getScheme(), endpoint.getAuthority(), null, null, null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean sleep(long ms) {
        if (this.shuttingDown.get()) {
            logger.info("Push stream shutting down, aborting retry");
            return false;
        }
        try {
            logger.info("Retrying in " + ms + "ms...");
            Thread.sleep(ms);
            return !this.shuttingDown.get();
        } catch (InterruptedException ie) {
            logger.warn("Interrupted while waiting for retry");
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static byte[] readBody(CloseableHttpResponse resp) throws IOException {
        HttpEntity entity = resp.getEntity();
        if (entity == null) return null;
        return entity.getContent().readAllBytes();
    }

    private static String headerValue(CloseableHttpResponse resp, String name) {
        var header = resp.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    public void Close() throws IOException {
        logger.info("Closing push stream...");
        this.shuttingDown.set(true);

        if (this.client != null)
            this.client.close();
    }
}
