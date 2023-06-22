package com.independentid.scim.test.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.independentid.set.SecurityEventToken;
import jakarta.ejb.Init;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Key;
import java.util.List;

@Path("/signals")
public class TestReceiverResource {
    private final static Logger logger = LoggerFactory.getLogger(TestReceiverResource.class);
    String issuerUrl = "http://localhost:8888/jwks/myissuer.io";
    String aud = "example.com";
    String issuer = "myissuer.io";
    Key issuerPublicKey;
    boolean initialized = false;
    static int received = 0;
    static int jwtErrs = 0;
    static int miscErrs = 0;

    @Init
    public void initialize() {
        if (issuerPublicKey != null) return;

        HttpsJwks httpJkws = new HttpsJwks(this.issuerUrl);
        try {
            List<JsonWebKey> keys = httpJkws.getJsonWebKeys();

            for (JsonWebKey key : keys) {
                if (key.getKeyId().equalsIgnoreCase(this.issuer)) {
                    this.issuerPublicKey = key.getKey();
                    logger.info("IssuerKey loaded for " + this.issuer);
                    break;
                }

            }
            if (this.issuerPublicKey == null) {
                logger.warn("No issuer public key was located from: " + this.issuerUrl);
                throw new RuntimeException();
            }
        } catch (JoseException | IOException e) {
            logger.error("Error loading issuer key from: " + this.issuerUrl, e);
            throw new RuntimeException();
        }
        initialized = true;
    }

    @POST()
    @Path("/events")
    @Consumes("application/secevent+jwt")
    @Produces(MediaType.APPLICATION_JSON)
    public Response receive(String eventString) {
        if (!initialized) initialize();
        try {
            SecurityEventToken token = new SecurityEventToken(eventString, issuerPublicKey, null);
            logger.info("Received event:\n" + token.toPrettyString());
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
}
