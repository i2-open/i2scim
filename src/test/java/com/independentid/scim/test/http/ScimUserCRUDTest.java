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


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.*;
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
	BackendHandler handler;
	
	@ConfigProperty(name="scim.mongodb.uri",defaultValue = "mongodb://localhost:27017")
	String dbUrl;

	@ConfigProperty(name="scim.mongodb.dbname",defaultValue = "testSCIM")
	String scimDbName;
	
	private MongoClient mclient = null;

	@TestHTTPResource("/")
	URL baseUrl;
	
	private static String user1url = "";
	
	private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	//private String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";
	
	//private String user1compare = "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\",\"urn:ietf:params:scim:schemas:extension:enterprise:2.0:User\"],\"id\":\"2819c223-7f76-453a-919d-413861904646\",\"externalId\":\"701984\",\"userName\":\"bjensen@example.com\",\"name\":{\"formatted\":\"Ms. Barbara J Jensen III\",\"familyName\":\"Jensen\",\"givenName\":\"Barbara\",\"middleName\":\"Jane\",\"honorificPrefix\":\"Ms.\",\"honorificSuffix\":\"III\"},\"displayName\":\"Babs Jensen\",\"nickName\":\"Babs\",\"profileUrl\":\"https://login.example.com/bjensen\",\"emails\":[{\"value\":\"bjensen@example.com\",\"type\":\"work\",\"primary\":true},{\"value\":\"babs@jensen.org\",\"type\":\"home\"}],\"addresses\":[{\"streetAddress\":\"100 Universal City Plaza\",\"locality\":\"Hollywood\",\"region\":\"CA\",\"postalCode\":\"91608\",\"country\":\"USA\",\"formatted\":\"100 Universal City Plaza\\nHollywood, CA 91608 USA\",\"type\":\"work\"},{\"streetAddress\":\"456 Hollywood Blvd\",\"locality\":\"Hollywood\",\"region\":\"CA\",\"postalCode\":\"91608\",\"country\":\"USA\",\"formatted\":\"456 Hollywood Blvd\\nHollywood, CA 91608 USA\",\"type\":\"home\"}],\"phoneNumbers\":[{\"value\":\"555-555-5555\",\"type\":\"work\"},{\"value\":\"555-555-4444\",\"type\":\"mobile\"}],\"ims\":[{\"value\":\"someaimhandle\",\"type\":\"aim\"}],\"photos\":[{\"value\":\"https://photos.example.com/profilephoto/72930000000Ccne/F\",\"type\":\"photo\"},{\"value\":\"https://photos.example.com/profilephoto/72930000000Ccne/T\",\"type\":\"thumbnail\"}],\"userType\":\"Employee\",\"title\":\"Tour Guide\",\"preferredLanguage\":\"en-US\",\"locale\":\"en-US\",\"timezone\":\"America/Los_Angeles\",\"active\":true,\"password\":\"t1meMa$heen\",\"groups\":[{\"value\":\"e9e30dba-f08f-4109-8486-d5c6a331660a\",\"$ref\":\"/Groups/e9e30dba-f08f-4109-8486-d5c6a331660a\",\"display\":\"Tour Guides\"},{\"value\":\"fc348aa8-3835-40eb-a20b-c726e15c55b5\",\"$ref\":\"/Groups/fc348aa8-3835-40eb-a20b-c726e15c55b5\",\"display\":\"Employees\"},{\"value\":\"71ddacd2-a8e7-49b8-a5db-ae50d0a5bfd7\",\"$ref\":\"/Groups/71ddacd2-a8e7-49b8-a5db-ae50d0a5bfd7\",\"display\":\"US Employees\"}],\"x509Certificates\":[{\"value\":\"MIIDQzCCAqygAwIBAgICEAAwDQYJKoZIhvcNAQEFBQAwTjELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAoMC2V4YW1wbGUuY29tMRQwEgYDVQQDDAtleGFtcGxlLmNvbTAeFw0xMTEwMjIwNjI0MzFaFw0xMjEwMDQwNjI0MzFaMH8xCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRQwEgYDVQQKDAtleGFtcGxlLmNvbTEhMB8GA1UEAwwYTXMuIEJhcmJhcmEgSiBKZW5zZW4gSUlJMSIwIAYJKoZIhvcNAQkBFhNiamVuc2VuQGV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7Kr+Dcds/JQ5GwejJFcBIP682X3xpjis56AK02bc1FLgzdLI8auoR+cC9/Vrh5t66HkQIOdA4unHh0AaZ4xL5PhVbXIPMB5vAPKpzz5iPSi8xO8SL7I7SDhcBVJhqVqr3HgllEG6UClDdHO7nkLuwXq8HcISKkbT5WFTVfFZzidPl8HZ7DhXkZIRtJwBweq4bvm3hM1Os7UQH05ZS6cVDgweKNwdLLrT51ikSQG3DYrl+ft781UQRIqxgwqCfXEuDiinPh0kkvIi5jivVu1Z9QiwlYEdRbLJ4zJQBmDrSGTMYn4lRc2HgHO4DqB/bnMVorHB0CC6AV1QoFK4GPe1LwIDAQABo3sweTAJBgNVHRMEAjAAMCwGCWCGSAGG+EIBDQQfFh1PcGVuU1NMIEdlbmVyYXRlZCBDZXJ0aWZpY2F0ZTAdBgNVHQ4EFgQU8pD0U0vsZIsaA16lL8En8bx0F/gwHwYDVR0jBBgwFoAUdGeKitcaF7gnzsNwDx708kqaVt0wDQYJKoZIhvcNAQEFBQADgYEAA81SsFnOdYJtNg5Tcq+/ByEDrBgnusx0jloUhByPMEVkoMZ3J7j1ZgI8rAbOkNngX8+pKfTiDz1RC4+dx8oU6Za+4NJXUjlL5CvV6BEYb1+QAEJwitTVvxB/A67g42/vzgAtoRUeDov1+GFiBZ+GNF/cAYKcMtGcrs2i97ZkJMo=\"}],\"urn:ietf:params:scim:schemas:extension:enterprise:2.0:User\":{\"division\":\"Theme Park\",\"manager\":[{\"value\":\"26118915-6090-4610-87e4-49d8ca9f808d\",\"$ref\":\"/Users/26118915-6090-4610-87e4-49d8ca9f808d\",\"displayName\":\"John Smith\"}],\"costCenter\":\"4130\",\"organization\":\"Universal Studios\",\"department\":\"Tour Operations\",\"employeeNumber\":\"701984\"}}"; 
		
	
	//private static ScimResource user1,user2 = null;
	


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
		mclient.close();
		
		try {
			handler.getProvider().syncConfig(smgr.getSchemas(), smgr.getResourceTypes());
		} catch (IOException e) {
			fail("Failed to initialize test Mongo DB: "+scimDbName);
		}
		
	}
	/**
	 * This test checks that a JSON user can be parsed into a SCIM Resource
	 */
	@Test
	public void b_ScimAddUserTest() {
		
		logger.info("\tB1. Add User BJensen...");
		CloseableHttpClient client = HttpClients.createDefault();

		try {
			File user1File = ConfigMgr.findClassLoaderResource(testUserFile1);

			assert user1File != null;
			InputStream userStream = new FileInputStream(user1File);

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
				user1url = loc.getValue();  // This will be used to retrieve the user later
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
			
			logger.info("\tB2. Attempt to add User BJensen again (uniquenes test)...");
			// Attempt to repeat the operation. It should fail due to non-unique username match
			post = new HttpPost(req);
			userStream = new FileInputStream(user1File);
			reqEntity = new InputStreamEntity(
	        userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
			reqEntity.setChunked(false);
			post.setEntity(reqEntity);
			resp = client.execute(post);
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("Confirm error 400 occurred (uniqueness)")
				.isEqualTo(ScimResponse.ST_BAD_REQUEST);
			body = EntityUtils.toString(resp.getEntity());
			assertThat(body)
				.as("Is a uniqueness error")
				.contains(ScimResponse.ERR_TYPE_UNIQUENESS);

			resp.close();
			
			
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
		
		logger.info("\tD. Searching user from backend with filter=UserName eq bjensen@example.com and addresses[country eq \\\"USA\\\" and type eq \\\"home\\\"]");
		CloseableHttpClient client = HttpClients.createDefault();
		
		String req = TestUtils.mapPathToReqUrl(baseUrl,
				"/Users?filter="+URLEncoder.encode("UserName eq bjensen@example.com and addresses[country eq \"USA\" and type eq \"home\"]",StandardCharsets.UTF_8));
		
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
	public void f_updateUserTest() throws MalformedURLException {
		logger.info("\t E. Modify user with PUT Test");
		CloseableHttpClient client = HttpClients.createDefault();
		
		String req = TestUtils.mapPathToReqUrl(baseUrl, user1url);
		
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
	public void g_ScimDeleteUserTest() throws MalformedURLException {
		
		logger.info("Deleting user from backend");
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
