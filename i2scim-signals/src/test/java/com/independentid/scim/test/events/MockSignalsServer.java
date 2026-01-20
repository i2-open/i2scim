package com.independentid.scim.test.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.auth.SsfAuthorizationToken;
import com.independentid.scim.core.err.UnauthorizedException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.signals.StreamConfigProps;
import com.independentid.signals.StreamModels;
import io.quarkus.test.Mock;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.PublicKey;
import java.util.*;

import static com.independentid.signals.SsfHandler.*;
import static com.independentid.signals.StreamModels.DeliveryPoll;
import static com.independentid.signals.StreamModels.ReceivePush;

@Mock
@Singleton
@Path("/signals")
//@JsonIgnoreProperties(ignoreUnknown = true)
public class MockSignalsServer {
    private final static Logger logger = LoggerFactory.getLogger(MockSignalsServer.class);

    private final HashMap<String, StreamModels.StreamConfig> streamConfigs = new HashMap<>();
    // The issuer id value to use in SET tokens
    @ConfigProperty(name = "scim.signals.pub.iss", defaultValue = "DEFAULT")
    String pubIssuer;

    // The private key associated with the issuer id.
    @ConfigProperty(name = "scim.signals.pub.pem.path", defaultValue = "NONE")
    String pubPemPath;

    @ConfigProperty(name = "scim.signals.pub.pem.value", defaultValue = "NONE")
    String pubPemValue;

    @ConfigProperty(name = "scim.signals.rcv.iss.jwksJson", defaultValue = "NONE")
    String issJwksJson;

    @Inject
    StreamConfigProps configProps;

    Key issuerKey;
    PublicKey publicKey;

    static int received = 0;
    static int jwtErrs = 0;
    static int miscErrs = 0;
    static Map<String, SecurityEventToken> pollEvents = new HashMap<>();
    static Map<String, SecurityEventToken> ackEvents = new HashMap<>();
    static int ackedCnt = 0;
    static int sent = 0;

    protected SsfAuthorizationToken.AuthIssuer authIssuer;

    @PostConstruct
    public void init() throws IOException {

        // Use the push stream issuer key to issue IAT tokens for validation
        issuerKey = configProps.getIssuerPrivateKey();
        publicKey = configProps.getIssuerPublicKey();
        this.authIssuer = new SsfAuthorizationToken.AuthIssuer(configProps.pubIssuer, issuerKey, publicKey);

    }


    /*
    getIat issues an initial access token
     */
    @GET
    @Path(SSF_IAT)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response getIat(@HeaderParam("Authorization") String authz) {
        SsfAuthorizationToken.AuthContext authContext = null;
        if (authz != null) {
            String[] scopes = new String[1];
            scopes[0] = "admin";
            try {
                authContext = this.authIssuer.ValidateAuthorization(authz, scopes);
            } catch (UnauthorizedException e) {
                logger.error(e.getMessage());
                return Response.status(Response.Status.UNAUTHORIZED.getStatusCode(), e.getMessage()).build();
            }
        }
        try {
            String iat = authIssuer.IssueProjectIat(authContext);
            ObjectNode node = JsonUtil.getMapper().createObjectNode();

            node.put("token", iat);
            return Response.ok(node.toPrettyString(), MediaType.APPLICATION_JSON).build();

        } catch (MalformedClaimException | JoseException e) {
            logger.error("Error generating IAT: " + e.getMessage(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()).build();
        }

    }

    @POST
    @Path(SSF_REG)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response registerClient(@HeaderParam("Authorization") String authz, StreamModels.RegisterParameters regParams) {
        SsfAuthorizationToken.AuthContext authContext = null;
        if (authz != null) {
            String[] scopes = new String[1];
            scopes[0] = "reg";
            try {
                authContext = authIssuer.ValidateAuthorization(authz, scopes);
            } catch (UnauthorizedException e) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            if (authContext == null)
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "AuthContext was null").build();
        } else {
            logger.info("Missing authorization header");
            Response.status(Response.Status.UNAUTHORIZED.getStatusCode(), "Missing authorization header");
        }

        if (regParams != null) {

            assert authContext != null;
            try {
                // String clientId =  RandomStringUtils.random(5, true,true);
                String clientToken = this.authIssuer.IssuerClientToken("abc", authContext.projectId, true);

                ObjectNode node = JsonUtil.getMapper().createObjectNode();
                node.put("token", clientToken);
                return Response.ok(node.toPrettyString(), MediaType.APPLICATION_JSON).build();

            } catch (MalformedClaimException | JoseException e) {
                logger.error("Unexpected error: " + e.getMessage(), e);
                return Response.serverError().build();
            }

        }
        return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Missing request body: null input stream").build();
    }

    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response getJwksDefault() {
        return Response.ok().entity(this.issJwksJson).build();
    }

    @GET
    @Path("/jwks/{issuer}")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response getJwks(@PathParam("issuer") String issuer) {
        if (this.pubIssuer.equalsIgnoreCase(issuer))
            return Response.ok().entity(issJwksJson).build();

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path(SSF_WELLKNOWN)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response getWellKnown(@Context UriInfo uriInfo) {

        StreamModels.TransmitterConfig config = new StreamModels.TransmitterConfig();

        config.client_registration_endpoint = mapPathToServer(uriInfo, SSF_REG, SSF_WELLKNOWN);

        config.issuer = this.pubIssuer;

        ArrayList<String> methods = new ArrayList<>();
        methods.add(DeliveryPoll);
        methods.add(ReceivePush);
        config.delivery_methods_supported = methods;
        config.configuration_endpoint = mapPathToServer(uriInfo, SSF_CONFIG, SSF_WELLKNOWN);


        try {
            String jsonResponse = JsonUtil.getMapper().writeValueAsString(config);
            return Response.ok().entity(jsonResponse).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String mapPathToServer(UriInfo uriInfo, String newPath, String curPath) {
        String requestUri = uriInfo.getRequestUri().toString();
        int trimIndex = requestUri.indexOf(curPath);
        return requestUri.substring(0, trimIndex) + newPath;
    }

    @POST
    @Path(SSF_CONFIG)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response createStream(@HeaderParam("Authorization") String authz, @Context UriInfo uriInfo, StreamModels.StreamConfig configRequest) {
        SsfAuthorizationToken.AuthContext authContext = null;
        if (authz != null) {
            String[] scopes = new String[2];
            scopes[0] = "reg";
            scopes[1] = "admin";
            try {
                authContext = this.authIssuer.ValidateAuthorization(authz, scopes);
            } catch (UnauthorizedException e) {
                return Response.status(Response.Status.UNAUTHORIZED).build();
            }
            if (authContext == null)
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "AuthContext was null").build();
        }

        if (configRequest != null) {
            switch (configRequest.Delivery.Method) {
                case DeliveryPoll:
                    configRequest.Delivery.EndpointUrl = mapPathToServer(uriInfo, SSF_POLL, SSF_CONFIG);
                    break;
                case ReceivePush:
                    configRequest.Delivery.EndpointUrl = mapPathToServer(uriInfo, SSF_PUSH, SSF_CONFIG);
            }
            configRequest.Id = UUID.randomUUID().toString();
            configRequest.Delivery.AuthorizationHeader = "Bearer nothingToBear";
            configRequest.EventsDelivered = configRequest.EventsRequested;
            this.streamConfigs.put(configRequest.Id, configRequest);

            try {
                String jsonConfig = JsonUtil.getMapper().writeValueAsString(configRequest);

                return Response.ok(jsonConfig, MediaType.APPLICATION_JSON).build();

            } catch (JsonProcessingException e) {
                logger.error("Unexpected error: " + e.getMessage(), e);
                return Response.serverError().build();
            }

        }
        return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Invalid input: missing request body").build();
    }

    @GET
    @Path(SSF_CONFIG)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response getStreamConfig(@QueryParam("stream_id") String streamId) {
        if (streamId == null || streamId.equals("")) {
            return Response.status(Response.Status.NOT_IMPLEMENTED).build();
        }

        StreamModels.StreamConfig config = this.streamConfigs.get(streamId);
        try {
            String body = JsonUtil.getMapper().writeValueAsString(config);
            return Response.ok(body).build();
        } catch (JsonProcessingException e) {
            logger.error("Error serializing configuration: " + e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    /*
    receive processes the event stream, validates the event and then stores it localling in pollEvents for a
    subsequent poll which then receives the event back.
     */
    @POST
    @Path("/events")
    @Consumes("application/secevent+jwt")
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response receive(String eventString) {

        try {
            // For this test, the polling config has the correct keys to validate a pushed event
            SecurityEventToken token = new SecurityEventToken(eventString, publicKey, null);
            logger.info("Received event:\n" + token.toPrettyString());
            pollEvents.put(token.getJti(), token);
            received++;
            return Response.accepted().build();
        } catch (InvalidJwtException | JoseException e) {
            String msg = "{\n \"err\": \"invalid_key\",\n \"description\": \"" + e.getMessage() + "\"\n}";
            jwtErrs++;
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(msg)
                    .build();
        } catch (JsonProcessingException e) {
            miscErrs++;
            logger.error("Error validating token: " + e.getMessage(), e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path("/poll")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @PermitAll
    public Response pollRequest(String body) {

        JsonNode pollJson;
        try {
            pollJson = JsonUtil.getJsonTree(body.getBytes());
        } catch (IOException e) {
            logger.error("Unexpected error at polling endpoint: " + e.getMessage(), e);
            return Response.status(Response.Status.BAD_REQUEST.getStatusCode(), "Invalid JSON: " + e.getMessage()).build();
        }

        JsonNode ackNode = pollJson.get("ack");
        boolean hadAcks = false;
        if (ackNode != null && ackNode.isArray()) {
            ArrayNode array = (ArrayNode) ackNode;
            for (JsonNode node : array) {
                String jti = node.asText();
                // TODO should we check that the JTI is still unacked?
                ackEvents.remove(jti);
                ackedCnt++;
                hadAcks = true;
            }
        }
        JsonNode maxEvents = pollJson.get("maxEvents");
        int maxItems = 1000;
        if (maxEvents != null)
            maxItems = maxEvents.asInt();
        JsonNode returnImmediately = pollJson.get("returnImmediately");
        boolean doWait = true;
        if (returnImmediately != null)
            doWait = !returnImmediately.asBoolean();
        if (hadAcks) doWait = false; // we don't want to wait if there were acks.
        int waitCnt = 0;
        while (pollEvents.isEmpty() && doWait && waitCnt < 5) {
            waitCnt++;
            logger.info("Waiting for events...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
        if (pollEvents.isEmpty()) {
            String emptyResponse = "{\"sets\":{}}";
            return Response.ok(emptyResponse, MediaType.TEXT_PLAIN_TYPE).build();
        }
        ObjectNode respNode = JsonUtil.getMapper().createObjectNode();
        ObjectNode setsNode = JsonUtil.getMapper().createObjectNode();

        int i = 0;
        Set<String> set = new HashSet<>(pollEvents.keySet());
        for (String jti : set) {
            SecurityEventToken token = pollEvents.get(jti);
            try {
                // For this test, we use the same key as push stream to sign the event
                String signed = token.JWS(issuerKey);
                setsNode.put(jti, signed);
            } catch (JoseException | MalformedClaimException e) {
                logger.error(e.getMessage());
                continue;
            }
            ackEvents.put(jti, token);
            pollEvents.remove(jti);
            sent++;
            i++;
            if (i == maxItems) break;
        }
        respNode.set("sets", setsNode);
        if (pollEvents.size() - i > 0) {
            respNode.put("moreAvailable", true);
        }

        return Response.ok(respNode.toPrettyString(), MediaType.TEXT_PLAIN_TYPE).build();

    }

    // Used to diagnose the mock server is running.
    @Path("/hello")
    @GET
    public String helloThere() {
        return "Hello there";
    }

    // The following are accessors for stats purposes in testing
    public static int getReceived() {
        return received;
    }

    public static int getJwtErrs() {
        return jwtErrs;
    }

    public static int getMiscErrs() {
        return miscErrs;
    }

    public static int getTotalReceivedEvents() {
        return received + jwtErrs + miscErrs;
    }

    public static int getAckedCnt() {
        return ackedCnt;
    }

    public static int getUnAckedCnt() {
        return ackEvents.size();
    }

    public static int getPendingPollCnt() {
        return pollEvents.size();
    }

    public static void addPollEvents(List<SecurityEventToken> events) {
        for (SecurityEventToken event : events) {
            pollEvents.put(event.getJti(), event);
        }
    }

    public static int getSent() {
        return sent;
    }

}
