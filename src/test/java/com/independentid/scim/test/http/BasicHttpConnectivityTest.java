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
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class BasicHttpConnectivityTest {

    private static final Logger logger = LoggerFactory.getLogger(BasicHttpConnectivityTest.class);

    @TestHTTPResource("/")
    URL baseUrl;

    @Inject
    ConfigMgr mgr;

    @Test
    public void a_basic_httpTest() throws IOException {
        logger.info("========= SCIM Unit Http Connectivity Test =========");

        logger.debug("\tStarting Simple HTTP Connectivity Test");

        //String name = servlet.getServletName();
        //logger.debug("Servlet name:\t"+servlet.getServletName());
        //ServletContext ctx = servlet.getServletContext();
        //logger.debug("Servlet Context Path:\t"+ctx.getContextPath());

        assertThat(mgr).isNotNull();

        //We need a rest template to run

        HttpResponse resp = TestUtils.executeGet(baseUrl,
                "/test");
        assert resp != null;
        HttpEntity body = resp.getEntity();
        String res = EntityUtils.toString(body);

        assertThat(res)
                .as("Default test endpoint works")
                .isEqualTo("Hello Tester!");

    }
}
