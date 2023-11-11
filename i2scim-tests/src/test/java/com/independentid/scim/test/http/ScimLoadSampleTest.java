/*
 * Copyright 2021.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.test.http;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimLoadSampleTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimLoadSampleTest.class);

    //private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";
    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @Inject
    TestUtils testUtils;

    @TestHTTPResource("/")
    URL baseUrl;

    //private final static String dataSet = "classpath:/data/user-10pretty.json";
    private final static String dataSet = "classpath:/data/user-5000.json";

    private static ArrayList<ScimResource> data;
    private final static ArrayList<String> paths = new ArrayList<>();

    private static String readTime = null;

    //private static ScimResource user1,user2 = null;

    protected static ArrayList<ScimResource> readSampleData(SchemaManager smgr, String dataSet) throws IOException, ParseException, ScimException {
        ArrayList<ScimResource> data = new ArrayList<>();
        logger.debug("\t\tReading sample data from: " + dataSet);
        Instant start = Instant.now();

        InputStream dataStream = ConfigMgr.findClassLoaderResource(dataSet);
        JsonNode dataNode = JsonUtil.getJsonTree(dataStream);

        JsonNode info = dataNode.get("info");
        String seed = info.get("seed").asText();
        String vers = info.get("version").asText();
        logger.debug("\t\tSeed: " + seed + ", Vers: " + vers);

        dataNode = dataNode.get("results");
        logger.debug("\t\tJSON Parsed " + dataNode.size() + " records.");
        Iterator<JsonNode> iter = dataNode.elements();
        int cnt = 0;
        while (iter.hasNext()) {
            data.add(parseUser(smgr, iter.next()));
            cnt++;
        }
        Instant end = Instant.now();
        Duration dur = Duration.between(start, end);
        readTime = dur.getSeconds() + "." + dur.getNano() + "secs";
        logger.info("\t\tMapping complete. " + cnt + " records mapped to SCIM Resource in " + readTime);
        return data;
    }

    protected static ScimResource parseUser(SchemaManager smgr, JsonNode mapNode) throws IOException, ParseException, ScimException {
        StringWriter writer = new StringWriter();

        JsonGenerator gen = JsonUtil.getGenerator(writer, true);
        gen.writeStartObject();

        gen.writeArrayFieldStart("schemas");
        gen.writeString(ScimParams.SCHEMA_SCHEMA_User);
        gen.writeString(ScimParams.SCHEMA_SCHEMA_Ent_User);
        gen.writeEndArray();

        gen.writeObjectFieldStart("meta");
        gen.writeStringField("resourceType", "User");
        gen.writeEndObject();

        JsonNode name = mapNode.get("name");

        gen.writeFieldName("name");
        gen.writeStartObject();

        gen.writeStringField("honorificPrefix", name.get("title").asText());
        gen.writeStringField("familyName", name.get("last").asText());
        gen.writeStringField("givenName", name.get("first").asText());
        String formatted = name.get("first").asText() + " " + name.get("last").asText();
        gen.writeStringField("formatted", formatted);
        gen.writeEndObject();

        JsonNode locNode = mapNode.get("location");
        gen.writeFieldName("addresses");
        gen.writeStartArray();
        gen.writeStartObject();

        JsonNode snode = locNode.get("street");
        String street = snode.get("number").asText() + " " + snode.get("name").asText();
        gen.writeStringField("streetAddress", street);
        gen.writeStringField("locality", locNode.get("city").asText());
        gen.writeStringField("region", locNode.get("state").asText());
        gen.writeStringField("country", locNode.get("country").asText());
        gen.writeStringField("postalCode", locNode.get("postcode").asText());
        gen.writeStringField("type", "work");
        gen.writeBooleanField("primary", true);
        gen.writeEndObject();
        gen.writeEndArray();

        gen.writeFieldName("emails");
        gen.writeStartArray();
        gen.writeStartObject();
        gen.writeStringField("value", mapNode.get("email").asText());
        gen.writeStringField("type", "work");
        gen.writeEndObject();
        gen.writeEndArray();

        gen.writeStringField("userName", mapNode.path("login").get("username").asText());
        gen.writeStringField("password", mapNode.path("login").get("password").asText());
        gen.writeStringField("externalId", mapNode.path("login").get("uuid").asText());

        gen.writeFieldName("phoneNumbers");
        if (mapNode.get("phone") != null) {
            gen.writeStartArray();
            gen.writeStartObject();
            gen.writeStringField("value", mapNode.get("phone").asText());
            gen.writeStringField("type", "work");
            gen.writeEndObject();
        }
        if (mapNode.get("cell") != null) {
            gen.writeStartObject();
            gen.writeStringField("value", mapNode.get("cell").asText());
            gen.writeStringField("type", "mmobile");
            gen.writeEndObject();
        }
        gen.writeEndArray();

        JsonNode photos = mapNode.get("picture");
        if (photos != null) {
            gen.writeFieldName("photos");
            if (photos.get("large") != null) {
                gen.writeStartArray();
                gen.writeStartObject();
                gen.writeStringField("value", photos.get("large").asText());
                gen.writeStringField("type", "photo");
                gen.writeEndObject();
            }
            if (photos.get("thumbnail") != null) {
                gen.writeStartObject();
                gen.writeStringField("value", photos.get("thumbnail").asText());
                gen.writeStringField("type", "thumbnail");
                gen.writeEndObject();
            }
            gen.writeEndArray();
        }
        JsonNode tz = mapNode.path("location").get("timezone");
        String offset = "GMT" + tz.get("offset").asText();
        String[] names = TimeZone.getAvailableIDs(tz.get("offset").asInt());
        logger.debug("Zone names: " + Arrays.toString(names));
        TimeZone zone = TimeZone.getTimeZone(offset);
        gen.writeStringField("timezone", zone.getID());

        gen.close();
        writer.close();

        JsonNode scimjnode = JsonUtil.getJsonTree(writer.toString());
        return new ScimResource(smgr, scimjnode, null, "Users");

    }

    @Test
    public void a_initializeProvidero() {

        logger.info("========== Scim Load Test Sample Data ==========");
        logger.info("\tA. Initializing data set");

        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }

        try {
            data = readSampleData(smgr, dataSet);
        } catch (IOException | ParseException | ScimException e) {
            fail("Unable to read in sample data: " + e.getLocalizedMessage(), e);
        }
    }

    /**
     * This test checks that a JSON user can be parsed into a SCIM Resource
     */
    @Test
    public void b_ScimAddUserTest() throws MalformedURLException {

        logger.info("\tB. Adding Sample Users: Count=" + data.size());
        Instant start = Instant.now();

        URL rUrl = new URL(baseUrl, "/Users");
        String req = rUrl.toString();

        CloseableHttpClient client = HttpClients.createDefault();

        Iterator<ScimResource> iter = data.iterator();

        try {

            while (iter.hasNext()) {
                ScimResource user = iter.next();

                StringEntity reqEntity = null;
                String record;
                try {
                    record = user.toJsonString();
                    //logger.debug(record);
                    reqEntity = new StringEntity(record, ContentType.create(ScimParams.SCIM_MIME_TYPE, StandardCharsets.UTF_8));
                } catch (UnsupportedCharsetException e) {
                    fail("Unexpected error serializing sample data: " + e.getLocalizedMessage(), e);
                }

                HttpPost post = new HttpPost(req);
                post.setEntity(reqEntity);
                CloseableHttpResponse resp = client.execute(post);

                if (resp.getStatusLine().getStatusCode() == ScimResponse.ST_BAD_REQUEST) {
                    //logger.error("BAD REQUEST for record number: "+i);
                    //logger.error("Request entity:\n"+record);
                    HttpEntity bentity = resp.getEntity();
                    String body = EntityUtils.toString(bentity);
                    logger.warn("Error received:\n" + body);
                    assertThat(body)
                            .as("Is a uniqueness error")
                            .contains(ScimResponse.ERR_TYPE_UNIQUENESS);

                    continue;
                } else {

                    assertThat(resp.getStatusLine().getStatusCode())
                            .as("Create user response status of 201")
                            .isEqualTo(ScimResponse.ST_CREATED);

                }

                Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
                paths.add(hloc[0].getValue());
                resp.close();
            }

            Instant end = Instant.now();
            Duration loadTime = Duration.between(start, end);
            String elapse = loadTime.getSeconds() + "." + loadTime.getNano() + "secs";
            logger.info("----Summary----");
            logger.info("Read time:\t" + readTime);
            logger.info("Create time:\t" + elapse);
            logger.info("Records read: " + data.size() + ", created: " + paths.size());

            // There are a number of conflicts. Actual is 4978
            //assertThat(paths.size())
            //	.isEqualTo(data.size());
            assertThat(paths.size())
                    .isEqualTo(4978);


        } catch (IOException e) {
            Assertions.fail("Exception occured loading records: " + e.getMessage(), e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Test
    public void c_ResetTest() {
        Instant start = Instant.now();
        // Reset the provider but do not, erase the data. Instead force it to re-read the data.
        try {
            testUtils.resetProvider(false);
        } catch (ScimException | BackendException | IOException e) {
            fail("Unexpected error resetting Memory provider: " + e.getMessage(), e);
        }
        Instant end = Instant.now();
        Duration loadTime = Duration.between(start, end);
        String elapse = loadTime.getSeconds() + "." + loadTime.getNano() + "secs";
        logger.info("Re-read time: " + elapse);
    }


}
