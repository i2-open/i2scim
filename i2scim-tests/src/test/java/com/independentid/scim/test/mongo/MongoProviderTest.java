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

package com.independentid.scim.test.mongo;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.ExtensionValues;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MongoProviderTest {
	
	private static final Logger logger = LoggerFactory.getLogger(MongoProviderTest.class);

	@Inject
	SchemaManager smgr;

	@Inject
	BackendHandler handler;

	@Inject
	TestUtils testUtils;

	@ConfigProperty(name="scim.mongodb.dbname",defaultValue = "testSCIM")
	String scimDbName;

	static MongoProvider mp = null;

	private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

	private static String user1uid,grpid;
	private static String user1url,user2url,grpurl; //,user2url;

	@Test
	public void a_providerTest() {

		logger.info("========== Mongo Provider CRUD Test ==========");
		try {
			testUtils.resetProvider();
		} catch (ScimException | BackendException | IOException e) {
			Assertions.fail("Failed to reset provider: "+e.getMessage());
		}
		mp = (MongoProvider) handler.getProvider();

		assertThat(mp).isNotNull();

		assertThat(mp.ready()).isTrue();


	}

	public String getResponseBody(ScimResponse resp, RequestCtx ctx) throws IOException {
		StringWriter respWriter = new StringWriter();
		JsonGenerator gen = JsonUtil.getGenerator(respWriter,false);
		resp.serialize(gen,ctx);
		gen.close();
		return respWriter.toString();
	}
	/**
	 * This test checks that a JSON user can be parsed into a SCIM Resource
	 */
	@Test
	public void b_ScimAddUserTest() {

		logger.info("\tB1. Add User BJensen...");
		try {
			InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
			assert userStream != null;

			//InputStream userStream = this.resourceloader.getResource(testUserFile1).getInputStream();
			JsonNode node = JsonUtil.getJsonTree(userStream);
			//,user2;
			ScimResource user1 = new ScimResource(smgr, node, "Users");
			user1.setId(null);  // Mongo won't exxcept external ids
			RequestCtx ctx = new RequestCtx("/Users",null,null,smgr);
			ScimResponse resp = mp.create(ctx, user1);
			user1url = resp.getLocation();
			assertThat (resp.getStatus())
					.as("Check user created success")
					.isEqualTo(ScimResponse.ST_CREATED);

			assertThat(resp).isInstanceOf(ResourceResponse.class);
			ResourceResponse rresp = (ResourceResponse) resp;
			user1uid = rresp.getId();
			String body = getResponseBody(resp,ctx);
			logger.debug("Body:\n"+body);

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

			logger.info("\tB2. Attempt to add User BJensen again (uniquenes test)...");


			// Attempt to repeat the operation. It should fail due to non-unique username match
			ctx = new RequestCtx("/Users",null,null,smgr);
			resp = mp.create(ctx, user1);

			assertThat(resp.getStatus())
					.as("Confirm error 400 occurred (uniqueness)")
					.isEqualTo(ScimResponse.ST_BAD_REQUEST);
			body = getResponseBody(resp,ctx);
			assertThat(body)
					.as("Is a uniqueness error")
					.contains(ScimResponse.ERR_TYPE_UNIQUENESS);

			logger.info("\tB2. Adding JSmith...");
			// add jsmith
			userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
			assert userStream != null;
			node = JsonUtil.getJsonTree(userStream);
			//,user2;
			ScimResource user2 = new ScimResource(smgr, node, "Users");
			user2.setId(null);  // Mongo won't exxcept external ids
			ctx = new RequestCtx("/Users",null,null,smgr);
			resp = mp.create(ctx, user2);
			user2url = resp.getLocation();
			assertThat (resp.getStatus())
					.as("Check user created success")
					.isEqualTo(ScimResponse.ST_CREATED);


		} catch (IOException | ParseException | ScimException e) {
			Assertions.fail("Exception occured creating bjenson. "+e.getMessage(),e);
		}
	}

	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void c_ScimGetUserTest()  {

		try {
			logger.info("\tC. Retrieving user from backend using: "+user1url);
			RequestCtx ctx = new RequestCtx(user1url,null,null,smgr);

			ScimResponse resp = mp.get(ctx);


			assertThat(resp.getStatus())
					.as("GET User - Check for status response 200 OK")
					.isEqualTo(ScimResponse.ST_OK);

			String body = getResponseBody(resp,ctx);

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

		} catch (IOException | ParseException | ScimException | BackendException e) {
			org.assertj.core.api.Assertions.fail("Exception occured making GET request for bjensen",e);
		}
	}

	/**
	 * This test tries to search for the previously created user by searching on filter name
	 */
	@Test
	public void d_ScimSearchUserTest() {

		logger.info("\tD. Search using GET for user from backend with filter=UserName eq bjensen@example.com");

		try {
			RequestCtx ctx = new RequestCtx(user1url,null, "UserName eq bjensen@example.com",smgr);

			ScimResponse resp = mp.get(ctx);

			assertThat(resp.getStatus())
					.as("GET User - Check for status response 200 OK")
					.isEqualTo(ScimResponse.ST_OK);

			String body = getResponseBody(resp,ctx);

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

		} catch (IOException | ParseException | ScimException | BackendException e) {
			org.assertj.core.api.Assertions.fail("Exception occured making GET filter request for bjensen",e);
		}
	}

	@Test
	public void e_ScimSearchValPathUserTest() {

		logger.info("\tE. Searching user from backend with filter=UserName eq bjensen@example.com and addresses[country eq \\\"USA\\\" and type eq \\\"home\\\"]");


		try {
			RequestCtx ctx = new RequestCtx(user1url,null,"UserName eq bjensen@example.com and addresses[country eq \"USA\" and type eq \"home\"]",smgr);

			ScimResponse resp = mp.get(ctx);
			String body = getResponseBody(resp,ctx);

			assertThat(resp.getStatus())
					.as("GET User - Check for status response 200 OK")
					.isEqualTo(ScimResponse.ST_OK);

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

		} catch (IOException | ParseException | ScimException | BackendException e) {
			org.assertj.core.api.Assertions.fail("Exception occured making GET filter request for bjensen",e);
		}
	}

	@Test
	public void f_updateUserTest() {
		logger.info("\tF. Modify user with PUT Test");

		try {
			RequestCtx ctx = new RequestCtx(user1url,null,null,smgr);
			ScimResponse resp = mp.get(ctx);
			String body = getResponseBody(resp,ctx);

			assertThat(resp.getStatus())
					.as("GET User - Check for status response 200 OK")
					.isEqualTo(ScimResponse.ST_OK);

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

			Attribute name = res.getAttribute("displayName", null);

			// Modify the result and put back
			String dname = "\"Babs (TEST) Jensen\"";
			JsonNode node = JsonUtil.getJsonTree(dname);
			//node.get("displayName");
			StringValue newval = new StringValue(name,node);
			//res.removeValue(name);
			res.addValue(newval);

			ctx = new RequestCtx(user1url,null,null,smgr);
			resp = mp.put(ctx,res);


			assertThat(resp.getStatus())
					.as("PUT User - Check for status response 200 OK")
					.isEqualTo(ScimResponse.ST_OK);

			body = getResponseBody(resp,ctx);

			assertThat(body)
					.as("Check that PUT response is not a ListResponse")
					.doesNotContain(ScimParams.SCHEMA_API_ListResponse);

			assertThat(body)
					.as("Contains test value")
					.contains("Babs (TEST)");
			logger.debug("Entry retrieved:\n"+body);
		} catch (IOException | ParseException | ScimException | BackendException e) {
			org.assertj.core.api.Assertions.fail("Exception occured making GET request for bjensen",e);
		}
	}

	private String memberObj(String ref) {
		String id = ref.substring(ref.lastIndexOf("/")+1);
		return "{ \"value\": \""+id+"\",\n"+
				"    \"$ref\": \""+ref+"\"}";
	}

	@Test
	public void g_AddGroupTest() throws JsonProcessingException, ScimException, ParseException {
		logger.info("\tG. Add Group Test(also second container)");

		String jsonGroup = "{\n" +
				"     \"schemas\": [\"urn:ietf:params:scim:schemas:core:2.0:Group\"],\n" +
				"     \"displayName\": \"TEST Tour Guides\",\n" +
				"     \"members\": [\n";
		jsonGroup = jsonGroup + memberObj(user1url)+",\n"+memberObj(user2url)+"\n]}";
		JsonNode node = JsonUtil.getJsonTree(jsonGroup);
		ScimResource grpRes = new ScimResource(smgr,node,"Groups");
		RequestCtx ctx = new RequestCtx("/Groups",null,null,smgr);
		ScimResponse  resp = mp.create(ctx,grpRes);
		assertThat(resp.getStatus())
				.as("Confirm group created")
				.isEqualTo(ScimResponse.ST_CREATED);
		grpurl = resp.getLocation();
	}

	@Test
	public void h_GlobalSearchTest() throws ScimException, BackendException {
		logger.info("\tH. Global Search Test");

		String filter = "members.value eq "+user1uid+" or id eq "+user1uid; // we want to match a resource in each container
		RequestCtx ctx = new RequestCtx(null,null,filter,smgr);

		ScimResponse resp = mp.get(ctx);

		assertThat(resp.getStatus())
				.isEqualTo(ScimResponse.ST_OK);

		assertThat(resp)
				.as("Check for ListResponse")
				.isInstanceOf(ListResponse.class);
		ListResponse lresp = (ListResponse) resp;
		assertThat(lresp.getSize())
				.as("Should be two matches")
				.isEqualTo(2);
	}

	@Test
	public void i_ScimDeleteUserTest() {
		logger.info("\tI. Deleting user test");

		try {
			RequestCtx ctx = new RequestCtx(user1url,null,null,smgr);
			ScimResponse resp = mp.delete(ctx);

			// confirm status 204 per RFC7644 Sec 3.6
			assertThat(resp.getStatus())
					.as("Confirm succesfull deletion of user")
					.isEqualTo(ScimResponse.ST_NOCONTENT);


			// Try to retrieve the deleted object. Should return 404
			ctx = new RequestCtx(user1url,null,null,smgr);
			resp = mp.get(ctx);
			assertThat(resp.getStatus())
					.as("Confirm deleted user was not findable")
					.isEqualTo(ScimResponse.ST_NOTFOUND);

			// Try delete of non-existent object, should be 404
			ctx = new RequestCtx(user1url,null,null,smgr);
			resp = mp.delete(ctx);

			assertThat(resp.getStatus())
					.as("Confirm not found when deleting non-existent resource")
					.isEqualTo(ScimResponse.ST_NOTFOUND);


		} catch (ScimException | BackendException e) {
			org.assertj.core.api.Assertions.fail("Exception occured in DELETE test for bjensen",e);
		}
	}

}
