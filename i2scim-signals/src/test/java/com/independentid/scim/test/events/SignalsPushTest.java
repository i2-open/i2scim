package com.independentid.scim.test.events;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.set.SecurityEventToken;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.keys.X509Util;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import static com.independentid.scim.core.ConfigMgr.findClassLoaderResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(SignalsEventTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SignalsPushTest {
    private final static Logger logger = LoggerFactory.getLogger(SignalsPushTest.class);

    @TestHTTPEndpoint(TestReceiverResource.class)
    @TestHTTPResource()
    URL url;

    static String eventPath;

    @ConfigProperty(name = "scim.signals.stream.issuer.pem.path", defaultValue = "./issuer.pem")
    String issuerPemPath;

    @ConfigProperty(name = "scim.signals.stream.issuer")
    String issuer;

    @Inject
    ConfigMgr configMgr;

    static Key issuerKey;

    @Test
    public void a_init_test() {

        eventPath = url.toString() + "/events";

        String keyString;
        try {
            InputStream keyInput = findClassLoaderResource("/data/issuer.pem");
            assert keyInput != null;
            keyString = new String(keyInput.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        X509Util util = new X509Util();

        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            String pemString = keyString
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replaceAll(System.lineSeparator(), "")
                    .replace("-----END PRIVATE KEY-----", "");
            byte[] keyBytes = Base64.decodeBase64(pemString);
            issuerKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Error loading issuer PEM key: " + e.getMessage(), e);
            fail("Error loading PEM: " + e.getMessage(), e);
        }
        assertThat(this.issuerKey).isNotNull();
    }

    @Test
    public void b_SendEvent() {
        SecurityEventToken token = new SecurityEventToken();

        token.setJti("123457890");
        token.setTxn("1234");
        token.setAud("example.com");
        token.setIssuer(issuer);

        String tokenJsonString = token.toPrettyString();
        logger.info("Token: \n" + tokenJsonString);

        String signed = null;
        try {
            signed = token.JWS(issuerKey);
            logger.info("Signed token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed: " + e.getMessage());
        }
        assertThat(signed).isNotNull();

        StringEntity bodyEntity = new StringEntity(signed, ContentType.create("application/secevent+jwt"));
        CloseableHttpClient client = HttpClients.createDefault();

        HttpPost req = new HttpPost(eventPath);
        req.setEntity(bodyEntity);

        try {
            CloseableHttpResponse resp = client.execute(req);

            assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_ACCEPTED);
            resp.close();
            assertThat(TestReceiverResource.getReceived()).isEqualTo(1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            signed = token.JWS(null);
            logger.info("Unsigned token:\n" + signed);

        } catch (JoseException | MalformedClaimException e) {
            fail("Token was not signed: " + e.getMessage());
        }

        bodyEntity = new StringEntity(signed, ContentType.create("application/secevent+jwt"));
        req = new HttpPost(eventPath);
        req.setEntity(bodyEntity);

        try {
            CloseableHttpResponse resp = client.execute(req);
            assertThat(resp.getStatusLine().getStatusCode()).isEqualTo(HttpStatus.SC_BAD_REQUEST);
            assertThat(TestReceiverResource.getJwtErrs()).isEqualTo(1);
            assertThat(TestReceiverResource.getTotalReceivedEvents()).isEqualTo(2);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
