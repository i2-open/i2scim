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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.fail;

public class TestUtils {

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
}
