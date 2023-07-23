package com.independentid.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.keys.X509Util;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.independentid.scim.core.ConfigMgr.findClassLoaderResource;

@Singleton
public class StreamHandler {

    private final static Logger logger = LoggerFactory.getLogger(StreamHandler.class);

    // When provided, the config file will be used to configure event publication instead of endpoint and auth parameters
    @ConfigProperty(name = "scim.signals.pub.config.file", defaultValue = "NONE")
    String pubConfigFile;

    // The SET Push Endpoint (RFC8935)
    @ConfigProperty(name = "scim.signals.pub.push.endpoint", defaultValue = "NONE")
    String pubPushStreamEndpoint;

    // The authorization token to be used for the SET Push endpoint
    @ConfigProperty(name = "scim.signals.pub.push.auth", defaultValue = "NONE")
    String pubPushStreamToken;

    // The issuer id value to use in SET tokens
    @ConfigProperty(name = "scim.signals.pub.issr", defaultValue = "DEFAULT")
    String pubIssuer;

    // The private key associated with the issuer id.
    @ConfigProperty(name = "scim.signals.pub.pem.path", defaultValue = "./issuer.pem")
    String pubPemPath;

    // When true, unsigned tokens will be generated
    @ConfigProperty(name = "scim.signals.pub.algNone.override", defaultValue = "false")
    boolean pubIsUnsigned;

    // The audience of the receiver
    @ConfigProperty(name = "scim.signals.pub.aud", defaultValue = "example.com")
    String pubAud;

    // When the audience public key is provided, SETs are encrypted.
    @ConfigProperty(name = "scim.signals.pub.aud.jwksurl", defaultValue = "NONE")
    String pubAudJwksUrl;

    @ConfigProperty(name = "scim.signals.pub.aud.jwksJson", defaultValue = "NONE")
    String pubAudJwksJson;

    // When provided, the config file will be used to configure event reception instead of endpoint and auth parameters
    @ConfigProperty(name = "scim.signals.rcv.config.file", defaultValue = "NONE")
    String rcvConfigFile;

    @ConfigProperty(name = "scim.signals.rcv.poll.endpoint", defaultValue = "NONE")
    String rcvPollUrl;

    @ConfigProperty(name = "scim.signals.rcv.iss", defaultValue = "")
    String rcvIss;

    @ConfigProperty(name = "scim.signals.rcv.aud", defaultValue = "")
    String rcvAud;


    @ConfigProperty(name = "scim.signals.rcv.poll.auth", defaultValue = "NONE")
    String rcvPollAuth;

    // The issuer public key when receiving events
    @ConfigProperty(name = "scim.signals.rcv.iss.jwksUrl", defaultValue = "NONE")
    String rcvIssJwksUrl;

    @ConfigProperty(name = "scim.signals.rcv.iss.jwksJson", defaultValue = "NONE")
    String rcvIssJwksJson;

    // The private PEM key path for the audience when receiving encrypted tokens
    @ConfigProperty(name = "scim.signals.rcv.poll.pem.path", defaultValue = "NONE")
    String rcvPemPath;

    // When true, unsigned tokens will be generated
    @ConfigProperty(name = "scim.signals.rcv.algNone.override", defaultValue = "false")
    boolean rcvIsUnsigned;

    @ConfigProperty(name = "scim.signals.test", defaultValue = "false")
    boolean isTest;

    PushStream pushStream = new PushStream();
    PollStream pollStream = new PollStream();

    public StreamHandler() {

    }

    @PostConstruct
    public void init() {
        if (this.isTest) {
            // When in test mode, quarkus serves requests on port 8081.
            pubPushStreamEndpoint = "http://localhost:8081/signals/events";
            rcvPollUrl = "http://localhost:8081/signals/poll";
        }

        if (!this.pubConfigFile.equals("NONE")) {
            try {
                InputStream configInput = findClassLoaderResource(this.pubConfigFile);
                JsonNode configNode = JsonUtil.getJsonTree(configInput);
                JsonNode endNode = configNode.get("endpoint");
                if (endNode == null)
                    throw new RuntimeException("scim.signals.pub.config.file contains no endpoint value.");
                this.pushStream.endpointUrl = endNode.asText();
                JsonNode tokenNode = configNode.get("token");
                if (tokenNode != null)
                    this.pushStream.authorization = tokenNode.asText();
            } catch (IOException e) {
                throw new RuntimeException("Error reading scim.signals.pub.config.file at: " + this.pubConfigFile, e);
            }

        } else {
            this.pushStream.endpointUrl = pubPushStreamEndpoint;
            this.pushStream.authorization = pubPushStreamToken;
        }
        this.pushStream.iss = pubIssuer;
        this.pushStream.aud = pubAud;  //TODO switch this to multi-value

        if (pubIsUnsigned) {
            this.pushStream.isUnencrypted = true;
        } else {
            this.pushStream.isUnencrypted = false;
            this.pushStream.issuerKey = loadPem(pubPemPath);
            this.pushStream.receiverKey = loadJwksPublicKey(pubAudJwksUrl, pubAudJwksJson, pubAud);
        }

        if (!this.rcvConfigFile.equals("NONE")) {
            try {
                InputStream configInput = findClassLoaderResource(this.rcvConfigFile);
                JsonNode configNode = JsonUtil.getJsonTree(configInput);
                JsonNode endNode = configNode.get("endpoint");
                if (endNode == null)
                    throw new RuntimeException("scim.signals.pub.config.file contains no endpoint value.");
                this.pollStream.endpointUrl = endNode.asText();
                JsonNode tokenNode = configNode.get("token");
                if (tokenNode != null)
                    this.pollStream.authorization = tokenNode.asText();
            } catch (IOException e) {
                throw new RuntimeException("Error reading scim.signals.pub.config.file at: " + this.pubConfigFile, e);
            }

        } else {
            this.pollStream.endpointUrl = rcvPollUrl;
            this.pollStream.authorization = rcvPollAuth;
        }
        this.pollStream.endpointUrl = rcvPollUrl;
        this.pollStream.authorization = rcvPollAuth;
        this.pollStream.iss = rcvIss;
        this.pollStream.aud = rcvAud;
        this.pollStream.isUnencrypted = rcvIsUnsigned;

        if (!rcvIsUnsigned) {
            this.pollStream.receiverKey = loadPem(rcvPemPath);
            this.pollStream.issuerKey = loadJwksPublicKey(rcvIssJwksUrl, rcvIssJwksJson, rcvIss);
        }
    }

    public PushStream getPushStream() {
        return this.pushStream;
    }

    public PollStream getPollStream() {
        return this.pollStream;
    }

    public static class PushStream {
        public String endpointUrl;
        public String authorization;
        public Key issuerKey, receiverKey;
        boolean isUnencrypted;
        public String iss;
        public String aud;
        CloseableHttpClient client = HttpClients.createDefault();

        public boolean pushEvent(SecurityEventToken event) {
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

    public static class PollStream {
        public String endpointUrl;
        public String authorization;
        public Key issuerKey, receiverKey;
        boolean isUnencrypted;
        public String iss;
        public String aud;
        int timeOutSecs = 3600; // 1 hour by default
        int maxEvents = 1000;
        boolean returnImmediately = false; // long polling
        CloseableHttpClient client = HttpClients.createDefault();

        public Map<String, SecurityEventToken> pollEvents(List<String> acks, boolean ackOnly) {
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

                HttpPost pollRequest = new HttpPost(this.endpointUrl);
                if (!this.authorization.equals("NONE")) {
                    pollRequest.setHeader("Authorization", this.authorization);
                }

                ArrayNode ackNode = reqNode.putArray("ack");
                for (String item : acks) {
                    ackNode.add(item);
                }

                StringEntity bodyEntity = new StringEntity(reqNode.toPrettyString(), ContentType.APPLICATION_JSON);

                pollRequest.setEntity(bodyEntity);
                CloseableHttpResponse resp = client.execute(pollRequest);

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


    public static Key loadPem(String path) {
        try {
            if (path.equals("NONE"))
                return null;
            InputStream keyInput = findClassLoaderResource(path);
            // load the issuer key

            X509Util util = new X509Util();
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            String keyString = new String(keyInput.readAllBytes());

            String pemString = keyString
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replaceAll(System.lineSeparator(), "")
                    .replace("-----END PRIVATE KEY-----", "");
            byte[] keyBytes = Base64.decodeBase64(pemString);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Error loading PEM PKCS8 key: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static Key loadJwksPublicKey(String url, String jwksJson, String kid) {
        if (!url.equals("NONE")) {
            HttpsJwks httpJkws = new HttpsJwks(url);
            try {
                List<JsonWebKey> keys = httpJkws.getJsonWebKeys();

                for (JsonWebKey key : keys) {
                    if (key.getKeyId().equalsIgnoreCase(kid)) {

                        logger.info("Public key matched" + kid);
                        return key.getKey();
                    }
                }
                String msg = "No aud public key was located from: " + url;
                logger.error(msg);
                throw new RuntimeException("No receiver aud key was located from: " + url);

            } catch (JoseException | IOException e) {
                logger.error("Error loading aud public key from: " + url, e);
                throw new RuntimeException(e);
            }
        } else {
            if (!jwksJson.equals("NONE")) {
                try {
                    JsonWebKeySet jwks = new JsonWebKeySet(jwksJson);
                    List<JsonWebKey> keys = jwks.getJsonWebKeys();

                    for (JsonWebKey key : keys) {
                        if (key.getKeyId().equalsIgnoreCase(kid)) {
                            logger.info("Public key loaded for " + kid);
                            return key.getKey();
                        }
                    }
                } catch (JoseException e) {
                    logger.error("Error parsing public key for " + kid, e);
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

}
