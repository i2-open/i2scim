package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.serializer.JsonUtil;
import jakarta.ws.rs.core.MediaType;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static com.independentid.scim.core.ConfigMgr.findClassLoaderResource;
import static com.independentid.signals.StreamConfigProps.*;
import static com.independentid.signals.StreamModels.*;

public class SsfHandler {
    private final static Logger logger = LoggerFactory.getLogger(SsfHandler.class);

    public final static String SSF_WELLKNOWN = "/.well-known/ssf-configuration";
    public final static String SSF_IAT = "/iat";
    public final static String SSF_REG = "/register";
    public final static String SSF_CONFIG = "/stream";
    public final static String SSF_POLL = "/poll";
    public final static String SSF_PUSH = "/events";

    @JsonIgnore
    StreamConfigProps configProps;
    @JsonIgnore
    CloseableHttpClient client;

    public String serverUrl;
    public String iat;
    public String clientToken;
    public PollStream pollStream;
    public PushStream pushStream;
    public TransmitterConfig ssfConfig;
    public boolean initialized = false;

    protected SsfHandler() {
    }

    public String toString() {
        ObjectMapper mapper = JsonUtil.getMapper();
        StringWriter stringWriter = new StringWriter();
        try {
            mapper.writeValue(stringWriter, this);
        } catch (IOException e) {
            logger.error("Error serializing SSF Config: " + e.getMessage(), e);
        }
        return stringWriter.toString();
    }

    public static SsfHandler Open(StreamConfigProps props) throws IOException {
        SsfHandler client;
        if (props.configFileExists()) {
            File configFile = props.getConfigFile();
            try {
                FileInputStream configStream = new FileInputStream(configFile);
                client = JsonUtil.getMapper().readValue(configStream, SsfHandler.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            client = new SsfHandler();
        }
        client.configProps = props;
        client.init();
        PollStream pollStream = client.getPollStream();
        if (pollStream.enabled) {
            pollStream.issuerKey = props.getIssuerPublicKey();
            pollStream.receiverKey = props.getAudPrivateKey();
        }
        PushStream pushStream = client.getPushStream();
        if (pushStream.enabled) {
            pushStream.issuerKey = props.getIssuerPrivateKey();
            pushStream.receiverKey = props.getAudPublicKey();
        }
        return client;
    }

    public void init() throws IOException {
        if (initialized) return;

        // Both stream types initialize as disabled by default
        this.pollStream = new PollStream();
        this.pushStream = new PushStream();

        switch (configProps.getMode()) {
            case Mode_SsfAuto:
                logger.info("Initializing using SSF registration to (" + configProps.ssfUrl + ")....");
                try {
                    getWellKnownConfig();
                    registerAsClient();
                    registerPushStream();
                    registerPollStream();
                } catch (ScimException e) {
                    throw new RuntimeException(e);
                }
                break;

            case Mode_PreConfig:
                logger.info("Initializing using pre-config files....");
                if (!configProps.pubConfigFile.equals("NONE"))
                    loadTransmitterFile(configProps.pubConfigFile);

                if (!configProps.rcvConfigFile.equals("NONE"))
                    loadReceiverFile(configProps.rcvConfigFile);

                break;

            default:
                logger.info("Initializing using environment properties....");
                // In default mode, the streams are manually configured using properties alone.
                this.pushStream = configProps.getConfigPropPushStream();
                this.pollStream = configProps.getConfigPropPollStream();

        }
        initialized = true;
        this.save();
    }

    private void getWellKnownConfig() throws ScimException {
        this.serverUrl = configProps.ssfUrl;
        this.iat = configProps.ssfAuthorization;
        this.client = HttpClients.createDefault();

        String wellKnownUrl = serverUrl;
        if (!serverUrl.endsWith(SSF_WELLKNOWN)) {
            wellKnownUrl = serverUrl + SSF_WELLKNOWN;
        }

        // Because this is a new instance, we need to auto-register
        HttpGet req = new HttpGet(wellKnownUrl);
        try {
            CloseableHttpResponse resp = this.client.execute(req);

            int code = resp.getStatusLine().getStatusCode();
            if (code >= 200 && code < 300) {
                HttpEntity respEntity = resp.getEntity();
                InputStream contentStream = respEntity.getContent();
                String cfgString = new String(contentStream.readAllBytes());
                TransmitterConfig txConfig = JsonUtil.getMapper().readValue(cfgString, TransmitterConfig.class);
                resp.close();
                this.ssfConfig = txConfig;

            } else {
                String msg = "Received error retrieving Ssf Well-Known data from: " + serverUrl + " - " + resp.getStatusLine().getReasonPhrase();
                logger.error(msg);
                resp.close();
                throw new ScimException(msg);
            }
        } catch (IOException e) {
            logger.error("Error requesting SSF Well-known Configuration: " + e.getMessage());
            throw new ScimException("Error requesting SSF Well-known configuration: " + e.getMessage(), e);
        }
    }

    private void registerPushStream() throws IOException, ScimException {

        StreamModels.StreamConfig config = new StreamModels.StreamConfig();

        String[] auds = configProps.pubAud.split(",");
        config.Aud = new ArrayList<>();
        config.Aud.addAll(Arrays.asList(auds));

        config.Iss = configProps.pubIssuer;
        Delivery delivery = new Delivery();
        delivery.Method = ReceivePush;
        config.Delivery = delivery;
        config.EventsRequested = GetScimEventTypes(false);
        config.RouteMode = "FW";
        config.IssuerJwksUrl = configProps.pubIssJwksUrl;

        HttpPost createPost = new HttpPost(this.ssfConfig.configuration_endpoint);
        StringWriter configWriter = new StringWriter();
        JsonUtil.getMapper().writeValue(configWriter, config);
        StringEntity entity = new StringEntity(configWriter.toString());
        createPost.setEntity(entity);
        createPost.setHeader("Content-Type", MediaType.APPLICATION_JSON);
        createPost.setHeader("Authorization", "Bearer " + this.clientToken);

        CloseableHttpResponse resp = this.client.execute(createPost);

        if (resp.getStatusLine().getStatusCode() >= 400) {
            logger.error("Create push stream failed. Status " + resp.getStatusLine().toString());
            throw new ScimException("Push stream creation failed: " + resp.getStatusLine().toString());
        }
        HttpEntity body = resp.getEntity();

        StreamConfig pushResp = JsonUtil.getMapper().readValue(body.getContent(), StreamConfig.class);
        this.pushStream.streamId = pushResp.Id;
        this.pushStream.endpointUrl = pushResp.Delivery.EndpointUrl;
        this.pushStream.authorization = pushResp.Delivery.EndpointUrl;
        this.pushStream.enabled = true;

        resp.close();
    }

    private void registerPollStream() throws IOException, ScimException {

        StreamModels.StreamConfig config = new StreamModels.StreamConfig();

        String[] auds = configProps.rcvAud.split(",");
        config.Aud = new ArrayList<>();
        config.Aud.addAll(Arrays.asList(auds));

        config.Iss = configProps.rcvIss;
        config.Delivery = new Delivery();
        config.Delivery.Method = DeliveryPoll;
        config.EventsRequested = GetScimEventTypes(true);
        config.RouteMode = "FW";
        config.IssuerJwksUrl = configProps.rcvIssJwksUrl;

        HttpPost createPost = new HttpPost(this.ssfConfig.configuration_endpoint);
        StringWriter configWriter = new StringWriter();
        JsonUtil.getMapper().writeValue(configWriter, config);
        StringEntity entity = new StringEntity(configWriter.toString());
        createPost.setEntity(entity);
        createPost.setHeader("Content-Type", MediaType.APPLICATION_JSON);
        createPost.setHeader("Authorization", "Bearer " + this.clientToken);

        CloseableHttpResponse resp = this.client.execute(createPost);

        if (resp.getStatusLine().getStatusCode() >= 400) {
            logger.error("Create push stream failed. Status " + resp.getStatusLine().toString());
            throw new ScimException("Receive poll stream creation failed: " + resp.getStatusLine().toString());
        }
        HttpEntity body = resp.getEntity();

        StreamConfig pollResp = JsonUtil.getMapper().readValue(body.getContent(), StreamConfig.class);
        this.pollStream.streamId = pollResp.Id;
        this.pollStream.endpointUrl = pollResp.Delivery.EndpointUrl;
        this.pollStream.authorization = pollResp.Delivery.AuthorizationHeader;
        this.pollStream.enabled = true;
        resp.close();
    }

    protected void registerAsClient() throws ScimException {
        HttpPost postRequest = new HttpPost(this.ssfConfig.client_registration_endpoint);
        if (!this.iat.equals("NONE"))
            postRequest.setHeader("Authorization", this.iat);
        StringWriter stringWriter = new StringWriter();
        JsonGenerator gen;
        try {
            gen = JsonUtil.getGenerator(stringWriter, true);
            gen.writeStartObject();
            gen.writeStringField("description", "i2scim.io v0.7.0-dev");
            gen.writeArrayFieldStart("scopes");
            gen.writeString("admin");
            gen.writeString("stream");
            gen.writeEndArray();
            gen.writeEndObject();
            gen.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            HttpEntity entity = new StringEntity(stringWriter.toString());
            postRequest.setEntity(entity);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        try {
            postRequest.setHeader("Content-Type", MediaType.APPLICATION_JSON);
            postRequest.setHeader("Accept", MediaType.APPLICATION_JSON);
            CloseableHttpResponse resp = this.client.execute(postRequest);
            if (resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new ScimException("Ssf registration failed " + resp.getStatusLine().toString());
            }
            HttpEntity entity = resp.getEntity();
            JsonNode rootNode = JsonUtil.getJsonTree(entity.getContent());
            JsonNode token = rootNode.get("token");
            this.clientToken = token.asText();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void loadTransmitterFile(String pubConfigFile) throws IOException {
        logger.info("Loading Push stream config from: " + pubConfigFile);
        this.pushStream = new PushStream();

        InputStream configInput = findClassLoaderResource(pubConfigFile);
        if (configInput == null) {
            throw new RuntimeException("Error loading pub config file: " + pubConfigFile);
        }
        JsonNode configNode = JsonUtil.getJsonTree(configInput);
        JsonNode endNode = configNode.get("endpoint");
        if (endNode == null)
            throw new RuntimeException("scim.signals.pub.config.file contains no endpoint value.");
        this.pushStream.endpointUrl = endNode.asText();
        JsonNode tokenNode = configNode.get("token");
        if (tokenNode != null)
            this.pushStream.authorization = tokenNode.asText();
        tokenNode = configNode.get("iss");
        if (tokenNode != null)
            this.pushStream.iss = tokenNode.asText();

        tokenNode = configNode.get("aud");
        if (tokenNode != null)
            this.pushStream.aud = tokenNode.asText();
        if (!this.pushStream.isUnencrypted) {
            this.pushStream.issuerKey = configProps.getIssuerPrivateKey();
            this.pushStream.receiverKey = configProps.getAudPublicKey();
        }
        this.pushStream.enabled = true;
    }

    public PushStream getPushStream() {
        return this.pushStream;
    }

    public PollStream getPollStream() {
        return this.pollStream;
    }

    public void loadReceiverFile(String rcvConfigFile) throws IOException {
        logger.info("Loading Poll stream config from: " + rcvConfigFile);
        this.pollStream = new PollStream();

        InputStream configInput = findClassLoaderResource(rcvConfigFile);
        JsonNode configNode = JsonUtil.getJsonTree(configInput);
        JsonNode endNode = configNode.get("endpoint");
        if (endNode == null)
            throw new RuntimeException("scim.signals.pub.config.file contains no endpoint value.");
        this.pollStream.endpointUrl = endNode.asText();
        JsonNode tokenNode = configNode.get("token");
        if (tokenNode != null)
            this.pollStream.authorization = tokenNode.asText();

        tokenNode = configNode.get("iss");
        if (tokenNode != null)
            this.pollStream.iss = tokenNode.asText();

        tokenNode = configNode.get("aud");
        if (tokenNode != null)
            this.pollStream.aud = tokenNode.asText();


        tokenNode = configNode.get("issJwksUrl");
        if (tokenNode != null)
            this.pollStream.issJwksUrl = tokenNode.asText();

        if (!this.pollStream.isUnencrypted) {
            this.pollStream.issuerKey = configProps.getIssuerPublicKey();
            this.pollStream.receiverKey = configProps.getAudPrivateKey();
        }
        this.pollStream.enabled = true;
    }

    public void save() throws IOException {
        ObjectMapper mapper = JsonUtil.getMapper();
        File configFile = configProps.getConfigFile();


        if (!configFile.exists())
            configFile.createNewFile();
        mapper.writeValue(configFile, this);

    }

    /**
     * @return true if based on mode there is a valid configuration
     */
    public boolean validateConfiguration() {
        if (configProps == null) {
            logger.error("Unexpected error, configuration properties is not set");
            return false;
        }
        switch (configProps.getMode()) {
            case Mode_SsfAuto:
                return ((initialized && clientToken != null && !clientToken.isBlank()) &&
                        validateStreams());
            case Mode_Manual:
                return validateStreams();
            case Mode_PreConfig:
                return validatePreConfig() && validateStreams();

        }
        logger.error("Unable to detect configuration mode");
        return false;
    }

    private boolean validatePreConfig() {
        if (configProps.rcvEnabled) {
            if (configProps.rcvConfigFile.equals("NONE")) return false;
            String filePath = Objects.requireNonNull(SsfHandler.class.getResource(configProps.rcvConfigFile)).getFile();
            File rcvFile = new File(filePath);
            if (!rcvFile.exists()) return false;
        }
        if (configProps.pubEnabled) {
            if (configProps.pubConfigFile.equals("NONE")) return false;
            String filePath = Objects.requireNonNull(SsfHandler.class.getResource(configProps.pubConfigFile)).getFile();
            File pubFile = new File(filePath);
            return pubFile.exists();
        }

        return true;
    }

    private boolean validateStreams() {
        boolean isValid = true;
        if (configProps.rcvEnabled) {
            if (this.pollStream == null || !this.pollStream.enabled)
                isValid = false;
        }
        if (configProps.pubEnabled) {
            if (this.pushStream == null || !this.pushStream.enabled)
                isValid = false;
        }
        return isValid;
    }

}
