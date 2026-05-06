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
import java.security.Key;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicBoolean;

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
    boolean isUnencrypted;
    public String iss;
    public String aud;
    public String issJwksUrl;

    @JsonUnwrapped
    public StreamStateHolder state = new StreamStateHolder();

    public int maxRetries = 10;
    public int initialDelay = 2000;
    public int maxDelay = 300000;

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
        int attempt = 0;
        long delay = this.initialDelay;

        if (this.client == null)
            setSslContext(null);

        while (attempt <= this.maxRetries && !this.shuttingDown.get()) {
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
                    if (code >= 200 && code < 300) {
                        return true;
                    }

                    if (code == 429 || code >= 500) {
                        logger.warn("Retryable error response: " + code + " " + resp.getReasonPhrase());
                        // Fall through to retry logic
                    } else {
                        // Fatal error
                        String reason;
                        if (code == 400) {
                            logger.error("Received BAD request response.");
                            HttpEntity respEntity = resp.getEntity();
                            String body = "";
                            if (respEntity != null) {
                                byte[] respBytes = respEntity.getContent().readAllBytes();
                                body = new String(respBytes);
                                logger.error("\n" + body);
                            }
                            reason = code + " Bad Request: " + body;
                        } else {
                            logger.error("Received fatal error on event submission: " + code + " " + resp.getReasonPhrase());
                            reason = code + " " + resp.getReasonPhrase();
                        }
                        this.state.transitionTo(StreamStatus.DISABLED, reason);
                        return false;
                    }
                }
            } catch (IOException e) {
                if (this.shuttingDown.get()) {
                    logger.info("Push stream shutting down, aborting event push");
                    return false;
                }
                logger.warn("Communications error while pushing event (attempt " + (attempt + 1) + "): " + e.getMessage());
            }

            attempt++;
            if (attempt > this.maxRetries) {
                logger.error("Max retries reached. Event push failed.");
                this.state.transitionTo(StreamStatus.DISABLED, "Max retries reached after " + this.maxRetries + " attempts");
                break;
            }

            if (this.shuttingDown.get()) {
                logger.info("Push stream shutting down, aborting retry");
                return false;
            }

            try {
                logger.info("Retrying in " + delay + "ms...");
                Thread.sleep(delay);
                delay = Math.min(delay * 2, maxDelay);
            } catch (InterruptedException ie) {
                logger.warn("Interrupted while waiting for retry");
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    public void Close() throws IOException {
        logger.info("Closing push stream...");
        this.shuttingDown.set(true);

        if (this.client != null)
            this.client.close();
    }
}
