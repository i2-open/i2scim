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


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.ComplexValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
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
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;


/**
 * This tests only basic functionality of SCIM Patch. {@link com.independentid.scim.test.sub.ScimResourceTest} contains
 * the full functionality test. This test checks basic Http requirements and function.
 */
@QuarkusTest
@TestProfile(ScimHttpTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimPatchTest {
	
	private final static Logger logger = LoggerFactory.getLogger(ScimPatchTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

	@Inject
	SchemaManager smgr;

	@Inject
    TestUtils testUtils;

	@TestHTTPResource("/")
	URL baseUrl;
	
	private static String user1url = "", user2url = "", grpUrl = "";
	
	private static final String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	private static final String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

	static String patchRequestBody;
	static ScimResource res1,res2;

	static JsonPatchRequest jpr;

	/**
	 * This test actually resets and re-initializes the SCIM Mongo test database.
	 */
	@Test
	public void a_initializeProvider() {
	
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
	public void b_PrepareTestData() throws MalformedURLException, UnsupportedEncodingException {
		
		logger.info("\tB1. Add users and group...");

		try {

			InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);

			JsonNode userNode = JsonUtil.getJsonTree(userStream);
			res1 = new ScimResource(smgr,userNode,"Users");
			URL rUrl = new URL(baseUrl,"/Users");
			String req = rUrl.toString();
			
			
			HttpPost post = new HttpPost(req);
				
			StringEntity reqEntity = new StringEntity(userNode.toString());
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
			userNode = JsonUtil.getJsonTree(userStream);
			res2 = new ScimResource(smgr,userNode,"Users");
			reqEntity = new StringEntity(userNode.toString());

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
			Assertions.fail("Exception occured creating users. "+e.getMessage(),e);
		} catch (ScimException | ParseException e) {
			Assertions.fail("Scim exception occured parsing users: "+e.getMessage(),e);
		}

		String jsonGroup = "{\n" +
				"     \"schemas\": [\"urn:ietf:params:scim:schemas:core:2.0:Group\"],\n" +
				"     \"id\": \"e9e30dba-f08f-4109-8486-d5c6a331660a\",\n" +
				"     \"displayName\": \"TEST Tour Guides\",\n" +
				"     \"members\": [\n";
		jsonGroup = jsonGroup + memberObj(user1url)+"\n]}";

		String req = TestUtils.mapPathToReqUrl(baseUrl,"/Groups");

		HttpPost postGroup = new HttpPost(req);
		StringEntity body = new StringEntity(jsonGroup);
		body.setChunked(false);
		postGroup.setEntity(body);

		HttpResponse resp = null;
		try {
			resp = TestUtils.executeRequest(postGroup);
		} catch (IOException e) {
			fail("Failed to create group: "+e.getMessage(),e);
		}
		assert resp != null;
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

	private String memberObj(String ref) {
		String id = ref.substring(ref.lastIndexOf("/")+1);
		return "{ \"value\": \""+id+"\",\n"+
				"    \"$ref\": \""+ref+"\",\n" +
				"	 \"type\": \"User\" }";
	}

	@Test
	public void c_GroupTest() throws IOException {
		logger.info("\tC. Test Modify Group...");

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
				.as("Does not have jsmith url")
				.doesNotContain(user2url);
		System.out.println("Entry retrieved:\n"+body);

		ObjectNode reqJson = JsonUtil.getMapper().createObjectNode();
		ArrayNode snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
		snode.add(ScimParams.SCHEMA_API_PatchOp);

		ArrayNode anode = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);
		String memStr = memberObj(user2url);
		String opStr = "{\"op\": \"add\", \"path\": \"members\", \"value\": "+memStr+"}";
		JsonNode memNode = JsonUtil.getJsonTree(opStr);

		anode.add(memNode);

		String req = TestUtils.mapPathToReqUrl(baseUrl,grpUrl);
        HttpPatch post = new HttpPatch(req);

        String requestBody = reqJson.toPrettyString();
        logger.info("\t...Patch request\n"+requestBody);
        StringEntity reqEntity = new StringEntity(requestBody);
        reqEntity.setChunked(false);
        post.setEntity(reqEntity);

        logger.info("\t...Patching group to add JSmith");
        //logger.debug(EntityUtils.toString(reqEntity));

        resp = TestUtils.executeRequest(post);
        assertThat(resp.getStatusLine().getStatusCode())
                .as("Has HTTP response status of 200 - ok")
                .isEqualTo(ScimResponse.ST_OK);
        String rbody = EntityUtils.toString(resp.getEntity());
        assertThat(rbody)
                .as("Has smith ref of "+user2url)
                .contains(user2url);
        System.out.println("Response:\n"+rbody);
	}

	@Test
	public void d_CheckPatchUser() throws ScimException, IOException {
		logger.info("D. Checking Patch User");

		Attribute phone = smgr.findAttribute("User:phoneNumbers",null);
		Attribute valAttr = phone.getSubAttribute("value");
		Attribute typAttr = phone.getSubAttribute("type");
		StringValue val = new StringValue(valAttr,"987-654-3210");
		StringValue type = new StringValue(typAttr,"test");
		Map<Attribute,Value> map = new HashMap<>();
		map.put(valAttr,val);
		map.put(typAttr,type);
		ComplexValue phoneVal = new ComplexValue(phone,map);

		JsonPatchOp patchOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_ADD,"User:phoneNumbers",phoneVal);

		ObjectNode reqJson = JsonUtil.getMapper().createObjectNode();
		ArrayNode snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
		snode.add(ScimParams.SCHEMA_API_PatchOp);
		ArrayNode anode = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);
		anode.add(patchOp.toJsonNode());

		RequestCtx ctx = new RequestCtx(user2url,null,null,smgr);
		jpr = new JsonPatchRequest(reqJson, ctx);  // test the Json Parser constructor

		assertThat(jpr.getSize())
				.as("Check one operation parsed")
				.isEqualTo(1);

		patchRequestBody = jpr.toJsonNode().toPrettyString();
		logger.info("\t...JSmith patch request:\n"+patchRequestBody);

		String req = TestUtils.mapPathToReqUrl(baseUrl,user2url);

		HttpPatch patchUser = new HttpPatch(req);
		StringEntity body = new StringEntity(patchRequestBody);
		body.setChunked(false);
		patchUser.setEntity(body);

		HttpResponse resp = TestUtils.executeRequest(patchUser);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Patch user response status of 200 OK")
				.isEqualTo(ScimResponse.ST_OK);

		HttpEntity entity = resp.getEntity();
		assertThat(entity)
				.isNotNull();
		String respbody  = EntityUtils.toString(entity);

		logger.info("\t...user patch response:\n"+respbody);
		assertThat(respbody)
				.as("JSmith Has the new phone number")
				.contains("987-654-3210");
	}

	@Test
	public void e_NoTargetTest() throws IOException {
		logger.info("E. Checking No Target Response");
		// This is a valid request, however, there is no type equal to blah so No_target
		JsonPatchOp faultyValueOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE,"phoneNumbers[type eq blah].value",null);
		jpr = new JsonPatchRequest();
		jpr.addOperation(faultyValueOp);

		String req = TestUtils.mapPathToReqUrl(baseUrl,user1url);

		HttpPatch patchUser = new HttpPatch(req);
		StringEntity body = new StringEntity(jpr.toJsonNode().toPrettyString());
		body.setChunked(false);
		patchUser.setEntity(body);

		HttpResponse resp = TestUtils.executeRequest(patchUser);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Patch resposne bad request")
				.isEqualTo(ScimResponse.ST_BAD_REQUEST);

		HttpEntity entity = resp.getEntity();
		assertThat(entity)
				.isNotNull();
		String respbody  = EntityUtils.toString(entity);

		logger.info("\t...Response to no match:\n"+respbody);
		assertThat(respbody)
				.as("confirm noTarget Error")
				.contains("noTarget");
	}

	@Test
	public void f_InvalidValueTest() throws IOException {
		logger.info("F. Checking Invalid Value Response");
		JsonPatchOp faultyValueOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE,"phoneNumbers[type eq blah].value",null);
		jpr = new JsonPatchRequest();
		jpr.addOperation(faultyValueOp);

		String req = TestUtils.mapPathToReqUrl(baseUrl,user1url);

		HttpPatch patchUser = new HttpPatch(req);
		StringEntity body = new StringEntity(jpr.toJsonNode().toPrettyString());
		body.setChunked(false);
		patchUser.setEntity(body);

		HttpResponse resp = TestUtils.executeRequest(patchUser);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Patch resposne bad request")
				.isEqualTo(ScimResponse.ST_BAD_REQUEST);

		HttpEntity entity = resp.getEntity();
		assertThat(entity)
				.isNotNull();
		String respbody  = EntityUtils.toString(entity);

		logger.info("\t...Response to invalid value request:\n"+respbody);
		assertThat(respbody)
				.as("confirm invalid value error")
				.contains("invalidValue");
	}

	@Test
	public void g_MethodNotAllowedTest() throws IOException {
		logger.info("G. Checking PATCH on Container not allowed");
		JsonPatchOp dummyOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE,"phoneNumbers",null);
		jpr = new JsonPatchRequest();
		jpr.addOperation(dummyOp);

		String req = TestUtils.mapPathToReqUrl(baseUrl,"/Users");

		HttpPatch patchUser = new HttpPatch(req);
		StringEntity body = new StringEntity(jpr.toJsonNode().toPrettyString());
		body.setChunked(false);
		patchUser.setEntity(body);

		HttpResponse resp = TestUtils.executeRequest(patchUser);
		assertThat(resp.getStatusLine().getStatusCode())
				.as("Patch resposne bad request")
				.isEqualTo(ScimResponse.ST_METHODNOTALLOWED);

	}
}
