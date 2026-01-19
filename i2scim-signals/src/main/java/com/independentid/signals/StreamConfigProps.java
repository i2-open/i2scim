package com.independentid.signals;

import jakarta.inject.Singleton;
import org.apache.commons.codec.binary.Base64;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

import static com.independentid.scim.core.ConfigMgr.findClassLoaderResource;

@Singleton
public class StreamConfigProps {

    private final static Logger logger = LoggerFactory.getLogger(StreamConfigProps.class);
    // Configuration properties

    @ConfigProperty(name = "scim.signals.enable", defaultValue = "false")
    public boolean enabled;

    @ConfigProperty(name = "scim.signals.pub.enable", defaultValue = "true")
    public boolean pubEnabled;

    @ConfigProperty(name = "scim.signals.rcv.enable", defaultValue = "true")
    public boolean rcvEnabled;

    // When provided, the config file will be used to configure event publication instead of endpoint and auth parameters
    @ConfigProperty(name = "scim.signals.pub.config.file", defaultValue = "NONE")
    public String pubConfigFile;

    // The SET Push Endpoint (RFC8935)
    @ConfigProperty(name = "scim.signals.pub.push.endpoint", defaultValue = "NONE")
    public String pubPushStreamEndpoint;

    // The authorization token to be used for the SET Push endpoint
    @ConfigProperty(name = "scim.signals.pub.push.auth", defaultValue = "NONE")
    public String pubPushStreamToken;

    // The issuer id value to use in SET tokens
    @ConfigProperty(name = "scim.signals.pub.iss", defaultValue = "DEFAULT")
    public String pubIssuer;

    // The private key associated with the issuer id.
    @ConfigProperty(name = "scim.signals.pub.pem.path", defaultValue = "NONE")
    public String pubPemPath;

    @ConfigProperty(name = "scim.signals.pub.pem.value", defaultValue = "NONE")
    public String pubPemValue;

    @ConfigProperty(name = "scim.signals.pub.issJwksUrl", defaultValue = "NONE")
    public String pubIssJwksUrl;

    // When true, unsigned tokens will be generated
    @ConfigProperty(name = "scim.signals.pub.algNone.override", defaultValue = "false")
    public boolean pubIsUnsigned;

    // The audience of the receiver
    @ConfigProperty(name = "scim.signals.pub.aud", defaultValue = "example.com")
    public String pubAud;

    // When the audience public key is provided, SETs are encrypted.
    @ConfigProperty(name = "scim.signals.pub.aud.jwksurl", defaultValue = "NONE")
    public String pubAudJwksUrl;

    @ConfigProperty(name = "scim.signals.pub.aud.jwksJson", defaultValue = "NONE")
    public String pubAudJwksJson;

    // When provided, the config file will be used to configure event reception instead of endpoint and auth parameters
    @ConfigProperty(name = "scim.signals.rcv.config.file", defaultValue = "NONE")
    public String rcvConfigFile;

    @ConfigProperty(name = "scim.signals.rcv.poll.endpoint", defaultValue = "NONE")
    public String rcvPollUrl;

    @ConfigProperty(name = "scim.signals.rcv.iss", defaultValue = "DEFAULT")
    public String rcvIss;

    @ConfigProperty(name = "scim.signals.rcv.aud", defaultValue = "DEFAULT")
    public String rcvAud;


    @ConfigProperty(name = "scim.signals.rcv.poll.auth", defaultValue = "NONE")
    public String rcvPollAuth;

    // The issuer public key when receiving events
    @ConfigProperty(name = "scim.signals.rcv.iss.jwksUrl", defaultValue = "NONE")
    public String rcvIssJwksUrl;

    @ConfigProperty(name = "scim.signals.rcv.iss.jwksJson", defaultValue = "NONE")
    public String rcvIssJwksJson;

    // The private PEM key path for the audience
    // e when receiving encrypted tokens
    @ConfigProperty(name = "scim.signals.rcv.poll.pem.path", defaultValue = "NONE")
    public String rcvPemPath;

    @ConfigProperty(name = "scim.signals.rcv.poll.pem.value", defaultValue = "NONE")
    public String rcvPemValue;

    @ConfigProperty(name = "scim.signals.ssf.configFile", defaultValue = "/scim/ssfConfig.json")
    public String ssfConfigfile;

    @ConfigProperty(name = "scim.signals.ssf.serverUrl", defaultValue = "NONE")
    public String ssfUrl;

    @ConfigProperty(name = "scim.signals.ssf.authorization", defaultValue = "NONE")
    public String ssfAuthorization;

    // When true, unsigned tokens will be generated
    @ConfigProperty(name = "scim.signals.rcv.algNone.override", defaultValue = "false")
    boolean unSignedMode;

    @ConfigProperty(name = "scim.signals.rcv.retry.max", defaultValue = "10")
    public int rcvRetryMax;

    @ConfigProperty(name = "scim.signals.rcv.retry.interval", defaultValue = "2000")
    public int rcvRetryInterval;

    @ConfigProperty(name = "scim.signals.rcv.retry.maxInterval", defaultValue = "300000")
    public int rcvRetryMaxInterval;

    @ConfigProperty(name = "scim.signals.pub.retry.max", defaultValue = "10")
    public int pubRetryMax;

    @ConfigProperty(name = "scim.signals.pub.retry.interval", defaultValue = "2000")
    public int pubRetryInterval;

    @ConfigProperty(name = "scim.signals.pub.retry.maxInterval", defaultValue = "300000")
    public int pubRetryMaxInterval;

    @ConfigProperty(name = "scim.signals.test", defaultValue = "false")
    boolean isTest;

    public final static String Mode_SsfAuto = "auto";
    public final static String Mode_PreConfig = "pre";
    public final static String Mode_Manual = "manual";

    public PushStream getConfigPropPushStream() {
        if (this.pubEnabled) {
            PushStream pushStream = new PushStream();
            pushStream.enabled = true;
            if (isTest)
                pushStream.endpointUrl = "http://localhost:8081/signals/events";
            else
                pushStream.endpointUrl = pubPushStreamEndpoint;
            pushStream.authorization = pubPushStreamToken;
            pushStream.iss = pubIssuer;
            pushStream.aud = pubAud;
            pushStream.issJwksUrl = pubIssJwksUrl;
            pushStream.maxRetries = pubRetryMax;
            pushStream.initialDelay = pubRetryInterval;
            pushStream.maxDelay = pubRetryMaxInterval;
            if (pubIsUnsigned) {
                pushStream.isUnencrypted = true;
            } else {
                pushStream.isUnencrypted = false;
                pushStream.issuerKey = getIssuerPrivateKey();
                pushStream.receiverKey = getAudPublicKey();
            }
            return pushStream;
        }
        return null;
    }

    public PollStream getConfigPropPollStream() {
        if (this.rcvEnabled) {
            PollStream pollStream = new PollStream();
            pollStream.enabled = true;
            if (isTest)
                pollStream.endpointUrl = "http://localhost:8081/signals/poll";
            else
                pollStream.endpointUrl = rcvPollUrl;
            pollStream.authorization = rcvPollAuth;
            pollStream.iss = rcvIss;
            pollStream.aud = rcvAud;
            pollStream.issJwksUrl = rcvIssJwksUrl;
            pollStream.isUnencrypted = unSignedMode;
            pollStream.maxRetries = rcvRetryMax;
            pollStream.initialDelay = rcvRetryInterval;
            pollStream.maxDelay = rcvRetryMaxInterval;

            if (!unSignedMode) {
                pollStream.receiverKey = getAudPrivateKey();
                pollStream.issuerKey = getIssuerPublicKey();
            }
            return pollStream;
        }
        return null;
    }

    public boolean configFileExists() {
        File cfgFile = getConfigFile();
        return cfgFile.exists() && cfgFile.length() > 1;
    }

    public Key getIssuerPrivateKey() {
        return loadPem(this.pubPemPath, this.pubPemValue);
    }

    public PublicKey getIssuerPublicKey() {
        return loadJwksPublicKey(this.rcvIssJwksUrl, this.rcvIssJwksJson, this.rcvIss);
    }

    public PublicKey getAudPublicKey() {
        return loadJwksPublicKey(this.pubAudJwksUrl, this.pubAudJwksJson, this.pubAud);
    }

    public Key getAudPrivateKey() {
        return loadPem(this.rcvPemPath, this.rcvPemValue);
    }

    private static Key loadPem(String pemPath, String pemValue) {
        try {
            if (pemPath.equals("NONE") && pemValue.equals("NONE"))
                return null;
            String pemString;
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            if (!pemPath.equals("NONE")) {
                InputStream keyInput = findClassLoaderResource(pemPath);
                // load the issuer key
                if (keyInput == null) {
                    throw new RuntimeException("Could not load issuer PEM at: " + pemPath);
                }


                String keyString = new String(keyInput.readAllBytes());
                pemString = keyString
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replaceAll(System.lineSeparator(), "")
                        .replace("-----END PRIVATE KEY-----", "");
            } else pemString = pemValue;
            byte[] keyBytes = Base64.decodeBase64(pemString);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Error loading PEM PKCS8 key: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private static PublicKey loadJwksPublicKey(String url, String jwksJson, String kid) {
        if (!url.equals("NONE")) {
            HttpsJwks httpJkws = new HttpsJwks(url);
            try {
                List<JsonWebKey> keys = httpJkws.getJsonWebKeys();

                for (JsonWebKey key : keys) {
                    if (key.getKeyId().equalsIgnoreCase(kid)) {

                        logger.info("Public key matched" + kid);
                        return (PublicKey) key.getKey();
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
                            return (PublicKey) key.getKey();
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

    public String getMode() {
        if (!ssfUrl.equals("NONE"))
            return Mode_SsfAuto;
        if (!pubConfigFile.equals("NONE") || !rcvConfigFile.equals("NONE"))
            return Mode_PreConfig;
        return Mode_Manual;
    }

    public File getConfigFile() {
        return new File(this.ssfConfigfile);
    }
}
