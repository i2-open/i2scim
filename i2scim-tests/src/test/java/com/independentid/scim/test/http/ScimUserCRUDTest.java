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

package com.independentid.scim.test.http;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ExtensionValues;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimUserCRUDTest {
	
	private final static Logger logger = LoggerFactory.getLogger(ScimUserCRUDTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	@Inject
    TestUtils testUtils;

	@TestHTTPResource("/")
	URL baseUrl;
	
	private static String user1url,user2url = "";
	
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

	private CloseableHttpResponse addUser(CloseableHttpClient client, String file) throws IOException {
		InputStream userStream = ConfigMgr.findClassLoaderResource(file);

		URL rUrl = new URL(baseUrl,"/Users");
		String req = rUrl.toString();


		HttpPost post = new HttpPost(req);

		InputStreamEntity reqEntity = new InputStreamEntity(
				userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
		reqEntity.setChunked(false);
		post.setEntity(reqEntity);

		logger.debug("Executing test add for bjensen: "+post.getRequestLine());
		//logger.debug(EntityUtils.toString(reqEntity));

		CloseableHttpResponse resp = client.execute(post);

		return resp;

	}

	/**
	 * This test checks that a JSON user can be parsed into a SCIM Resource
	 */
	@Test
	public void b_ScimAddUserTest() {
		
		logger.info("\tB1. Add User BJensen...");
		CloseableHttpClient client = HttpClients.createDefault();

		try {

			CloseableHttpResponse resp = addUser(client,testUserFile1);
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
				user1url = loc.getValue();  // This will be used to retrieve the user later
			}

			logger.debug("Response is: "+resp.getStatusLine());
			String body = EntityUtils.toString(resp.getEntity());
			logger.debug("Body:\n"+body);
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

			logger.info("\tB2. Attempt to add User BJensen again (uniquenes test)...");
			// Attempt to repeat the operation. It should fail due to non-unique username match

			resp = addUser(client,testUserFile1);
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm error 400 occurred (uniqueness)")
				.isEqualTo(ScimResponse.ST_BAD_REQUEST);
			body = EntityUtils.toString(resp.getEntity());
			assertThat(body)
				.as("Is a uniqueness error")
				.contains(ScimResponse.ERR_TYPE_UNIQUENESS);

			resp.close();

			// Add the JSmith entry...this will be needed for sort test.
			logger.info("\tB3. Adding second user JSmith...");
			resp = addUser(client,testUserFile2);
			assertThat(resp.getStatusLine().getStatusCode())
					.as("Create JSmith user response expected status 201")
					.isEqualTo(ScimResponse.ST_CREATED);
			hloc = resp.getHeaders(HttpHeaders.LOCATION);
			if (hloc == null || hloc.length == 0)
				fail("No HTTP Location header in create response");
			else {
				Header loc = hloc[0];
				assertThat(loc).isNotNull();
				assertThat(loc.getValue())
						.as("Created object URL created in users endpoint")
						.contains("/Users/");
				user2url = loc.getValue();  // This will be used to retrieve the user later
			}

		} catch (IOException e) {
			Assertions.fail("Exception occured creating bjenson. "+e.getMessage(),e);
		} finally {
			try {
				client.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}				
	}

	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void c_ScimGetUserTest() throws MalformedURLException {
		String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);
		
		logger.info("\tC. Retrieving user from backend using: "+req);
		
		CloseableHttpClient client = HttpClients.createDefault();
		
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
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
			System.out.println("Entry retrieved:\n"+body);
			
			// Check that the result can be parsed as a SCIM object
			JsonNode jres = JsonUtil.getJsonTree(body);
			ScimResource res = new ScimResource(smgr,jres, "Users");
			ExtensionValues ext = res.getExtensionValues("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(ext)
				.as("Check the enterprise user extension present")
				.isNotNull();
			StringValue val = (StringValue) ext.getValue("division");
			String value = val.toString();
			assertThat(value)
				.as("Check value of division is 'Theme Park'.")
				.isEqualTo("Theme Park");		
			
			resp.close();
			
		} catch (IOException | ParseException | ScimException e) {
			fail("Exception occured making GET request for bjensen",e);
		}
	}
	
	/**
	 * This test tries to search for the previously created user by searching on filter name
	 */
	@Test
	public void d1_ScimSearchUserTest() throws MalformedURLException {
		
		logger.info("\tD1. Search using GET for user from backend with filter=UserName eq bjensen@example.com");
		CloseableHttpClient client = HttpClients.createDefault();

		String req = TestUtils.mapPathToReqUrl(baseUrl,
				"/Users?filter="+URLEncoder.encode("UserName eq bjensen@example.com",StandardCharsets.UTF_8));
		
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check query response is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			logger.debug("Entry retrieved:\n"+body);
			
			// Check that the extension attributes were parsed and returned
			assertThat(body)
				.as("Contains the correct extension")
				.contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(body)
				.as("Contains an extension value Tour Operations")
				.contains("Tour Operations");
			
			// Check that the result can be parsed as a SCIM object
			JsonNode jres = JsonUtil.getJsonTree(body);
			jres = jres.get("Resources").get(0);
			ScimResource res = new ScimResource(smgr,jres, "Users");
			
			ExtensionValues ext = res.getExtensionValues("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(ext)
				.as("Check the enterprise user extension present")
				.isNotNull();
			StringValue val = (StringValue) ext.getValue("division");
			assertThat(val.toString())
				.as("Check value of division is 'Theme Park'.")
				.isEqualTo("Theme Park");			
				
			resp.close();
			
		} catch (IOException | ParseException | ScimException e) {
			fail("Exception occured making GET filter request for bjensen",e);
		}
	}
	
	/**
	 * This test searches for the previously created user by searching on filter name and uses POST
	 */
	@Test
	public void d2_ScimSearchUserTest() throws MalformedURLException {
		
		logger.info("\tD2. POST Search user from backend with filter=UserName eq bjensen@example.com");
		CloseableHttpClient client = HttpClients.createDefault();
		
		String req = TestUtils.mapPathToReqUrl(baseUrl,
				"/Users/.search");
		logger.info("\t\t"+req);
		//?filter="+URLEncoder.encode("UserName eq bjensen@example.com",StandardCharsets.UTF_8);
		
		HttpPost request = new HttpPost(req);
		request.setHeader("Content-type",ScimParams.SCIM_MIME_TYPE);
		request.setHeader("Accept",ScimParams.SCIM_MIME_TYPE);
		try {
			StringWriter writer = new StringWriter();
			JsonGenerator gen = JsonUtil.getGenerator(writer, true);
			
			gen.writeStartObject();
			gen.writeArrayFieldStart("schemas");
			gen.writeString(ScimParams.SCHEMA_API_SearchRequest);
			gen.writeEndArray();
						
			gen.writeStringField("filter", "UserName eq bjensen@example.com");
			gen.writeArrayFieldStart("attributes");
			gen.writeString("userName");
			gen.writeString("name");
			gen.writeEndArray();
			
			gen.writeEndObject();
			gen.close();
			writer.close();
			
			StringEntity sEntity = new StringEntity(writer.toString(),ContentType.create(ScimParams.SCIM_MIME_TYPE));
			
			request.setEntity(sEntity);
			
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
		
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check query response is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("givenName sub attribute of name is present")
				.contains("\"givenName\"");
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			logger.debug("Entry retrieved:\n"+body);
					
				
			resp.close();
			
		} catch (IOException e) {
			fail("Exception occured making POST Search filter request for bjensen",e);
		}
	}
	
	@Test
	public void e_ScimSearchValPathUserTest() throws MalformedURLException {
		
		logger.info("\tE. Searching user from backend with valuePath filter");
		CloseableHttpClient client = HttpClients.createDefault();
		
		String req = TestUtils.mapPathToReqUrl(baseUrl,
				"/Users?filter="+URLEncoder.encode("UserName eq bjensen@example.com and addresses[country eq \"USA\" and type eq \"home\"]",StandardCharsets.UTF_8));
		logger.info("\t\t"+req);
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check query response is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			logger.debug("Entry retrieved:\n"+body);
			
			// Check that the extension attributes were parsed and returned
			assertThat(body)
				.as("Contains the correct extension")
				.contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(body)
				.as("Contains an extension value Tour Operations")
				.contains("Tour Operations");
			
			// Check that the result can be parsed as a SCIM object
			JsonNode jres = JsonUtil.getJsonTree(body);
			jres = jres.get("Resources").get(0);
			ScimResource res = new ScimResource(smgr,jres, "Users");
			
			ExtensionValues ext = res.getExtensionValues("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(ext)
				.as("Check the enterprise user extension present")
				.isNotNull();
			StringValue val = (StringValue) ext.getValue("division");
			assertThat(val.toString())
				.as("Check value of division is 'Theme Park'.")
				.isEqualTo("Theme Park");			
				
			resp.close();
			
		} catch (IOException | ParseException | ScimException e) {
			fail("Exception occured making GET filter request for bjensen",e);
		}
	}

	@Test
	public void f_sortandPagingTests() throws IOException {
		logger.info("\tF1. Performing sort by attribute test - ascending");
		String req = "/Users?sortBy=name.givenName";
		logger.info("\t\tGET "+req);

		HttpResponse resp = TestUtils.executeGet(baseUrl,req);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm success returned as status OK")
				.isEqualTo(ScimResponse.ST_OK);
		String body = EntityUtils.toString(resp.getEntity());

		int smithIndex = body.indexOf("Jim");
		int jansenIndex = body.indexOf("Barb");

		assertThat(smithIndex)
				.as("Smith is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Jansen is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Barbara comes before Jim")
				.isLessThan(smithIndex);

		logger.info("\tF2. Performing sort by attribute test - descending");
		req = "/Users?sortBy=name.givenName&sortOrder=descend";
		logger.info("\t\tGET "+req);
		resp = TestUtils.executeGet(baseUrl,req);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm success returned as status OK")
				.isEqualTo(ScimResponse.ST_OK);
		body = EntityUtils.toString(resp.getEntity());

		smithIndex = body.indexOf("Jim");
		jansenIndex = body.indexOf("Barb");
		assertThat(smithIndex)
				.as("Smith is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Jansen is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Barbara comes after Jim")
				.isGreaterThan(smithIndex);

		logger.info("\tF3. Performing sort by multiple attribute test - ascending");
		req = "/Users?sortBy=title,name.familyName&sortOrder=asc";
		logger.info("\t\tGET "+req);
		resp = TestUtils.executeGet(baseUrl,req);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm success returned as status OK")
				.isEqualTo(ScimResponse.ST_OK);
		body = EntityUtils.toString(resp.getEntity());

		smithIndex = body.indexOf("Jim");
		jansenIndex = body.indexOf("Barb");
		assertThat(smithIndex)
				.as("Smith is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Jansen is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Barbara comes before Jim")
				.isLessThan(smithIndex);

		logger.info("\tF4. Performing sort by multiple attribute test - descending");
		req = "/Users?sortBy=title,name.familyName&sortOrder=descending";
		logger.info("\t\tGET "+req);
		resp = TestUtils.executeGet(baseUrl,req);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm success returned as status OK")
				.isEqualTo(ScimResponse.ST_OK);
		body = EntityUtils.toString(resp.getEntity());

		smithIndex = body.indexOf("Jim");
		jansenIndex = body.indexOf("Barb");
		assertThat(smithIndex)
				.as("Smith is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Jansen is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Barbara comes before Jim")
				.isGreaterThan(smithIndex);

		logger.info("\tF5. Retrieving 1st page of result with pagesize of 1");
		req = "/Users?sortBy=title,name.familyName&sortOrder=descending&startIndex=1&count=1";
		logger.info("\t\tGET "+req);
		resp = TestUtils.executeGet(baseUrl,req);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm success returned as status OK")
				.isEqualTo(ScimResponse.ST_OK);
		body = EntityUtils.toString(resp.getEntity());

		smithIndex = body.indexOf("Jim");
		jansenIndex = body.indexOf("Barb");
		assertThat(smithIndex)
				.as("Smith is not present")
				.isEqualTo(-1);
		assertThat(jansenIndex)
				.as("Jansen is present")
				.isGreaterThan(0);

		logger.info("\tF5. Retrieving 2st page of result with pagesize of 1");
		req = "/Users?sortBy=title,name.familyName&sortOrder=descending&startIndex=2&count=1";
		logger.info("\t\tGET "+req);
		resp = TestUtils.executeGet(baseUrl,req);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm success returned as status OK")
				.isEqualTo(ScimResponse.ST_OK);
		body = EntityUtils.toString(resp.getEntity());

		smithIndex = body.indexOf("Jim");
		jansenIndex = body.indexOf("Barb");
		assertThat(smithIndex)
				.as("Smith is present")
				.isGreaterThan(0);
		assertThat(jansenIndex)
				.as("Jansen is not present")
				.isEqualTo(-1);

	}
	
	@Test
	public void g_updateUserTest() throws MalformedURLException {
		logger.info("\tG. Modify user with PUT Test (GET followed by PUT");
		CloseableHttpClient client = HttpClients.createDefault();
		
		String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);
		logger.info("\t\tGET "+req);
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check that it is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			logger.debug("Entry retrieved:\n"+body);
			
			// Check that the result can be parsed as a SCIM object
			JsonNode jres = JsonUtil.getJsonTree(body);
			ScimResource res = new ScimResource(smgr,jres, "Users");
			resp.close();
			
			Attribute name = res.getAttribute("displayName", null);
			
			// Modify the result and put back
			String dname = "\"Babs (TEST) Jensen\"";
			JsonNode node = JsonUtil.getJsonTree(dname);
			//node.get("displayName");
			StringValue newval = new StringValue(name,node);
			//res.removeValue(name);
			res.addValue(newval);

			logger.info("\t\tPUT "+req);
			HttpPut put = new HttpPut(req);
		    entity = new StringEntity(res.toJsonString(),ContentType.create(ScimParams.SCIM_MIME_TYPE));
			put.setEntity(entity);
			
			resp = client.execute(put);
			assertThat(resp.getStatusLine().getStatusCode())
				.as("PUT User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
		
			body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check that PUT response is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Contains test value")
				.contains("Babs (TEST)");
			logger.debug("Entry retrieved:\n"+body);
		} catch (IOException | ParseException | ScimException e) {
			fail("Exception occured making GET request for bjensen",e);
		}
	}
	
	@Test
	public void h_ScimDeleteUserTest() throws MalformedURLException {
		
		logger.info("\tH. Deleting user from backend");
		CloseableHttpClient client = HttpClients.createDefault();
		
		String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);
		
		HttpUriRequest request = new HttpDelete(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			
			// confirm status 204 per RFC7644 Sec 3.6
			assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm succesfull deletion of user")
				.isEqualTo(ScimResponse.ST_NOCONTENT);
			
			
			// Try to retrieve the deleted object. Should return 404
			request = new HttpGet(req);
			resp = client.execute(request);
			assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm deleted user was not findable")
				.isEqualTo(ScimResponse.ST_NOTFOUND);
			
			// Try delete of non-existent object, should be 404
			request = new HttpDelete(req);
			resp = client.execute(request);
			assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm not found when deleting non-existent resource")
				.isEqualTo(ScimResponse.ST_NOTFOUND);
			
			resp.close();
		} catch (IOException  e) {
			fail("Exception occured in DELETE test for bjensen",e);
		}
	}

}
