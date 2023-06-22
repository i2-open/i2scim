/*
 * Copyright (c) 2021.
 *
 * Confidential and Proprietary
 *
 * This unpublished source code may not be distributed outside
 * “Independent Identity Org”. without express written permission of
 * Phillip Hunt.
 *
 * People at companies that have signed necessary non-disclosure
 * agreements may only distribute to others in the company that are
 * bound by the same confidentiality agreement and distribution is
 * subject to the terms of such agreement.
 */

package com.independentid.signals;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.events.IEventHandler;
import com.independentid.scim.op.GetOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.SearchOp;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.set.SecurityEventToken;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.keys.X509Util;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

//@ApplicationScoped
//@Startup
@Singleton
@Priority(5)
@Named("SignalsEventHandler")
public class SignalsEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(SignalsEventHandler.class);

    @ConfigProperty(name = "scim.root.dir")
    String rootDir;

    @ConfigProperty(name = "scim.signals.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "scim.event.enable", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty(name = "scim.signals.stream.url", defaultValue = "NONE")
    String setPushEndpoint;

    @ConfigProperty(name = "scim.signals.admin.auth", defaultValue = "ssef")
    String setAdminAuthToken;

    @ConfigProperty(name = "scim.signals.stream.auth", defaultValue = "TBD")
    String setStreamToken;

    @ConfigProperty(name = "scim.signals.stream.issuer", defaultValue = "DEFAULT")
    String issuer;

    @ConfigProperty(name = "scim.signals.stream.aud", defaultValue = "example.com")
    String aud;

    @ConfigProperty(name = "scim.signals.stream.issuer.jwksUrl", defaultValue = "NONE")
    String issuerJwksUrl;

    @ConfigProperty(name = "scim.signals.stream.receiver.jwksUrl", defaultValue = "NONE")
    String receiverJwksUrl;

    @ConfigProperty(name = "scim.signals.stream.issuer.pem.path", defaultValue = "./issuer.pem")
    String issuerPemPath;

    @ConfigProperty(name = "scim.signals.stream.issuer.algnone.override", defaultValue = "false")
    boolean issuerUnsigned;


    protected static final List<String> acksPending = Collections.synchronizedList(new ArrayList<String>());
    protected static final List<Operation> pendingPubOps = Collections.synchronizedList(new ArrayList<Operation>());

    protected static final List<Operation> acceptedOps = Collections.synchronizedList(new ArrayList<Operation>());

    protected static final FifoCache<Operation> sendErrorOps = new FifoCache<Operation>(1024);

    final Properties prodProps = new Properties();


    @Inject
    ConfigMgr configMgr;

    @Inject
    SchemaManager schemaManager;

    @Inject
    PoolManager pool;

    Key issuerKey, receiverKey = null;

    static boolean isErrorState = false;

    boolean ready = false;

    CloseableHttpClient client;

    URI postNewEventUri, postPollEventsUri;

    public SignalsEventHandler() {

    }

    @Override
    @PostConstruct
    public void init() {
        if (!this.enabled) {
            logger.info("Signals Event Handler *disabled*");
            return;
        }

        logger.info("Signals Event Handler starting.");

        client = HttpClients.createDefault();

        try {
            URIBuilder build = new URIBuilder(setPushEndpoint);
            build.setPath("/status");
            URI statusUrl = build.build();

            build.setPath("/new-event");
            postNewEventUri = build.build();

            build.setPath("/poll");
            postPollEventsUri = build.build();

            logger.info("Checking connection to SSEF");
            HttpGet get = new HttpGet(statusUrl);
            get.setHeader(HttpHeaders.AUTHORIZATION, this.setStreamToken);

            CloseableHttpResponse resp = client.execute(get);

            if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                ready = true;
            } else {
                ready = false;
                logger.error("Access to SSEF failed: " + resp.getStatusLine().getReasonPhrase());
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }

        // ensure that config,schema managers are initialized.
        Operation.initialize(configMgr);

        //ready = true;
    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }

    public boolean loadKeys() {
        if (issuerUnsigned) return true;

        // load the issuer key
        File keyFile = new File(issuerPemPath);
        if (!keyFile.exists()) {
            keyFile = new File(configMgr.getServerRootPath(), issuerPemPath);
            if (!keyFile.exists()) {
                logger.error("No issuer keyfile found at:" + issuerPemPath);
                return false;
            }
        }

        X509Util util = new X509Util();

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            String keyString = Files.readString(keyFile.toPath(), Charset.defaultCharset());

            String pemString = keyString
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replaceAll(System.lineSeparator(), "")
                    .replace("-----END PRIVATE KEY-----", "");
            byte[] keyBytes = Base64.decodeBase64(pemString);
            issuerKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Error loading issuer PEM key: " + e.getMessage(), e);
            return false;
        }

        if (!this.receiverJwksUrl.equals("NONE")) {
            HttpsJwks httpJkws = new HttpsJwks(this.receiverJwksUrl);
            try {
                List<JsonWebKey> keys = httpJkws.getJsonWebKeys();

                for (JsonWebKey key : keys) {
                    if (key.getKeyId().equalsIgnoreCase(aud)) {
                        this.receiverKey = key.getKey();
                        logger.info("Receiver key loaded for audience " + aud);
                        break;
                    }

                }
                if (this.receiverKey == null) {
                    logger.warn("No receiver aud key was located from: " + this.receiverJwksUrl);
                    return false;
                }
            } catch (JoseException | IOException e) {
                logger.error("Error loading aud key from: " + this.receiverJwksUrl, e);
                return false;
            }
        }

        return true;

    }


    //@Incoming("rep-in")
    //@Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)

    public void consume(Object txn) {

        if (txn == null) {
            logger.warn("Ignoring invalid replication message.");
            return;
        }

        if (txn instanceof SecurityEventToken) {
            SecurityEventToken event = (SecurityEventToken) txn;
            Operation op = EventMapper.MapSetToOperation(event, schemaManager);
            if (logger.isDebugEnabled())
                logger.debug("\tReceived SCIM Event:\n" + op.toString());
            acceptedOps.add(op);
            pool.addJob(op);
        }

    }


    public FifoCache<Operation> getSendErrorOps() { return sendErrorOps; }

    public int getSendErrorCnt() { return sendErrorOps.size(); }

    private synchronized void produce(final Operation op) {
        RequestCtx ctx = op.getRequestCtx();
        if (ctx != null && ctx.isReplicaOp()) {
            if (logger.isDebugEnabled())
                logger.debug("Ignoring internal event: "+op);
            return; // This is a replication op and should not be re-replicated!
        }
        if (logger.isDebugEnabled())
            logger.debug("Processing event: "+op);

        SecurityEventToken token = EventMapper.MapOperationToSet(op);


    }

    /**
     * This method takes an operation and produces a stream.
     * @param op The {@link Operation} to be published
     */
    @Override
    public void publish(Operation op) {
        // Ignore search and get requests
        if (op instanceof GetOp
            || op instanceof SearchOp)
            return;
        pendingPubOps.add(op);
        processBuffer();

    }

    private synchronized void processBuffer() {
        while(pendingPubOps.size()>0 && isProducing())
            produce(pendingPubOps.remove(0));
    }

    @Override
    public boolean isProducing() {
        return !isErrorState;
    }

    @Override
    @PreDestroy
    public void shutdown() {
        //repEmitter.complete();
        if (notEnabled())
            return;

        processBuffer(); //Ensure all trans sent!

        try {
            this.client.close();
        } catch (IOException ignore) {

        }

    }


}
