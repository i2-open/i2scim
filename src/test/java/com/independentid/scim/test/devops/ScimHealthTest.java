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

package com.independentid.scim.test.devops;


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.SchemaManager;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimDevOpsTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimHealthTest {
	
	private final static Logger logger = LoggerFactory.getLogger(ScimHealthTest.class);

	public static String bearer = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJSLURla2xmOU5XSXpRMVVmRTVRNnY5UXRnVnNDQ1ROdE5iRUxnNXZjZ1J3In0.eyJleHAiOjE2MzQ0OTIzMDcsImlhdCI6MTYwMjk1NjMwNywianRpIjoiNWYyNDQ0ZGUtMDVlNi00MDFjLWIzMjYtZjc5YjJiMmZhNmZiIiwiaXNzIjoiaHR0cDovLzEwLjEuMTAuMTA5OjgxODAvYXV0aC9yZWFsbXMvZGV2Iiwic3ViIjoiNDA2MDQ0OWYtNDkxMy00MWM1LTkxYjAtYTRlZjY5MjYxZTY0IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoic2NpbS1zZXJ2ZXItY2xpZW50Iiwic2Vzc2lvbl9zdGF0ZSI6ImE2NGZkNjA3LWU1MzItNGQ0Ni04MGQ2LWE0NTUzYzRjZWQ1OCIsImFjciI6IjEiLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsibWFuYWdlciIsIm9mZmxpbmVfYWNjZXNzIl19LCJzY29wZSI6ImZ1bGwgbWFuYWdlciIsImNsaWVudElkIjoic2NpbS1zZXJ2ZXItY2xpZW50IiwiY2xpZW50SG9zdCI6IjEwLjEuMTAuMTE4IiwidXNlcl9uYW1lIjoic2VydmljZS1hY2NvdW50LXNjaW0tc2VydmVyLWNsaWVudCIsImNsaWVudEFkZHJlc3MiOiIxMC4xLjEwLjExOCJ9.Wouztkr7APb2_juPBhMtPbAqmFwQqsDQXYIQBeDpMuWnKGXZZMs17Rpzq8YnVSGfbfyrAduMAK2PAWnw8hxC4cGc0xEVS3lf-KcA5bUr4EnLcPVeQdEPsQ5eLrt_-BSPCQ8ere2fw6-Obv7FJ6aofAlT8LttWvEvkPzo2R0T0aZX8Oh7b15-icAVZ8ER0j7aFQ2k34dAq0Uwn58wakT6MA4qEFxze6GLeBuC4cAqNPYoOkUWTJxu1J_zLFDkpomt_zzx9u0Ig4asaErRyPj-ettElaGXMELZrNsaVbikCHgK7ujwMJDlEhUf8jxM8qwhCuf50-9ZydPAFA8Phj6FkQ";

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	@Inject
	BackendHandler handler;
	
	@ConfigProperty(name="scim.mongodb.uri",defaultValue = "mongodb://localhost:27017")
	String dbUrl;

	@ConfigProperty(name="scim.mongodb.dbname",defaultValue = "testSCIM")
	String scimDbName;
	
	private MongoClient mclient = null;

	@TestHTTPResource("/")
	URL baseUrl;
	
	//private static String user1url = "";
	
	private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";


	private static String bJensonUrl = null;
	private static String jSmithUrl = null;

	static CloseableHttpClient htclient ;
	
	private synchronized CloseableHttpResponse execute(HttpUriRequest req) throws IOException {
		return htclient.execute(req);
	}

	@BeforeAll
	public static void init() {
		htclient = HttpClients.createDefault();
	}

	@AfterAll
	public static void shutdown() throws IOException {
		htclient.close();
	}
	/**
	 * This test actually resets and re-initializes the SCIM Mongo test database.
	 */
	@Test
	public void a_initializeMongo() {
	
		logger.info("========== Scim Mongo CRUD Test ==========");
		logger.info("\tA. Initializing test database: "+scimDbName);
		
		if (mclient == null)
			mclient = MongoClients.create(dbUrl);


		MongoDatabase scimDb = mclient.getDatabase(scimDbName);
		
		scimDb.drop();
		
		try {
			handler.getProvider().syncConfig(smgr.getSchemas(), smgr.getResourceTypes());
		} catch (IOException | BackendException e) {
			fail("Failed to initialize test Mongo DB: "+scimDbName);
		}
		
	}

	@Test
	public void b1_initialHealthCheckJwt() throws IOException {
		URL rUrl = new URL(baseUrl,"/health");
		HttpGet get = new HttpGet(rUrl.toString());
		get.addHeader(HttpHeaders.AUTHORIZATION, bearer);

		CloseableHttpResponse resp = execute(get);

		assertThat(resp.getStatusLine().getStatusCode())
				.as("Check health response received ok")
				.isEqualTo(ScimResponse.ST_OK);

		HttpEntity entity = resp.getEntity();

		String body = EntityUtils.toString(entity);
		logger.debug("Health: \n"+body);
		assertThat(body)
				.as("Check that server is up")
				.contains("\"status\": \"UP\"");
		assertThat(body)
				.as("Provider is ready")
				.contains("\"scim.provider.ready\": true");
	}

	@Test
	public void b2_initialHealthCheckAnon() throws IOException {
		URL rUrl = new URL(baseUrl,"/health");
		HttpGet get = new HttpGet(rUrl.toString());
		//get.addHeader(HttpHeaders.AUTHORIZATION, bearer);

		CloseableHttpResponse resp = execute(get);

		assertThat(resp.getStatusLine().getStatusCode())
				.as("Check health response received ok")
				.isEqualTo(ScimResponse.ST_UNAUTHORIZED);

	}

	@Test
	public void c1_addUserTest_JWTadmin() throws IOException {

		// Perform add with JWT bearer authorization
		logger.info("B2. Attempting add bjensen as with JWT Bearer with role admin (SHOULD SUCCEED)");

		File user1File = ConfigMgr.findClassLoaderResource(testUserFile1);

		assert user1File != null;

		InputStream userStream ;

		URL rUrl = new URL(baseUrl,"/Users");
		String req = rUrl.toString();

		HttpPost post = new HttpPost(req);
		userStream = new FileInputStream(user1File);
		InputStreamEntity reqEntity = new InputStreamEntity(
				userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
		reqEntity.setChunked(false);
		post.setEntity(reqEntity);
		post.addHeader(HttpHeaders.AUTHORIZATION, bearer);

		CloseableHttpResponse resp = execute(post);

		logger.debug("Response is: "+resp.getStatusLine());
		String body = EntityUtils.toString(resp.getEntity());
		logger.debug("Body:\n"+body);

		Header[] heads = resp.getAllHeaders();
		for (Header head : heads) {
			logger.debug(head.getName() + "\t" + head.getValue());
		}

		Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
		if (hloc == null || hloc.length == 0)
			fail("No HTTP Location header in create response");
		else {
			Header loc = hloc[0];
			assertThat(loc).isNotNull();
			assertThat(loc.getValue())
					.as("Created object URL created in users endpoint")
					.contains("/Users/");
			bJensonUrl = loc.getValue();  // This will be used to retrieve the user later
		}


		assertThat(resp.getStatusLine().getStatusCode())
				.as("Create user response status of 201")
				.isEqualTo(ScimResponse.ST_CREATED);

		assertThat(body)
				.as("Check that it is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);

		assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");

		// Check that the extension attributes were parsed and returned
		assertThat(body)
				.as("Contains the correct extension")
				.contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
		assertThat(body)
				.as("Contains an extension value Tour Operations")
				.contains("Tour Operations");

		resp.close();

	}

	@Test
	public void c2_addJSmith_JWTadmin() throws IOException {
		/*  Add User JSmith */
		logger.info("B4. Add another User JSmith");

		InputStream userStream;

		URL rUrl = new URL(baseUrl,"/Users");
		String req = rUrl.toString();
		File user2File = ConfigMgr.findClassLoaderResource(testUserFile2);

		assert user2File != null;
		userStream = new FileInputStream(user2File);
		HttpPost post = new HttpPost(req);
		post.addHeader(HttpHeaders.AUTHORIZATION, bearer);
		InputStreamEntity reqEntity = new InputStreamEntity(
				userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
		reqEntity.setChunked(false);
		post.setEntity(reqEntity);
		CloseableHttpResponse resp = execute(post);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Check JSmith added")
				.isEqualTo(ScimResponse.ST_CREATED);
		Header[] headers = resp.getHeaders(HttpHeaders.LOCATION);
		if (headers.length > 0)
			jSmithUrl = headers[0].getValue();
		else
			fail("Missing location in creation response for JSmith");
		resp.close();
	}

	@Test
	public void d_metricsCheckJwt() throws IOException {
		URL rUrl = new URL(baseUrl,"/metrics/base");
		HttpGet get = new HttpGet(rUrl.toString());
		get.addHeader(HttpHeaders.AUTHORIZATION, bearer);
		get.addHeader(HttpHeaders.ACCEPT, "application/json");
		CloseableHttpResponse resp = execute(get);
		HttpEntity entity = resp.getEntity();

		String body = EntityUtils.toString(entity);
		logger.info("/metrics/base\n"+body);

		assertThat(resp.getStatusLine().getStatusCode())
				.as("Check health response received ok")
				.isEqualTo(ScimResponse.ST_OK);

		resp.close();

		rUrl = new URL(baseUrl,"/metrics/application");
		get = new HttpGet(rUrl.toString());
		get.addHeader(HttpHeaders.AUTHORIZATION, bearer);
		get.addHeader(HttpHeaders.ACCEPT, "application/json");
		resp = execute(get);
		body = EntityUtils.toString(resp.getEntity());
		logger.info("/metrics/application\n"+body);
		assertThat(body)
				.as("Confirm 2 create operations")
				.contains("\"com.independentid.scim.server.ScimV2Servlet.scim.ops.create.count\": 2,");
		resp.close();


		rUrl = new URL(baseUrl,"/metrics");
		get = new HttpGet(rUrl.toString());
		get.addHeader(HttpHeaders.AUTHORIZATION, bearer);
		get.addHeader(HttpHeaders.ACCEPT, "application/json");
		resp = execute(get);
		body = EntityUtils.toString(resp.getEntity());
		logger.info("/metrics\n"+body);
		resp.close();

	}


}