package com.independentid.scim.test.signals;

import com.independentid.signals.StreamConfigProps;
import com.sun.net.httpserver.HttpServer;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression: an SSF JWKS endpoint that returns malformed/empty JSON, or that
 * is unreachable, must not abort SCIM startup. The default Quarkus boot path
 * iterates {@code Instance<IEventHandler>}; any RuntimeException thrown out of
 * a JWKS load propagated up the @PostConstruct chain and killed the JVM.
 *
 * <p>This test exercises the boot-path loaders ({@code getIssuerPublicKey},
 * {@code getAudPublicKey}) directly, asserts they return {@code null} on every
 * failure mode, and confirms a subsequent successful fetch recovers the key
 * (proving the periodic-refresh path can heal without restart).
 */
class JwksGracefulLoadTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>("{}");

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", ex -> {
            byte[] payload = body.get().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, payload.length);
            ex.getResponseBody().write(payload);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
    }

    private static String validJwksJson(String kid) throws Exception {
        RsaJsonWebKey jwk = RsaJwkGenerator.generateJwk(2048);
        jwk.setKeyId(kid);
        JsonWebKeySet set = new JsonWebKeySet(jwk);
        return set.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY);
    }

    private static StreamConfigProps propsWithIssuerJwks(String url, String kid) {
        StreamConfigProps props = new StreamConfigProps();
        props.rcvIssJwksUrl = url;
        props.rcvIssJwksJson = "NONE";
        props.rcvIss = kid;
        props.ssfTrustCertsPath = "NONE";
        props.ssfTrustCertsValue = "NONE";
        return props;
    }

    @Test
    void returnsNullWhenJwksHasNoKeysMember() {
        body.set("{}");
        StreamConfigProps props = propsWithIssuerJwks(url(), "kid-1");

        AtomicReference<PublicKey> result = new AtomicReference<>();
        assertThatCode(() -> result.set(props.getIssuerPublicKey()))
                .as("missing 'keys' member must not throw — boot path must survive")
                .doesNotThrowAnyException();
        assertThat(result.get()).isNull();
    }

    @Test
    void returnsNullWhenJwksUnreachable() {
        // Stop the server so the request fails at connect.
        server.stop(0);
        StreamConfigProps props = propsWithIssuerJwks(url(), "kid-1");

        AtomicReference<PublicKey> result = new AtomicReference<>();
        assertThatCode(() -> result.set(props.getIssuerPublicKey()))
                .as("connection failure must not throw")
                .doesNotThrowAnyException();
        assertThat(result.get()).isNull();
    }

    @Test
    void returnsNullWhenNoMatchingKid() throws Exception {
        body.set(validJwksJson("other-kid"));
        StreamConfigProps props = propsWithIssuerJwks(url(), "expected-kid");

        AtomicReference<PublicKey> result = new AtomicReference<>();
        assertThatCode(() -> result.set(props.getIssuerPublicKey()))
                .as("kid mismatch must not throw")
                .doesNotThrowAnyException();
        assertThat(result.get()).isNull();
    }

    @Test
    void returnsNullOnInlineJsonParseError() {
        StreamConfigProps props = new StreamConfigProps();
        props.rcvIssJwksUrl = "NONE";
        props.rcvIssJwksJson = "{not valid json}";
        props.rcvIss = "kid-1";
        props.ssfTrustCertsPath = "NONE";
        props.ssfTrustCertsValue = "NONE";

        AtomicReference<PublicKey> result = new AtomicReference<>();
        assertThatCode(() -> result.set(props.getIssuerPublicKey()))
                .as("malformed inline JSON must not throw")
                .doesNotThrowAnyException();
        assertThat(result.get()).isNull();
    }

    @Test
    void recoversWhenJwksLaterReturnsValidKeys() throws Exception {
        body.set("{}");
        StreamConfigProps props = propsWithIssuerJwks(url(), "kid-recover");

        assertThat(props.getIssuerPublicKey())
                .as("first attempt against empty JWKS returns null")
                .isNull();

        body.set(validJwksJson("kid-recover"));

        assertThat(props.getIssuerPublicKey())
                .as("second attempt against populated JWKS returns the key")
                .isNotNull();
    }
}
