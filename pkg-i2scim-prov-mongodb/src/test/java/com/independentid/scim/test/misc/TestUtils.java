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
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.mongodb.client.MongoClient;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;

import static org.assertj.core.api.Assertions.fail;

@Singleton
public class TestUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    BackendHandler handler = BackendHandler.getInstance();

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
