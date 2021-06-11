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
import org.apache.http.HttpResponse;
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

import static org.assertj.core.api.Assertions.fail;

@Singleton
public class TestUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    @Inject
    BackendHandler handler;

    @Inject
    SchemaManager smgr;

    @ConfigProperty(name = "scim.prov.memory.persist.dir", defaultValue = "./scimdata")
    String storeDir;

    @ConfigProperty(name="scim.prov.mongo.uri",defaultValue = "mongodb://localhost:27017")
    String dbUrl;

    @ConfigProperty(name="scim.prov.mongo.dbname",defaultValue = "testSCIM")
    String scimDbName;

    @ConfigProperty(name = "scim.prov.mongo.username",defaultValue = "UNDEFINED")
    String dbUser;

    @ConfigProperty(name = "scim.prov.mongo.password",defaultValue = "t0p-Secret")
    String dbPwd;

    private MongoClient mclient = null;

    public static String mapPathToReqUrl(URL baseUrl, String path) throws MalformedURLException {
        URL rUrl = new URL(baseUrl,path);
        return rUrl.toString();
    }

    public static HttpResponse executeGet(URL baseUrl, String req) throws MalformedURLException {
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

    public static HttpResponse executeRequest(HttpUriRequest req) throws IOException {
        //if (req.startsWith("/"))

        return HttpClientBuilder.create().build().execute(req);

    }

    public void resetProvider() throws ScimException, BackendException, IOException {
        IScimProvider provider = handler.getProvider();
        if (provider instanceof MongoProvider)
            resetMongoDb((MongoProvider) provider);
        if (provider instanceof MemoryProvider)
            resetMemoryDb((MemoryProvider) provider);
    }

    void resetMongoDb(MongoProvider mongoProvider) throws ScimException, BackendException, IOException {
        if (!dbUrl.contains("@") && dbUser != null) {
            logger.info("Connecting to Mongo using admin user: "+dbUser);
            try {
                String userInfo = dbUser+":"+dbPwd;
                URI mUrl = new URI(dbUrl);
                URI newUrl = new URI(mUrl.getScheme()+"://"+userInfo+"@"+mUrl.getAuthority()+ mUrl.getRawPath());
                dbUrl = newUrl.toString();
            } catch (URISyntaxException e) {
                logger.error("Received exception: "+e.getMessage(),e);
            }
        } else {
            if (!dbUrl.contains("@"))
                logger.warn("Attempting to connect to Mongo with unauthenticated connection.");
        }

        logger.warn("\t*** Resetting Mongo database ["+scimDbName+"] ***");
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

    void resetMemoryDb(MemoryProvider provider) throws ScimException, BackendException, IOException {
        logger.warn("\t*** Resetting Memory Provider Data ["+storeDir+"] ***");
        // Shut the provider down
        if (provider.ready())
            provider.shutdown();
        smgr.resetConfig();

        resetMemDirectory();

        // restart and reload
        smgr.init();
        provider.init();

    }

    private void resetMemDirectory() {
        // Reset the memory provider
        logger.warn("\t*** Resetting Memory database files in "+storeDir+" ***");
        File memdir = new File (storeDir);
        File[] files = memdir.listFiles();
        if (files != null)
            for (File afile: files)
                afile.delete();

    }

    public ScimResource loadResource(String jsonfilepath,String container) {
        InputStream userStream;
        if (container == null)
            container = "Users";
        try {
            userStream = ConfigMgr.findClassLoaderResource(jsonfilepath);
            JsonNode node = JsonUtil.getJsonTree(userStream);
            return new ScimResource(smgr, node, container);
        } catch (ParseException | ScimException | IOException e) {
            fail("Error preparing operation for "+jsonfilepath+": "+e.getMessage(),e);
        }
        return null;
    }
}
