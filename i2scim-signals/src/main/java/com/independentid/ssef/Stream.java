package com.independentid.ssef;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Stream {
    public static final String SET_CONTENT_TYPE = "application/secevent+jwt";
    private final static Logger logger = LoggerFactory.getLogger(SsefEventHandler.class);
    public String protocol;
    public boolean isReceiver;
    public String endpoint;
    public String authorization;
    public String streamConfigUrl;

    public boolean enabled;

    private CloseableHttpClient client;

    public Stream(boolean isReceiver,String protocol,String streamConfigUrl, String endpointUrl,String authz,boolean enabled) {
        this.isReceiver = isReceiver;
        this.protocol = protocol;
        this.streamConfigUrl = streamConfigUrl;
        this.endpoint = endpointUrl;
        this.authorization = authz;
        this.enabled = enabled;

        this.client = HttpClients.createDefault();

    }

    public boolean PushEvent(String jti,String jwtString) {
        //prepare request

        StringEntity reqEntity = null;

        reqEntity = new StringEntity(jwtString, ContentType.create(SET_CONTENT_TYPE, StandardCharsets.UTF_8));


        HttpPost post = new HttpPost(this.endpoint);
        post.addHeader("Authorization",this.authorization);
        post.setEntity(reqEntity);
        CloseableHttpResponse resp = null;
        try {
            resp = client.execute(post);
        } catch (IOException e) {
            logger.error("STREAM ERROR: Error received attempting to transmit event: "+e.getMessage());

            // TODO: When should this be retried?
            return false;
        }

        if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED || resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            return true;
        }
        if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
            HttpEntity entity = resp.getEntity();
            String body = null;
            try {
                body = EntityUtils.toString(entity);
            } catch (IOException e) {
                logger.error("Error parsing PUSH response (status 400):"+e.getMessage());
            }
        }

        return false;
    }

    public void Close() throws IOException {
        this.client.close();
    }
}


