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


import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimConfigEndpointTest {

    private final Logger logger = LoggerFactory.getLogger(ScimConfigEndpointTest.class);

    //ScimBootApplication app;

    //@Inject
    //private ScimV2Servlet servlet;

    @TestHTTPResource("/")
    URL baseUrl;

    @Inject
    @Resource(name = "ConfigMgr")
    ConfigMgr cfgMgr;

    private HttpResponse executeGet(String req) throws MalformedURLException {
       return TestUtils.executeGet(baseUrl,req);
    }

    /**
     * This test just checks that spring boot injection environment worked and the basic test
     * endpoint test works.
     */
    @Test
    public void a_basic_webTest() throws IOException {
        logger.info("========= SCIM Servlet Basic Test          =========");

        logger.debug("  Starting Web Test");

        assertThat(cfgMgr.getPort()).isNotEqualTo(0);

        //String name = servlet.getServletName();
        //logger.debug("Servlet name:\t"+servlet.getServletName());
        //ServletContext ctx = servlet.getServletContext();
        //logger.debug("Servlet Context Path:\t"+ctx.getContextPath());


        //We need a rest template to run

        HttpResponse resp = executeGet(ScimParams.PATH_SERV_PROV_CFG);
        assert resp != null;
        HttpEntity body = resp.getEntity();
        String res = EntityUtils.toString(body);

        assertThat(res)
                .as("Service provider config returned")
                .contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);

    }

    @Test
    public void b_SchemasEndpoint() throws IOException {
        logger.info("=========      Schemas Endpoint Test       =========");
        HttpResponse resp = executeGet("/Schemas");
        assert resp != null;
        HttpEntity body = resp.getEntity();

        String res = EntityUtils.toString(body);
        logger.debug("Response returned: \n"+res);

        assertThat(res).isNotNull();

        assertThat(res)
                .as("Is a List Response")
                .startsWith("{\n" +
                        "  \"schemas\" : [ \"urn:ietf:params:scim:api:messages:2.0:ListResponse\" ]");
        assertThat(res)
                .as("Contains correct items per page(7)")
                .contains("\"itemsPerPage\" : 7,");
        assertThat(res)
                .as("Contains correct total numbrt of results (7)")
                .contains("\"totalResults\" : 7,");
        assertThat(res)
                .as("Confirtm List Response Type")
                .contains(ListResponse.SCHEMA_LISTRESP);

    }

    @Test
    public void c_SchemasFilterTest() throws IOException {

        logger.debug("Fetching schema by id eq \"Service\" filter");
        String req = "/Schemas?filter=" + URLEncoder.encode("id eq \"Service\"", StandardCharsets.UTF_8);

        HttpResponse httpResponse = executeGet(req);
        assert httpResponse != null;
        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check filter on schema is forbidden per RFC7644 Sec 4, pg 73.")
                .isEqualTo(ScimResponse.ST_FORBIDDEN);

        logger.debug("Fetching schema by User identifier");
        req = "/Schemas/" + ScimParams.SCHEMA_SCHEMA_User;
        httpResponse = executeGet(req);
        assert httpResponse != null;

        String res;
        HttpEntity entity = httpResponse.getEntity();

        res = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        logger.debug("Response returned: \n"+res);

        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check for status response 200 OK")
                .isEqualTo(ScimResponse.ST_OK);

        assert res != null;
        assertThat(res)
                .as("Check that it is not a ListResponse")
                .doesNotContain(ScimParams.SCHEMA_API_ListResponse);
        assertThat(res)
                .as("Check the requested object returned")
                .contains(ScimParams.SCHEMA_SCHEMA_User);
    }

    @Test
    public void d_ServiceProviderConfigTest() throws IOException {
        logger.info("=========      ServiceProviderConfig Test  =========");
        String req = "/ServiceProviderConfig";

        HttpResponse resp = executeGet(req);
        assert resp != null;


        HttpEntity body = resp.getEntity();

        String res = EntityUtils.toString(body);

        logger.debug("ServiceProviderConfig res:\n" + res);
        assertThat(res)
                .as("ServiceProviderConfig does not contain ListResponse")
                .doesNotContain(ScimParams.SCHEMA_API_ListResponse);

        assertThat(res)
                .as("Has schemas ServiceProviderConfig")
                .contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);

        req = "/ServiceProviderConfig/test";
        HttpResponse httpResponse = executeGet(req);
        assert httpResponse != null;

        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check for not found response to /ServiceProviderConfigs/test")
                .isEqualTo(ScimResponse.ST_NOTFOUND);

        // This tests an incorrect request to ServiceProviderConfigs rather than ServiceProviderrConfig
        req = "/ServiceProviderConfigs?filter=" + URLEncoder.encode("patch.supported eq true", StandardCharsets.UTF_8);
        httpResponse = executeGet(req);
        assert httpResponse != null;
        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check incorrect endpoint returns NOT FOUND")
                .isEqualTo(ScimResponse.ST_NOTFOUND);

        // This tests that a filter can be executed against ServiceProviderConfig
        req = "/ServiceProviderConfig?filter=" + URLEncoder.encode("patch.supported eq true", StandardCharsets.UTF_8);
        httpResponse = executeGet(req);
        assert httpResponse != null;
        HttpEntity entity = httpResponse.getEntity();
        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check if a filter was accepted (should not be error 400")
                .isNotEqualTo(ScimResponse.ST_BAD_REQUEST);
        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check for normal 200 response to filtered SPC request")
                .isEqualByComparingTo(ScimResponse.ST_OK);

        res = EntityUtils.toString(entity, StandardCharsets.UTF_8);

        logger.debug("Filter reject result:\n" + res);
        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check for normal 200 response to filtered SPC request")
                .isEqualByComparingTo(ScimResponse.ST_OK);
        assertThat(res)
                .as("Check ServiceProviderConfig actually returned")
                .contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);
        assertThat(res)
                .as("Confirm patch supported response")
                .contains("patch");
        assertThat(res)
                .as("Check that SPC response is not a ListResponse")
                .contains(ScimResponse.SCHEMA_LISTRESP);

        //This should return an empty ListResponse (no match)
        req = "/ServiceProviderConfig?filter=" + URLEncoder.encode("patch.supported eq false", StandardCharsets.UTF_8);
        httpResponse = executeGet(req);
        assert httpResponse != null;
        entity = httpResponse.getEntity();

        assertThat(httpResponse.getStatusLine().getStatusCode())
                .as("Check for normal 200 response to filtered SPC request")
                .isEqualByComparingTo(ScimResponse.ST_OK);

        res = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        assertThat(res)
                .as("Check ServiceProviderConfig not returned")
                .doesNotContain(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);
        assertThat(res)
                .as("Check that SPC response is an empty ListResponse")
                .contains(ScimResponse.SCHEMA_LISTRESP);
        assertThat(res)
                .as("Contains correct items per page")
                .contains("\"totalResults\" : 0,");
    }

    @Test
    public void e_ResourceTypesTest() {
        logger.info("=========      ResourceTypes Endpoint Test =========");

        try {
            // Return all Resource Types
            String req = "/ResourceTypes";
            HttpResponse httpResponse = executeGet(req);
            assert httpResponse != null;
            assertThat(httpResponse.getStatusLine().getStatusCode())
                    .as("Check for OK response to ResourceTypes endpoint")
                    .isEqualTo(ScimResponse.ST_OK);

            String res = EntityUtils.toString(httpResponse.getEntity());

            logger.debug("ResourceTypes test:\n" + res);

            assertThat(res).isNotNull();

            assertThat(res)
                    .as("Contains correct items per page")
                    .contains("\"itemsPerPage\" : 5,");
            assertThat(res)
                    .as("Contains correct items per page")
                    .contains("\"totalResults\" : 5,");
            assertThat(res)
                    .as("Confirtm List Response Type")
                    .contains(ListResponse.SCHEMA_LISTRESP);

            // Perform a filtered search that should be forbidden
            req = "/" +
                    ScimParams.PATH_TYPE_RESOURCETYPE + "?filter=" +
                    URLEncoder.encode("name eq \"User\"", StandardCharsets.UTF_8);

            httpResponse = executeGet(req);
            assert httpResponse != null;
            assertThat(httpResponse.getStatusLine().getStatusCode())
                    .as("Check filter on ResourceTypes is forbidden per RFC7644 Sec 4, pg 73.")
                    .isEqualTo(ScimResponse.ST_FORBIDDEN);

            // Return a specific ResourceType
            req = "/ResourceTypes/User";
            httpResponse = executeGet(req);
            assert httpResponse != null;
            assertThat(httpResponse.getStatusLine().getStatusCode())
                    .as("Check for status response 200 OK")
                    .isEqualTo(ScimResponse.ST_OK);

            HttpEntity entity = httpResponse.getEntity();
            res = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            assertThat(res)
                    .as("Check that returned ResourceType is not a ListResponse")
                    .doesNotContain(ScimParams.SCHEMA_API_ListResponse);
            assertThat(res)
                    .as("Check the requested User resource type returned")
                    .contains("\"/Users\"");
        } catch (ParseException | IOException e) {
            fail("Failed retrieving resource type information:" + e.getLocalizedMessage(), e);
        }

    }
}
