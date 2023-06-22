package com.independentid.signals;

import com.independentid.set.SecurityEventToken;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;

public class Stream {
    public static final String SET_CONTENT_TYPE = "application/secevent+jwt";
    public static final String PROTOCOL_DELIVERY_PUSH = "https://schemas.openid.net/secevent/risc/delivery-method/poll";
    public static final String PROTOCoL_DELIVERY_POLL = "https://schemas.openid.net/secevent/risc/delivery-method/push";
    private final static Logger logger = LoggerFactory.getLogger(SignalsEventHandler.class);
    public String protocol;
    public boolean isReceiver;
    public String endpoint;
    public String authorization;
    public String streamConfigUrl;
    public Key issuerKey;

    public boolean enabled;

    private CloseableHttpClient client;

    public Stream(boolean isReceiver, String protocol, String streamConfigUrl, String endpointUrl, String authz, boolean enabled, Key issuerKey) {
        this.isReceiver = isReceiver;
        this.protocol = protocol;
        this.streamConfigUrl = streamConfigUrl;
        this.endpoint = endpointUrl;
        this.authorization = authz;
        this.enabled = enabled;
        this.client = HttpClients.createDefault();
        this.issuerKey = issuerKey;

    }

    protected int PushEvent(SecurityEventToken token) {
        //prepare request

        StringEntity reqEntity = null;

        String encodedToken;
        try {
            encodedToken = token.JWS(this.issuerKey);
        } catch (JoseException e) {
            throw new RuntimeException(e);
        } catch (MalformedClaimException e) {
            throw new RuntimeException(e);
        }

        reqEntity = new StringEntity(encodedToken, ContentType.create(SET_CONTENT_TYPE, StandardCharsets.UTF_8));


        HttpPost post = new HttpPost(this.endpoint);
        post.addHeader("Authorization", this.authorization);
        post.setEntity(reqEntity);
        CloseableHttpResponse resp = null;
        try {
            resp = client.execute(post);
        } catch (IOException e) {
            logger.error("STREAM ERROR: Error received attempting to transmit event: " + e.getMessage());

            // TODO: When should this be retried?
            return 500;
        }

        if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED || resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            return HttpStatus.SC_ACCEPTED;
        }
        if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
            HttpEntity entity = resp.getEntity();
            String body = null;
            try {
                body = EntityUtils.toString(entity);
            } catch (IOException e) {
                logger.error("Error parsing PUSH response (status 400):" + e.getMessage());
                return HttpStatus.SC_BAD_REQUEST;
            }
        }
        // should not happen
        logger.error("Internal error: unknown result occurred.");
        return 500;
    }

    public void Close() throws IOException {
        this.client.close();
    }

    public boolean isTransmitter() {
        return !this.isReceiver;
    }

    /**
     * @param event The Security Event to deliver
     * @return the http status code of the result (2xx is success, 4xx error, etc)
     */
    public int DeliverEvent(SecurityEventToken event) {
        if (this.isReceiver) return HttpStatus.SC_INTERNAL_SERVER_ERROR;
        switch (this.protocol) {
            case PROTOCOL_DELIVERY_PUSH:
                return this.PushEvent(event);
            default:
                return HttpStatus.SC_NOT_IMPLEMENTED;
        }
    }


}


