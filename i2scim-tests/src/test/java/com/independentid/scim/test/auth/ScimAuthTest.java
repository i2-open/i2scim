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
package com.independentid.scim.test.auth;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.test.http.ScimUserCRUDTest;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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

import javax.annotation.Resource;
import javax.inject.Inject;
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
	// note: test token expires Sun Oct 17, 2021.
	public static String bearer = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJSLURla2xmOU5XSXpRMVVmRTVRNnY5UXRnVnNDQ1ROdE5iRUxnNXZjZ1J3In0.eyJleHAiOjE2MzQ0OTIzMDcsImlhdCI6MTYwMjk1NjMwNywianRpIjoiNWYyNDQ0ZGUtMDVlNi00MDFjLWIzMjYtZjc5YjJiMmZhNmZiIiwiaXNzIjoiaHR0cDovLzEwLjEuMTAuMTA5OjgxODAvYXV0aC9yZWFsbXMvZGV2Iiwic3ViIjoiNDA2MDQ0OWYtNDkxMy00MWM1LTkxYjAtYTRlZjY5MjYxZTY0IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoic2NpbS1zZXJ2ZXItY2xpZW50Iiwic2Vzc2lvbl9zdGF0ZSI6ImE2NGZkNjA3LWU1MzItNGQ0Ni04MGQ2LWE0NTUzYzRjZWQ1OCIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsibWFuYWdlciIsIm9mZmxpbmVfYWNjZXNzIl19LCJzY29wZSI6ImZ1bGwgbWFuYWdlciIsImNsaWVudElkIjoic2NpbS1zZXJ2ZXItY2xpZW50IiwiY2xpZW50SG9zdCI6IjEwLjEuMTAuMTE4IiwidXNlcl9uYW1lIjoic2VydmljZS1hY2NvdW50LXNjaW0tc2VydmVyLWNsaWVudCIsImNsaWVudEFkZHJlc3MiOiIxMC4xLjEwLjExOCJ9.Wouztkr7APb2_juPBhMtPbAqmFwQqsDQXYIQBeDpMuWnKGXZZMs17Rpzq8YnVSGfbfyrAduMAK2PAWnw8hxC4cGc0xEVS3lf-KcA5bUr4EnLcPVeQdEPsQ5eLrt_-BSPCQ8ere2fw6-Obv7FJ6aofAlT8LttWvEvkPzo2R0T0aZX8Oh7b15-icAVZ8ER0j7aFQ2k34dAq0Uwn58wakT6MA4qEFxze6GLeBuC4cAqNPYoOkUWTJxu1J_zLFDkpomt_zzx9u0Ig4asaErRyPj-ettElaGXMELZrNsaVbikCHgK7ujwMJDlEhUf8jxM8qwhCuf50-9ZydPAFA8Phj6FkQ";

	@Inject
	@Resource(name="ConfigMgr")
	ConfigMgr cmgr ;

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
