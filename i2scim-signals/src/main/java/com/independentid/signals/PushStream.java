package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.independentid.set.SecurityEventToken;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Key;
import java.security.PublicKey;

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
    public boolean errorState = false;

    public int maxRetries = 10;
    public int initialDelay = 2000;
    public int maxDelay = 300000;

    @JsonIgnore
    CloseableHttpClient client = HttpClients.createDefault();

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
        if (this.errorState)
            return false;
        if (this.aud != null)
            event.setAud(this.aud);
        event.setIssuer(this.iss);

        if (this.endpointUrl.equals("NONE")) {
            logger.error("Push endpoint is not yet set. Waiting...");
            int i = 0;
            while (this.endpointUrl.equals("NONE")) {
                i++;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                }
                if (i == 30) {
                    logger.error("Continuing to wait for push endpoint configuration...");
                    i = 0;
                }
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

        while (attempt <= this.maxRetries) {
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
                    int code = resp.getStatusLine().getStatusCode();
                    if (code >= 200 && code < 300) {
                        return true;
                    }

                    if (code == 429 || code >= 500) {
                        logger.warn("Retryable error response: " + code + " " + resp.getStatusLine().getReasonPhrase());
                        // Fall through to retry logic
                    } else {
                        // Fatal error
                        if (code == 400) {
                            logger.error("Received BAD request response.");
                            HttpEntity respEntity = resp.getEntity();
                            if (respEntity != null) {
                                byte[] respBytes = respEntity.getContent().readAllBytes();
                                String msg = new String(respBytes);
                                logger.error("\n" + msg);
                            }
                        } else
                            logger.error("Received fatal error on event submission: " + code + " " + resp.getStatusLine().getReasonPhrase());
                        this.errorState = true;
                        return false;
                    }
                }
            } catch (IOException e) {
                logger.warn("Communications error while pushing event (attempt " + (attempt + 1) + "): " + e.getMessage());
            }

            attempt++;
            if (attempt > this.maxRetries) {
                logger.error("Max retries reached. Event push failed.");
                this.errorState = true;
                break;
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
        if (this.client != null)
            this.client.close();
    }
}
