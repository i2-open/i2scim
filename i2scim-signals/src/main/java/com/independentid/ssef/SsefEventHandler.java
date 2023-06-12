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

package com.independentid.ssef;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.FifoCache;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.events.IEventHandler;
import com.independentid.scim.op.BulkOps;
import com.independentid.scim.op.GetOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.SearchOp;
import com.independentid.scim.protocol.RequestCtx;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

//@ApplicationScoped
//@Startup
@Singleton
@Priority(5)
@Named("SignalsEventHandler")
public class SsefEventHandler implements IEventHandler {
    private final static Logger logger = LoggerFactory.getLogger(SsefEventHandler.class);

    @ConfigProperty(name= "scim.root.dir")
    String rootDir;

    @ConfigProperty (name = "scim.ssef.enable", defaultValue = "false")
    boolean enabled;

    @ConfigProperty (name="scim.event.enable", defaultValue = "true")
    boolean eventsEnabled;

    @ConfigProperty(name="scim.ssef.stream.url",defaultValue = "NONE")
    String setPushEndpoint;

    @ConfigProperty(name = "scim.ssef.admin.auth", defaultValue="ssef")
    String setAdminAuthToken;

    @ConfigProperty(name= "scim.ssef.stream.auth",defaultValue = "TBD")
    String setStreamToken;


    protected static final List<String> acksPending = Collections.synchronizedList(new ArrayList<String>());
    protected static final List<Operation> pendingPubOps = Collections.synchronizedList(new ArrayList<Operation>());

    protected static final List<Operation> acceptedOps = Collections.synchronizedList(new ArrayList<Operation>());

    protected static final FifoCache<Operation> sendErrorOps = new FifoCache<Operation>(1024);

    final Properties prodProps = new Properties();


    @Inject
    ConfigMgr configMgr;


    @Inject
    PoolManager pool;

    static boolean isErrorState = false;

    boolean ready = false;

    CloseableHttpClient client;

    URI postNewEventUri, postPollEventsUri;
    public SsefEventHandler() {

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
            get.setHeader(HttpHeaders.AUTHORIZATION,this.setStreamToken);

            CloseableHttpResponse resp = client.execute(get);

            switch (resp.getStatusLine().getStatusCode()) {
                case HttpStatus.SC_OK:
                    break;
                default:
                    ready = false;
                    logger.error("Access to SSEF failed: "+resp.getStatusLine().getReasonPhrase());
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } catch (ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // ensure that config,schema managers are initialized.
        Operation.initialize(configMgr);

        ready = true;
    }

    public boolean notEnabled() {
        return !enabled || !eventsEnabled;
    }


    //@Incoming("rep-in")
    //@Acknowledgment(Acknowledgment.Strategy.PRE_PROCESSING)

    public void consume(JsonNode node)  {

        if (node == null) {
            logger.warn("Ignoring invalid replication message.");
            return;
        }


        try {
            Operation op = BulkOps.parseOperation(node,null,0,true);
            if (logger.isDebugEnabled())
                logger.debug("\tReceived SCIM Event:\n" + op.toString());
            acceptedOps.add(op);
            pool.addJob(op);
        } catch (ScimException e) {

            throw new RuntimeException(e);
        }



        // logevent will be triggereed within the operation itself depending on completion


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

        JsonNode token = EventMapper.MapOperation(op);
        StringEntity bodyEntity = new StringEntity(token.toPrettyString(), ContentType.APPLICATION_JSON);
        HttpPost eventPost = new HttpPost(postNewEventUri);
        eventPost.setHeader(HttpHeaders.AUTHORIZATION, setAdminAuthToken);
        eventPost.setEntity(bodyEntity);
        try {
            CloseableHttpResponse resp = client.execute(eventPost);
            if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK
                || resp.getStatusLine().getStatusCode() == HttpStatus.SC_ACCEPTED)
                return;
            logger.error("Unexpected http status submitting event to SSEF: "+resp.getStatusLine().getStatusCode());

        } catch (IOException e) {
            logger.error("IO Exception submitting event to SSEF: "+e.getMessage());
        }
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
