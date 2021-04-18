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


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimGroupCRUDTest {
	
	private final static Logger logger = LoggerFactory.getLogger(ScimGroupCRUDTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

	@Inject
	TestUtils testUtils;

	@TestHTTPResource("/")
	URL baseUrl;
	
	private static String user1url = "", user2url = "", grpUrl = "";
	
	private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";
	/**
	 * This test actually resets and re-initializes the SCIM Mongo test database.
	 */
	@Test
	public void a_initializeMongo() {
	
		logger.info("========== Scim HTTP CRUD Test ==========");
		logger.info("\tA. Initializing test data");

		try {
			testUtils.resetProvider();
		} catch (ScimException | BackendException | IOException e) {
			Assertions.fail("Failed to reset provider: "+e.getMessage());
		}


	}
	/**
	 * This test checks that a JSON user can be parsed into a SCIM Resource
	 */
	@Test
	public void b_ScimAddUserTest() {
		
		logger.info("\tB1. Add two users...");

		try {

			InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);

			URL rUrl = new URL(baseUrl,"/Users");
			String req = rUrl.toString();
			
			
			HttpPost post = new HttpPost(req);
				
			InputStreamEntity reqEntity = new InputStreamEntity(
	        userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
			reqEntity.setChunked(false);
			post.setEntity(reqEntity);
		
			logger.debug("Executing test add for bjensen: "+post.getRequestLine());
			//logger.debug(EntityUtils.toString(reqEntity));
		
			HttpResponse resp = TestUtils.executeRequest(post);

			Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
			if (hloc == null || hloc.length == 0)
				fail("No HTTP Location header in create response");
			else {
				Header loc = hloc[0];
				user1url = loc.getValue();  // This will be used to retrieve the user later
			}
			assertThat(resp.getStatusLine().getStatusCode())
			.as("Create user response status of 201")
			.isEqualTo(ScimResponse.ST_CREATED);

			userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
			post = new HttpPost(req);
			reqEntity = new InputStreamEntity(
					userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
			reqEntity.setChunked(false);
			post.setEntity(reqEntity);
			resp = TestUtils.executeRequest(post);

			hloc = resp.getHeaders(HttpHeaders.LOCATION);
			if (hloc == null || hloc.length == 0)
				fail("No HTTP Location header in create response");
			else {
				Header loc = hloc[0];
				user2url = loc.getValue();  // This will be used to retrieve the user later
			}

			assertThat(resp.getStatusLine().getStatusCode())
					.as("Create user response status of 201")
					.isEqualTo(ScimResponse.ST_CREATED);
			
		} catch (IOException e) {
			Assertions.fail("Exception occured creating bjenson. "+e.getMessage(),e);
		}
	}

	private String memberObj(String ref) {
		String id = ref.substring(ref.lastIndexOf("/")+1);
		return "{ \"value\": \""+id+"\",\n"+
				"    \"$ref\": \""+ref+"\"}";
	}

	@Test
	public void c_createGroupTest() throws IOException {
		logger.info("\tC. Creating Group...");
		String jsonGroup = "{\n" +
				"     \"schemas\": [\"urn:ietf:params:scim:schemas:core:2.0:Group\"],\n" +
				"     \"id\": \"e9e30dba-f08f-4109-8486-d5c6a331660a\",\n" +
				"     \"displayName\": \"TEST Tour Guides\",\n" +
				"     \"members\": [\n";
		jsonGroup = jsonGroup + memberObj(user1url)+",\n"+memberObj(user2url)+"\n]}";

		String req = TestUtils.mapPathToReqUrl(baseUrl,"/Groups");

		HttpPost postGroup = new HttpPost(req);
		StringEntity body = new StringEntity(jsonGroup);
		body.setChunked(false);
		postGroup.setEntity(body);

		HttpResponse resp = TestUtils.executeRequest(postGroup);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Create user response status of 201")
				.isEqualTo(ScimResponse.ST_CREATED);
		Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
		if (hloc == null || hloc.length == 0)
			fail("No HTTP Location header in create response");
		else {
			Header loc = hloc[0];
			grpUrl = loc.getValue();  // This will be used to retrieve the user later
		}
	}

	@Test
	public void d_getGroupTest() throws IOException {
		logger.info("\tD. Get Group...");

		HttpResponse resp = TestUtils.executeGet(baseUrl,grpUrl);

		assert resp != null;
		assertThat(resp.getStatusLine().getStatusCode())
				.as("GET Group- Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);

		String body = EntityUtils.toString(resp.getEntity());

		assertThat(body)
				.as("Check that it is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);

		assertThat(body)
				.as("Is user bjensen url")
				.contains(user1url);

		assertThat(body)
				.as("Contains an extension value Tour Operations")
				.contains("Tour Guides");

		System.out.println("Entry retrieved:\n"+body);
	}

	@Test
	public void e_getUserTest() throws IOException {
		logger.info("\tE. Check Groups on User...");

		HttpResponse resp = TestUtils.executeGet(baseUrl,user1url);

		assert resp != null;
		assertThat(resp.getStatusLine().getStatusCode())
				.as("GET Group- Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);

		String body = EntityUtils.toString(resp.getEntity());

		assertThat(body)
				.as("contains dynamic url for Tour Guides")
				.contains(grpUrl);

		assertThat(body)
				.as("has displayname TEST Tour Guides")
				.contains("\"TEST Tour Guides\"");

		assertThat(body)
				.as("still has original group US Employees")
				.contains("\"US Employees\"");

		System.out.println("Entry retrieved:\n"+body);
	}

	@Test
	public void f_getUserFilterTest() throws IOException {
		logger.info("\tF. Search filter for groups on User...");


		HttpResponse resp = TestUtils.executeGet(baseUrl,user2url+"?filter="+ URLEncoder.encode("groups.$ref eq "+grpUrl, StandardCharsets.UTF_8));

		assert resp != null;
		assertThat(resp.getStatusLine().getStatusCode())
				.as("GET Group- Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);

		String body = EntityUtils.toString(resp.getEntity());

		assertThat(body)
				.as("contains dynamic url for Tour Guides")
				.contains(grpUrl);

		assertThat(body)
				.as("has displayname Tour Guides")
				.contains("\"Tour Guides\"");

		System.out.println("Entry retrieved:\n"+body);
	}



}
