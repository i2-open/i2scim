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

package com.independentid.scim.test.misc;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;

import static org.assertj.core.api.Assertions.fail;

@Singleton
public class TestUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    public static final String ENV_TEST_OPA_URL = "TEST_OPA_URL";
    public static final String DEF_TEST_OPA_URL = "http://localhost:8181/v1/data/i2scim";

    public static final String ENV_TEST_MONGO_URI = "TEST_MONGO_URI";
    public static final String DEF_TEST_MONGO_URI = "mongodb://localhost:27017";

    // Not supported because each profile sets its own DB name
    //public static final String ENV_TEST_MONGO_DBNAME = "TEST_MONGO_DBNAME";
    public static final String DEF_TEST_MONGO_DBNAME = "testSCIM";
    public static final String ENV_TEST_MONGO_USER = "TEST_MONGO_USER";
    public static final String DEF_TEST_MONGO_USER = "admin";
    public static final String ENV_TEST_MONGO_SECRET = "TEST_MONGO_SECRET";
    public static final String DEF_TEST_MONGO_SECRET = "t0p-Secret";

    @Inject
    BackendHandler handler;

    @Inject
    SchemaManager smgr;

    @ConfigProperty(name = "scim.prov.memory.persist.dir", defaultValue = "./scimdata")
    String storeDir;

    @ConfigProperty(name = "scim.prov.mongo.uri", defaultValue = "mongodb://localhost:27017")
    String dbUrl;

    @ConfigProperty(name = "scim.prov.mongo.dbname", defaultValue = "testSCIM")
    String scimDbName;

    @ConfigProperty(name = "scim.prov.mongo.username", defaultValue = "UNDEFINED")
    String dbUser;

    @ConfigProperty(name = "scim.prov.mongo.password", defaultValue = "t0p-Secret")
    String dbPwd;

    private MongoClient mclient = null;

    public static String mapPathToReqUrl(URL baseUrl, String path) throws MalformedURLException {
        URL rUrl = new URL(baseUrl, path);
        return rUrl.toString();
    }

    /**
     * Updates a configuration hashmap with configured testing endpoints for MongoDB, OPA, etc. These
     * can be overridden using environment variables prefixed with "TEST_..." -- see static ENV values above.
     * If a value is already defined, the value is not updated.
     */
    public static void configTestEndpointsMap(Map<String,String> map) {
        String opa_uri = System.getenv(ENV_TEST_OPA_URL);
        if (opa_uri == null)
            opa_uri = DEF_TEST_OPA_URL;
        if (!map.containsKey("scim.opa.authz.url"))
            map.put("scim.opa.authz.url",opa_uri);

        String mongo_uri = System.getenv(ENV_TEST_MONGO_URI);
        if (mongo_uri == null)
            mongo_uri = DEF_TEST_MONGO_URI;
        if (!map.containsKey("scim.prov.mongo.uri"))
            map.put("scim.prov.mongo.uri",mongo_uri);

        if (!map.containsKey("scim.prov.mongo.dbname"))
            map.put("scim.prov.mongo.dbname",DEF_TEST_MONGO_DBNAME);

        String mongo_user = System.getenv(ENV_TEST_MONGO_USER);
        if (mongo_user == null)
            mongo_user = DEF_TEST_MONGO_USER;
        if (!map.containsKey("scim.prov.mongo.username"))
            map.put("scim.prov.mongo.username",mongo_user);

        String mongo_pass = System.getenv(ENV_TEST_MONGO_SECRET);
        if (mongo_pass == null)
            mongo_pass = DEF_TEST_MONGO_SECRET;
        if (!map.containsKey("scim.prov.mongo.password"))
            map.put("scim.prov.mongo.password",mongo_pass);
    }

    public static CloseableHttpResponse executeGet(URL baseUrl, String req) throws MalformedURLException {
        //if (req.startsWith("/"))
        req = mapPathToReqUrl(baseUrl, req);
        try {
            HttpUriRequest request = new HttpGet(req);
            return HttpClientBuilder.create().build().execute(request);
        } catch (IOException e) {
            fail("Failed request: " + req + "\n" + e.getLocalizedMessage(), e);
        }
        return null;
    }

    public static CloseableHttpResponse executeRequest(HttpUriRequest req) throws IOException {
        //if (req.startsWith("/"))

        return HttpClientBuilder.create().build().execute(req);

    }

    public void resetProvider(boolean eraseData) throws ScimException, BackendException, IOException {
        IScimProvider provider = handler.getProvider();
        if (provider instanceof MongoProvider)
            resetMongoDb((MongoProvider) provider);
        if (provider instanceof MemoryProvider)
            resetMemoryDb((MemoryProvider) provider, eraseData);
    }

    void resetMongoDb(MongoProvider mongoProvider) throws ScimException, BackendException, IOException {
        if (!dbUrl.contains("@") && dbUser != null) {
            logger.info("Connecting to Mongo using admin user: " + dbUser);
            try {
                String userInfo = dbUser + ":" + dbPwd;
                URI mUrl = new URI(dbUrl);
                URI newUrl = new URI(mUrl.getScheme() + "://" + userInfo + "@" + mUrl.getAuthority() + mUrl.getRawPath());
                dbUrl = newUrl.toString();
            } catch (URISyntaxException e) {
                logger.error("Received exception: " + e.getMessage(), e);
            }
        } else {
            if (!dbUrl.contains("@"))
                logger.warn("Attempting to connect to Mongo with unauthenticated connection.");
        }

        logger.warn("\t*** Resetting Mongo database [" + scimDbName + "] ***");
        if (mclient == null)
            mclient = MongoClients.create(dbUrl);
        MongoDatabase scimDb = mclient.getDatabase(scimDbName);

        // Shut the provider down
        if (mongoProvider.ready())
            mongoProvider.shutdown();
        smgr.resetConfig();

        // Reset the database

        scimDb.drop();
        mclient.close(); // gracefully close so the drop commits.
        mclient = null;

        // restart and reload
        smgr.init();
        mongoProvider.init();


    }

    void resetMemoryDb(MemoryProvider provider, boolean eraseData) throws ScimException, BackendException, IOException {
        logger.warn("\t*** Resetting Memory Provider Data [" + storeDir + "] ***");
        // Shut the provider down
        if (provider.ready())
            provider.shutdown();
        smgr.resetConfig();

        if (eraseData)
            resetMemDirectory();

        // restart and reload
        smgr.init();
        provider.init();

    }

    private void resetMemDirectory() {
        // Reset the memory provider
        logger.warn("\t*** Resetting Memory database files in " + storeDir + " ***");
        File memdir = new File(storeDir);
        File[] files = memdir.listFiles();
        if (files != null)
            for (File afile : files)
                afile.delete();

    }

    public ScimResource loadResource(String jsonfilepath, String container) {
        InputStream userStream;
        if (container == null)
            container = "Users";
        try {
            userStream = ConfigMgr.findClassLoaderResource(jsonfilepath);
            JsonNode node = JsonUtil.getJsonTree(userStream);
            return new ScimResource(smgr, node, container);
        } catch (ParseException | ScimException | IOException e) {
            fail("Error preparing operation for " + jsonfilepath + ": " + e.getMessage(), e);
        }
        return null;
    }
}
