package com.independentid.scim.test.events;


import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;

import static org.assertj.core.api.Assertions.fail;

@Singleton
@Priority(6)
public class TestUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @ConfigProperty(name = "scim.prov.memory.persist.dir", defaultValue = "./scimdata")
    String storeDir;

    @ConfigProperty(name="scim.prov.mongo.uri",defaultValue = "mongodb://localhost:27017")
    String dbUrl;

    @ConfigProperty(name="scim.prov.mongo.dbname",defaultValue = "testSCIM")
    String scimDbName;


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
