package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.independentid.set.SecurityEventToken;
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
                "Audience:  \t" + aud + "\n";
    }

    public boolean pushEvent(SecurityEventToken event) {
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
        StringEntity bodyEntity = new StringEntity(signed, ContentType.create("application/secevent+jwt"));
        HttpPost req = new HttpPost(this.endpointUrl);
        req.setEntity(bodyEntity);
        if (!this.authorization.equals("NONE")) {
            req.setHeader("Authorization", this.authorization);
        }
        try {
            CloseableHttpResponse resp = client.execute(req);

            int code = resp.getStatusLine().getStatusCode();
            if (code >= 200 && code < 300) {
                resp.close();
                return true;
            }
            logger.error("Received error on event submission: " + resp.getStatusLine().getReasonPhrase());
            resp.close();
            return false;
        } catch (IOException e) {
            logger.error("Error transmitting event: " + e.getMessage());
            return false;
        }
    }

    public void Close() throws IOException {
        if (this.client != null)
            this.client.close();
    }
}
