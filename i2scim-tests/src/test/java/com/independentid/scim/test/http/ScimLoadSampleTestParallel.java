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

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimLoadSampleTestParallel {

    private final static Logger logger = LoggerFactory.getLogger(ScimLoadSampleTest.class);

    private static final int testThreads = 10;

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
    private final static List<String> paths = Collections.synchronizedList(new ArrayList<>());

    static String req = null;

    private static String readTime = null;

    protected synchronized ScimResource getRecord() {
        if (data.isEmpty()) return null;
        return data.remove(0); // remove and return the first item in the array.
    }

    @Test
    public void a_initializeMongo() {

        logger.info("========== Scim Concurrent Test Sample Load ==========");
        logger.info("\tA. Initializing test data set");

        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }

        try {
            data = ScimLoadSampleTest.readSampleData(smgr, dataSet);
        } catch (IOException | ParseException | ScimException e) {
            fail("Unable to read in sample data: " + e.getLocalizedMessage(), e);
        }
    }

    public static class ProcessedCounter {
        private static int count = 0;

        public static synchronized void increment() {
            int temp = count;
            count = temp + 1;
        }

        public static int getCount() {
            return count;
        }
    }

    /**
     * This test checks that a JSON user can be parsed into a SCIM Resource
     */
    @Test
    public void b_parallelTest() throws MalformedURLException, InterruptedException {
        logger.info("\tB. Adding sample users: Count=" + data.size() + ", threads=" + testThreads);
        Instant start = Instant.now();

        URL rUrl = new URL(baseUrl, "/Users");
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
                    sendUser(client, record);
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
                String elapse = threadTime.getSeconds() + "." + threadTime.getNano() + "secs";
                logger.info("\t***Thread #" + tcnt + " complete. Number of records processed: " + cnt + ", elapsed: " + elapse);
                latch.countDown();
            });
        }
        latch.await();
        Instant end = Instant.now();
        Duration loadTime = Duration.between(start, end);
        String elapse = loadTime.getSeconds() + "." + loadTime.getNano() + "secs";
        logger.info("------Summary------");
        logger.info("Read time:\t" + readTime);
        logger.info("Create time:\t" + elapse);
        logger.info("Records read: " + data.size() + ", processed: " + ProcessedCounter.count + ", created: " + paths.size());

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
            } catch (UnsupportedCharsetException e) {
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
