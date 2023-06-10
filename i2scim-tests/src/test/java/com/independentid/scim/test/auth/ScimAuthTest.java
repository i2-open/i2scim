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
package com.independentid.scim.test.auth;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.test.http.ScimUserCRUDTest;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimAuthTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimAuthTest {
	
	private final static Logger logger = LoggerFactory.getLogger(ScimUserCRUDTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

	public static String bearer;

	@Inject
	@Resource(name="ConfigMgr")
	ConfigMgr cmgr ;

	@Inject
	TestUtils testUtils;

	@ConfigProperty(name="smallrye.jwt.verify.key.location",defaultValue = "classpath:/certs/jwks-certs.json")
	String jwks;

	@TestHTTPResource("/")
	URL baseUrl;
	
	@Test
	public void a_certTest() throws IOException {

		assertThat(jwks)
				.as("JWKS file is not null")
				.isNotNull();

		InputStream str = ConfigMgr.findClassLoaderResource(jwks);

		assert str != null;
		assertThat(str.available())
			.as("Jwks file located")
			.isGreaterThan(0);
	}
	
	/**
	 * This attempts to retrieve configs using anonymous.
	 */
	@Test
	public void b_ScimGetConfigAnonTest() throws MalformedURLException {
		String req = TestUtils.mapPathToReqUrl(baseUrl, "/Schemas");
		
		logger.info("\n\n\tRetrieving /Schemas using anonymous: "+req);
		
		HttpUriRequest request = new HttpGet(req);

		// for this test, acis should allow anon access to /ServiceProviderConfig but require
		// authenticated access to /Schemas and /ResourceTypes
		try {
			HttpResponse resp = TestUtils.executeRequest(request);
			assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm anonymous request is unauthorized.")
				.isEqualTo(ScimResponse.ST_UNAUTHORIZED);

			req = TestUtils.mapPathToReqUrl(baseUrl, "/ServiceProviderConfig");
			request = new HttpGet(req);
			resp = TestUtils.executeRequest(request);
			assertThat(resp.getStatusLine().getStatusCode())
					.as("Confirm anonymous access to ServiceProviderConfig")
					.isEqualTo(ScimResponse.ST_OK);
			
		} catch (IOException e) {
			fail("Exception occured making anonymous GET request.",e);
		}
	}
	
	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void c_ScimGetUserBasicRootTest() throws MalformedURLException {
		String req = TestUtils.mapPathToReqUrl(baseUrl,
				"/Schemas");
		
		logger.info("\n\n\tRetrieving /Schemas using Root Basic Auth using: "+req);
		
		String cred = cmgr.getRootUser()+":"+cmgr.getRootPassword();
		HttpGet request = new HttpGet(req);
		String encoding = Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8));
		
		request.addHeader(HttpHeaders.AUTHORIZATION, "Basic "+encoding);
		
		try {
			HttpResponse resp = TestUtils.executeRequest(request);
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET /Schemas (Basic Auth) - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			HttpEntity entity = resp.getEntity();
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check that it is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);
			
		} catch (IOException e) {
			fail("Exception occured making GET request (Basic auth) for /Schemas",e);
		}
	}
	
	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void d_ScimGetUserJwtTest() throws MalformedURLException {
		bearer = testUtils.getAuthToken("admin",true);

		String req = TestUtils.mapPathToReqUrl(baseUrl,
				"/Schemas");
		
		logger.info("\n\n\tRetrieving Schemas using Bearer JWT: "+req);

		HttpGet request = new HttpGet(req);
		
		request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
		
		try {
			HttpResponse resp = TestUtils.executeRequest(request);

			HttpEntity entity = resp.getEntity();

			String body = EntityUtils.toString(entity);
			System.out.println("Response:\n"+body);
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET /Schemas (Bearer JWT Auth) - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			

			assertThat(body)
				.as("Check that it is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);

		} catch (IOException e) {
			fail("Exception occured making GET request (Bearer JWT auth) for /Schemas",e);
		}
	}

}
