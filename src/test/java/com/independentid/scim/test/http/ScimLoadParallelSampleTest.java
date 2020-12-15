/*
 * Copyright (c) 2020.
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

package com.independentid.scim.test.http;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimLoadParallelSampleTest {

    private final static Logger logger = LoggerFactory.getLogger(ScimLoadSampleTest.class);

    private static final int testThreads = 50;

    //private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

    @Inject
    @Resource(name="SchemaMgr")
    SchemaManager smgr;

    @Inject
    BackendHandler handler;

    @ConfigProperty(name="scim.mongodb.uri",defaultValue = "mongodb://localhost:27017")
    String dbUrl;

    @ConfigProperty(name="scim.mongodb.dbname",defaultValue = "SCIMsample")
    String scimDbName;

    private static MongoClient mclient = null;

    @TestHTTPResource("/")
    URL baseUrl;

    //private final static String dataSet = "classpath:/data/user-10pretty.json";
    private final static String dataSet = "classpath:/data/user-5000.json";

    private final static ArrayList<ScimResource> data = new ArrayList<>();
    private final static List<String> paths = Collections.synchronizedList(new ArrayList<>());

    private static String readTime = null;

    static String req = null;

    //private static ScimResource user1,user2 = null;

    private void readSampleData() throws IOException, ParseException, ScimException {
        logger.debug("\t\tReading sample data from: "+dataSet);
        Instant start = Instant.now();
        File dataFile = ConfigMgr.findClassLoaderResource(dataSet);
        InputStream dataStream = new FileInputStream(dataFile);
        JsonNode dataNode = JsonUtil.getJsonTree(dataStream);

        JsonNode info = dataNode.get("info");
        String seed = info.get("seed").asText();
        String vers = info.get("version").asText();
        logger.debug("\t\tSeed: "+seed+", Vers: "+vers);

        dataNode = dataNode.get("results");
        logger.debug("\t\tJSON Parsed "+dataNode.size()+" records.");
        Iterator<JsonNode> iter = dataNode.elements();
        int cnt=0;
        while (iter.hasNext()) {
            parseUser(iter.next());
            cnt++;
        }
        Instant end = Instant.now();
        Duration dur = Duration.between(start, end);
        readTime = dur.getSeconds()+"."+dur.getNano()+"secs";
        logger.info("\t\tMapping complete. "+cnt+" records mapped to SCIM Resource in "+readTime);
    }

    private void parseUser(JsonNode mapNode) throws IOException, ParseException, ScimException {
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
        String formatted =  name.get("first").asText() + " " +  name.get("last").asText();
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
        String offset = "GMT"+tz.get("offset").asText();
        String[] names = TimeZone.getAvailableIDs(tz.get("offset").asInt());
        logger.debug("Zone names: "+ Arrays.toString(names));
        TimeZone zone = TimeZone.getTimeZone(offset);
        gen.writeStringField("timezone", zone.getID());

        gen.close();
        writer.close();

        JsonNode scimjnode = JsonUtil.getJsonTree(writer.toString());
        ScimResource user = new ScimResource(smgr,scimjnode,null,"Users");
        data.add(user);

    }

    protected synchronized ScimResource getRecord() {
        if (data.isEmpty()) return null;
        return data.remove(0); // remove and return the first item in the array.
    }

    @Test
    public void a_initializeMongo()  {

        logger.info("========== Scim Concurrent Test Load ==========");
        logger.info("\tA. Initializing test database: "+scimDbName);

        if (mclient == null)
            mclient = MongoClients.create(dbUrl);


        MongoDatabase scimDb = mclient.getDatabase(scimDbName);

        scimDb.drop();  // reset the database.

        try {
            handler.getProvider().syncConfig(smgr.getSchemas(), smgr.getResourceTypes());
        } catch (IOException | InstantiationException | ClassNotFoundException e) {
            fail("Failed to initialize test Mongo DB: "+scimDbName);
        }
        try {
            readSampleData();
        } catch (IOException | ParseException | ScimException e) {
            fail("Unable to read in sample data: "+e.getLocalizedMessage(),e);
        }
    }

    public static class ProcessedCounter {
        private static int count= 0;
        public static synchronized void increment() {
            int temp = count;
            count = temp + 1;
        }

        public static int getCount() { return count; }
    }
    /**
     * This test checks that a JSON user can be parsed into a SCIM Resource
     */
    @Test
    public void b_parallelTest() throws MalformedURLException, InterruptedException {
        logger.info("\tB. Adding sample users: Count="+data.size()+", threads="+testThreads);
        Instant start = Instant.now();

        URL rUrl = new URL(baseUrl,"/Users");
        req = rUrl.toString();

        int numberOfThreads = testThreads;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            final int tcnt = i;

            service.execute(() -> {
                Instant tstart = Instant.now();

                int cnt = 0;
                CloseableHttpClient client = HttpClients.createDefault();
                ScimResource record = getRecord();
                while (record != null) {
                    sendUser(client,record);
                    cnt++;  // increment records processed
                    record = getRecord(); // grab the next if available
                }
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Instant tend = Instant.now();
                Duration threadTime = Duration.between(tstart, tend);
                String elapse = threadTime.getSeconds()+"."+threadTime.getNano()+"secs";
                logger.info("\t***Thread #"+ tcnt +" complete. Number of records processed: "+cnt+", elapsed: "+elapse);
                latch.countDown();
            });
        }
        latch.await();
        Instant end = Instant.now();
        Duration loadTime = Duration.between(start, end);
        String elapse = loadTime.getSeconds()+"."+loadTime.getNano()+"secs";
        logger.info("------Summary------");
        logger.info("Read time:\t"+readTime);
        logger.info("Create time:\t"+elapse);
        logger.info("Records read: "+data.size()+", processed: "+ProcessedCounter.count+", created: "+paths.size());

        // There are a number of conflicts. Actual is 4978
        //assertThat(paths.size())
        //	.isEqualTo(data.size());
        assertThat(paths.size())
                .isEqualTo(4978);


    }


    public void sendUser(CloseableHttpClient client, ScimResource user) {

        try {

            StringEntity reqEntity = null;
            String record;
            try {
                record = user.toJsonString();
                //logger.debug(record);
                reqEntity = new StringEntity(record, ContentType.create(ScimParams.SCIM_MIME_TYPE, StandardCharsets.UTF_8));
            } catch (UnsupportedCharsetException | ScimException e) {
                fail("Unexpected error serializing sample data: " + e.getLocalizedMessage(), e);
            }

            HttpPost post = new HttpPost(req);
            post.setEntity(reqEntity);
            CloseableHttpResponse resp = client.execute(post);

            if (resp.getStatusLine().getStatusCode() == ScimResponse.ST_BAD_REQUEST) {

                //logger.error("Request entity:\n" + record);
                HttpEntity bentity = resp.getEntity();
                String body = EntityUtils.toString(bentity);
                logger.warn("Error received:\n" + body);
                assertThat(body)
                        .as("Is a uniqueness error")
                        .contains(ScimResponse.ERR_TYPE_UNIQUENESS);

                return;
            } else {

                assertThat(resp.getStatusLine().getStatusCode())
                        .as("Create user response status of 201")
                        .isEqualTo(ScimResponse.ST_CREATED);

            }
            ProcessedCounter.increment();
            Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
            paths.add(hloc[0].getValue());
            resp.close();

        } catch (IOException e) {
            Assertions.fail("Exception occured loading records: " + e.getMessage(), e);
        }
    }


}
