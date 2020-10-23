/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2020 Phillip Hunt, All Rights Reserved                        *
 *                                                                    *
 *  Confidential and Proprietary                                      *
 *                                                                    *
 *  This unpublished source code may not be distributed outside       *
 *  “Independent Identity Org”. without express written permission of *
 *  Phillip Hunt.                                                     *
 *                                                                    *
 *  People at companies that have signed necessary non-disclosure     *
 *  agreements may only distribute to others in the company that are  *
 *  bound by the same confidentiality agreement and distribution is   *
 *  subject to the terms of such agreement.                           *
 **********************************************************************/
package com.independentid.scim.test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.annotation.Resource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimBootApplication;


@ActiveProfiles("testing")
@RunWith(SpringJUnit4ClassRunner.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ScimBootApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ConfigMgr.class)
@TestMethodOrder(Alphanumeric.class)
@TestPropertySource(properties = {
		"scim.mongodb.test=true",
		"scim.mongodb.dbname=testCrudSCIM",
		"scim.security.enable=true",
		"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=classpath:/certs/jwks-certs.json"
})
public class ScimAuthTest {
	
	private static Logger logger = LoggerFactory.getLogger(ScimUserCRUDTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";
	public static String bearer = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJSLURla2xmOU5XSXpRMVVmRTVRNnY5UXRnVnNDQ1ROdE5iRUxnNXZjZ1J3In0.eyJleHAiOjE2MzQ0OTIzMDcsImlhdCI6MTYwMjk1NjMwNywianRpIjoiNWYyNDQ0ZGUtMDVlNi00MDFjLWIzMjYtZjc5YjJiMmZhNmZiIiwiaXNzIjoiaHR0cDovLzEwLjEuMTAuMTA5OjgxODAvYXV0aC9yZWFsbXMvZGV2Iiwic3ViIjoiNDA2MDQ0OWYtNDkxMy00MWM1LTkxYjAtYTRlZjY5MjYxZTY0IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoic2NpbS1zZXJ2ZXItY2xpZW50Iiwic2Vzc2lvbl9zdGF0ZSI6ImE2NGZkNjA3LWU1MzItNGQ0Ni04MGQ2LWE0NTUzYzRjZWQ1OCIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsibWFuYWdlciIsIm9mZmxpbmVfYWNjZXNzIl19LCJzY29wZSI6ImZ1bGwgbWFuYWdlciIsImNsaWVudElkIjoic2NpbS1zZXJ2ZXItY2xpZW50IiwiY2xpZW50SG9zdCI6IjEwLjEuMTAuMTE4IiwidXNlcl9uYW1lIjoic2VydmljZS1hY2NvdW50LXNjaW0tc2VydmVyLWNsaWVudCIsImNsaWVudEFkZHJlc3MiOiIxMC4xLjEwLjExOCJ9.Wouztkr7APb2_juPBhMtPbAqmFwQqsDQXYIQBeDpMuWnKGXZZMs17Rpzq8YnVSGfbfyrAduMAK2PAWnw8hxC4cGc0xEVS3lf-KcA5bUr4EnLcPVeQdEPsQ5eLrt_-BSPCQ8ere2fw6-Obv7FJ6aofAlT8LttWvEvkPzo2R0T0aZX8Oh7b15-icAVZ8ER0j7aFQ2k34dAq0Uwn58wakT6MA4qEFxze6GLeBuC4cAqNPYoOkUWTJxu1J_zLFDkpomt_zzx9u0Ig4asaErRyPj-ettElaGXMELZrNsaVbikCHgK7ujwMJDlEhUf8jxM8qwhCuf50-9ZydPAFA8Phj6FkQ";
	
	@Autowired
	ScimBootApplication app;
	
	@Autowired
	ResourceLoader resourceloader;
	
	@Autowired
	ConfigMgr cfg;

	@Resource(name="ConfigMgr")
	private ConfigMgr cmgr ;
	
	@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
	String jwks;
	
	@LocalServerPort
	private int port;
	
	@Test
	public void a_certTest() {
		org.springframework.core.io.Resource res = resourceloader.getResource(jwks); 
		
		assertThat(res.exists())
			.as("Jwks file located")
			.isTrue();
	}
	
	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void b_ScimGetUserAnonTest() {
		String req = "http://localhost:"+port+"/Schemas";
		
		logger.info("\n\n\tRetrieving /Schemas using anonymous: "+req);
		
		CloseableHttpClient client = HttpClients.createDefault();
		
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm anonymous request is unauthorized.")
				.isEqualTo(ScimResponse.ST_UNAUTHORIZED); 
			
		} catch (IOException e) {
			fail("Exception occured making anonymous GET request.",e);
		}
	}
	
	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void c_ScimGetUserBasicTest() {
		String req = "http://localhost:"+port+"/Schemas";
		
		logger.info("\n\n]rRetrieving /Schemas using Basic Auth using: "+req);
		
		CloseableHttpClient client = HttpClients.createDefault();
		
		String cred = cfg.getRootUser()+":"+cfg.getRootPassword();
		HttpUriRequest request = new HttpGet(req);
		String encoding = Base64.getEncoder().encodeToString(cred.getBytes(StandardCharsets.UTF_8));
		
		request.addHeader(HttpHeaders.AUTHORIZATION, "Basic "+encoding);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			
			
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET /Schemas (Basic Auth) - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			HttpEntity entity = resp.getEntity();
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check that it is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);
			
			resp.close();
			
		} catch (IOException e) {
			fail("Exception occured making GET request (Basic auth) for /Schemas",e);
		}
	}
	
	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void d_ScimGetUserJwtTest() {
		String req = "http://localhost:"+port+"/Schemas";
		
		logger.info("\n\n\tRetrieving Schemas using Bearer JWT: "+req);
		
		CloseableHttpClient client = HttpClients.createDefault();
		
		
		HttpUriRequest request = new HttpGet(req);
		
		request.addHeader(HttpHeaders.AUTHORIZATION, bearer);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			
			
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET /Schemas (Bearer JWT Auth) - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			HttpEntity entity = resp.getEntity();
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check that it is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);
			
			resp.close();
			
		} catch (IOException e) {
			fail("Exception occured making GET request (Bearer JWT auth) for /Schemas",e);
		}
	}

}
