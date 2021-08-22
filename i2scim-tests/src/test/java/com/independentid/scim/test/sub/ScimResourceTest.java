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

package com.independentid.scim.test.sub;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.client.ResourceBuilder;
import com.independentid.scim.client.i2scimClient;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.InvalidValueException;
import com.independentid.scim.core.err.NoTargetException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.*;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
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
import java.text.ParseException;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;


@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimResourceTest {
	
	private final Logger logger = LoggerFactory.getLogger(ScimResourceTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr ;

	@Inject
	BackendHandler handler;
	
	final static String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	final static String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";

	final static String certMatch = "MIIDQzCCAqygAwIBAg";
	//private String user1compare = "{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\",\"urn:ietf:params:scim:schemas:extension:enterprise:2.0:User\"],\"id\":\"2819c223-7f76-453a-919d-413861904646\",\"externalId\":\"701984\",\"userName\":\"bjensen@example.com\",\"name\":{\"formatted\":\"Ms. Barbara J Jensen III\",\"familyName\":\"Jensen\",\"givenName\":\"Barbara\",\"middleName\":\"Jane\",\"honorificPrefix\":\"Ms.\",\"honorificSuffix\":\"III\"},\"displayName\":\"Babs Jensen\",\"nickName\":\"Babs\",\"profileUrl\":\"https://login.example.com/bjensen\",\"emails\":[{\"value\":\"bjensen@example.com\",\"type\":\"work\",\"primary\":true},{\"value\":\"babs@jensen.org\",\"type\":\"home\"}],\"addresses\":[{\"streetAddress\":\"100 Universal City Plaza\",\"locality\":\"Hollywood\",\"region\":\"CA\",\"postalCode\":\"91608\",\"country\":\"USA\",\"formatted\":\"100 Universal City Plaza\\nHollywood, CA 91608 USA\",\"type\":\"work\"},{\"streetAddress\":\"456 Hollywood Blvd\",\"locality\":\"Hollywood\",\"region\":\"CA\",\"postalCode\":\"91608\",\"country\":\"USA\",\"formatted\":\"456 Hollywood Blvd\\nHollywood, CA 91608 USA\",\"type\":\"home\"}],\"phoneNumbers\":[{\"value\":\"555-555-5555\",\"type\":\"work\"},{\"value\":\"555-555-4444\",\"type\":\"mobile\"}],\"ims\":[{\"value\":\"someaimhandle\",\"type\":\"aim\"}],\"photos\":[{\"value\":\"https://photos.example.com/profilephoto/72930000000Ccne/F\",\"type\":\"photo\"},{\"value\":\"https://photos.example.com/profilephoto/72930000000Ccne/T\",\"type\":\"thumbnail\"}],\"userType\":\"Employee\",\"title\":\"Tour Guide\",\"preferredLanguage\":\"en-US\",\"locale\":\"en-US\",\"timezone\":\"America/Los_Angeles\",\"active\":true,\"password\":\"t1meMa$heen\",\"groups\":[{\"value\":\"e9e30dba-f08f-4109-8486-d5c6a331660a\",\"$ref\":\"/Groups/e9e30dba-f08f-4109-8486-d5c6a331660a\",\"display\":\"Tour Guides\"},{\"value\":\"fc348aa8-3835-40eb-a20b-c726e15c55b5\",\"$ref\":\"/Groups/fc348aa8-3835-40eb-a20b-c726e15c55b5\",\"display\":\"Employees\"},{\"value\":\"71ddacd2-a8e7-49b8-a5db-ae50d0a5bfd7\",\"$ref\":\"/Groups/71ddacd2-a8e7-49b8-a5db-ae50d0a5bfd7\",\"display\":\"US Employees\"}],\"x509Certificates\":[{\"value\":\"MIIDQzCCAqygAwIBAgICEAAwDQYJKoZIhvcNAQEFBQAwTjELMAkGA1UEBhMCVVMxEzARBgNVBAgMCkNhbGlmb3JuaWExFDASBgNVBAoMC2V4YW1wbGUuY29tMRQwEgYDVQQDDAtleGFtcGxlLmNvbTAeFw0xMTEwMjIwNjI0MzFaFw0xMjEwMDQwNjI0MzFaMH8xCzAJBgNVBAYTAlVTMRMwEQYDVQQIDApDYWxpZm9ybmlhMRQwEgYDVQQKDAtleGFtcGxlLmNvbTEhMB8GA1UEAwwYTXMuIEJhcmJhcmEgSiBKZW5zZW4gSUlJMSIwIAYJKoZIhvcNAQkBFhNiamVuc2VuQGV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA7Kr+Dcds/JQ5GwejJFcBIP682X3xpjis56AK02bc1FLgzdLI8auoR+cC9/Vrh5t66HkQIOdA4unHh0AaZ4xL5PhVbXIPMB5vAPKpzz5iPSi8xO8SL7I7SDhcBVJhqVqr3HgllEG6UClDdHO7nkLuwXq8HcISKkbT5WFTVfFZzidPl8HZ7DhXkZIRtJwBweq4bvm3hM1Os7UQH05ZS6cVDgweKNwdLLrT51ikSQG3DYrl+ft781UQRIqxgwqCfXEuDiinPh0kkvIi5jivVu1Z9QiwlYEdRbLJ4zJQBmDrSGTMYn4lRc2HgHO4DqB/bnMVorHB0CC6AV1QoFK4GPe1LwIDAQABo3sweTAJBgNVHRMEAjAAMCwGCWCGSAGG+EIBDQQfFh1PcGVuU1NMIEdlbmVyYXRlZCBDZXJ0aWZpY2F0ZTAdBgNVHQ4EFgQU8pD0U0vsZIsaA16lL8En8bx0F/gwHwYDVR0jBBgwFoAUdGeKitcaF7gnzsNwDx708kqaVt0wDQYJKoZIhvcNAQEFBQADgYEAA81SsFnOdYJtNg5Tcq+/ByEDrBgnusx0jloUhByPMEVkoMZ3J7j1ZgI8rAbOkNngX8+pKfTiDz1RC4+dx8oU6Za+4NJXUjlL5CvV6BEYb1+QAEJwitTVvxB/A67g42/vzgAtoRUeDov1+GFiBZ+GNF/cAYKcMtGcrs2i97ZkJMo=\"}],\"urn:ietf:params:scim:schemas:extension:enterprise:2.0:User\":{\"division\":\"Theme Park\",\"manager\":[{\"value\":\"26118915-6090-4610-87e4-49d8ca9f808d\",\"$ref\":\"/Users/26118915-6090-4610-87e4-49d8ca9f808d\",\"displayName\":\"John Smith\"}],\"costCenter\":\"4130\",\"organization\":\"Universal Studios\",\"department\":\"Tour Operations\",\"employeeNumber\":\"701984\"}}"; 
		
	final static String entSchema = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
	
	static ScimResource user1,user2 = null;

	/**
	 * This test checks that a JSON user can be parsed into a SCIM Resource
	 */
	@Test
	public void a_ScimResParseUser1Test() {
		
		logger.info("========== ScimResource Test ==========");

		i2scimClient client = new i2scimClient(this.smgr);

		try {
			InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
			assert userStream != null;

			//InputStream userStream = this.resourceloader.getResource(testUserFile1).getInputStream();
			ResourceBuilder builder = client.getResourceBuilder(userStream);
			user1 = builder.build();
			String outString = user1.toJsonString();
			logger.debug("User loaded: \n"+ outString);
			assertThat(outString)
					.as("User1 contains the certificate")
					.contains(certMatch);

			userStream.close();
			
			userStream = ConfigMgr.findClassLoaderResource(testUserFile2);
			user2 = client.getResourceBuilder(userStream).build();
			logger.debug("User loaded: \n"+user2);
			
			assertThat(user1)
				.as("SCIM User BJensen Parse Test")
				.isNotNull();
			
			assertThat(user2)
				.as("SCIM User JSmith Parse Test")
				.isNotNull();

			Attribute userAttr = user1.getAttribute("userName", null);
			StringValue userValue = (StringValue) user1.getValue(userAttr);
			assertThat(userValue.getRawValue())
				.as("Has username value of bjensen")
				.isEqualTo("bjensen@example.com");
			
			userValue = (StringValue) user2.getValue(userAttr);
			assertThat(userValue.getRawValue())
				.as("Has username value of jsmith@example.com")
				.isEqualTo("jsmith@example.com");
			
			ExtensionValues exts = user1.getExtensionValues(entSchema);
			Value dval = exts.getValue("department");
			assertThat(dval)
				.as("Check that an enterprise department value returned.")
				.isNotNull();
			
			StringValue sval = (StringValue)dval;
		
			assertThat(sval.getRawValue())
				.as("Check that department is Tour Operations")
				.isEqualTo("Tour Operations");
			
		} catch (IOException | ParseException | ScimException e) {
			Assertions.fail("Exception occured while parsing test user bjensen. "+e.getMessage(),e);
		}
		
		
	}

	@Test
	public void b_serializationTest_NoCtx() {
		assert user1 != null;
		assert user2 != null;

		// Note that without context match, there should be no differences.  All attributes should be returned regardless of serialization mode.
		
		Attribute x509attr = smgr.findAttribute("x509Certificates",null);
		assertThat(x509attr).isNotNull();

		Value certVal = user1.getValue(x509attr);
		checkCertValue(certVal);

		StringWriter writer = new StringWriter();
		try {
			JsonGenerator gen = JsonUtil.getGenerator(writer,false);
			user1.serialize(gen,null,false);
			gen.close();
			writer.close();
			String result = writer.toString();
			logger.debug("Result for serializing no ctx user1:\n"+result);
			assertThat(result)
					.as("Contains the certificate")
					.contains(certMatch);

			// Now generate a JsonMode representation and check for values
			JsonNode unode = user1.toJsonNode(null);

			JsonNode eUserNode = unode.get("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(eUserNode)
					.as("Has an Enterprise User extension node")
					.isNotNull();
			String result2 = unode.toPrettyString();

			logger.debug("Result for toJsonNode for user1:\n"+result2);
			logger.info("\n\nComparing toJsonNode.toString vs serialize outputs:\n");
			DiffMatchPatch dmp = new DiffMatchPatch();
			LinkedList<DiffMatchPatch.Diff> diff = dmp.diffMain(result,result2,false);
			for (DiffMatchPatch.Diff adif : diff) {
				if (!adif.operation.equals(DiffMatchPatch.Operation.EQUAL))
					logger.info("Difference: "+adif);
			}
			assertThat(diff.size())
					.as("Difference analysis returns 1 result").isEqualTo(1);
			DiffMatchPatch.Diff thediff = diff.get(0);
			assertThat(thediff.operation)
					.as("Is an equality match").isEqualTo(DiffMatchPatch.Operation.EQUAL);

		} catch (IOException | ScimException e) {
			fail(e.getMessage());
		}

	}

	@Test
	public void c_serializationTest_Ctx() {
		assert user1 != null;
		assert user2 != null;

		StringWriter writer = new StringWriter();
		try {
			RequestCtx ctx = new RequestCtx("/Users",null,null,smgr);
			ctx.setAttributes("id,meta,name,x509certificates");
			JsonGenerator gen = JsonUtil.getGenerator(writer,false);
			user1.serialize(gen,ctx,false);
			gen.close();
			writer.close();
			String result = writer.toString();
			logger.debug("Result for serializing with ctx user1:\n"+result);
			assertThat(result)
					.as("Contains the certificate")
					.contains(certMatch);

			// Now generate a JsonMode representation and check for values
			JsonNode unode = user1.toJsonNode(ctx);

			JsonNode eUserNode = unode.get("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(eUserNode)
					.as("Has an Enterprise User extension node")
					.isNull();
			String result2 = unode.toPrettyString();

			logger.debug("Result for toJsonNode with ctx for user1:\n"+result2);
			logger.info("\n\nComparing toJsonNode.toString vs serialize outputs:\n");
			DiffMatchPatch dmp = new DiffMatchPatch();
			LinkedList<DiffMatchPatch.Diff> diff = dmp.diffMain(result,result2,false);
			int differenceCount = 0;
			for (DiffMatchPatch.Diff adif : diff) {
				if (!adif.operation.equals(DiffMatchPatch.Operation.EQUAL)) {
					differenceCount++;
					logger.info("Difference: " + adif);
				}
			}
			assertThat(differenceCount)
					.as("Difference analysis returns 1 result").isEqualTo(1);
			DiffMatchPatch.Diff thediff = diff.get(0);
			assertThat(thediff.operation)
					.as("Is an equality match").isEqualTo(DiffMatchPatch.Operation.EQUAL);

		} catch (IOException | ScimException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void d_copyTest() {
		try {
			ScimResource copyRes = user1.copy(null);

			String original = user1.toString();
			String copy = copyRes.toString();

			DiffMatchPatch dmp = new DiffMatchPatch();
			LinkedList<DiffMatchPatch.Diff> diff = dmp.diffMain(original,copy,false);
			for (DiffMatchPatch.Diff adif : diff) {
				if (!adif.operation.equals(DiffMatchPatch.Operation.EQUAL))
					logger.info("Difference: "+adif);
			}
			assertThat(diff.size())
					.as("Difference analysis returns 1 result").isEqualTo(1);
			DiffMatchPatch.Diff thediff = diff.get(0);
			assertThat(thediff.operation)
					.as("Is an equality match").isEqualTo(DiffMatchPatch.Operation.EQUAL);

			RequestCtx ctx = new RequestCtx("/Users",null,null,smgr);
			ctx.setAttributes("id,meta,name,x509certificates");
			ScimResource limitedCopy = user1.copy(ctx);
			Attribute uname = smgr.findAttribute("userName",ctx);
			Meta meta = limitedCopy.getMeta();
			Value val = limitedCopy.getValue(uname);
			assertThat(meta).isNotNull();
			assertThat(val).isNull();

		} catch (ScimException | ParseException e) {
			e.printStackTrace();
		}

	}

	private void checkCertValue(Value certVal) {
		assertThat(certVal).as("Has a certificate value").isNotNull();
		assertThat(certVal).as("Cert is instanceof MultiValue").isInstanceOf(MultiValue.class);
		MultiValue mval = (MultiValue) certVal;
		Value[] vals = mval.getRawValue();
		assertThat(vals)
				.as("Contains 1 value")
				.hasSize(1);
		assertThat(vals[0])
				.as("Is a ComplexValue")
				.isInstanceOf(ComplexValue.class);
		ComplexValue cval = (ComplexValue) vals[0];

		BinaryValue bval = (BinaryValue) cval.getValue("value");
		assertThat(bval.toString())
				.as("Is a match")
				.startsWith(certMatch);
	}

	@Test
	public void e_AddRemoveValueTest() throws SchemaException {
		Attribute phoneNum = smgr.findAttribute("User:phoneNumbers",null);
		String pnumber = "604-307-1751";
		String ptype = "test";
		String testGiven = "BarbTEST";
		LinkedHashMap<Attribute,Value> map = new LinkedHashMap<>();
		map.put(phoneNum.getSubAttribute("value"),new StringValue(phoneNum.getSubAttribute("value"),pnumber));
		map.put(phoneNum.getSubAttribute("type"),new StringValue(phoneNum.getSubAttribute("type"),ptype));
		ComplexValue number = new ComplexValue(phoneNum,map);

		Attribute gname = smgr.findAttribute("User:name.givenName",null);
		StringValue given = new StringValue(gname,testGiven);
		user1.addValue(given);

		Attribute uname = smgr.findAttribute("User:userName",null);

		String newUsername = "testUser";
		user1.addValue(new StringValue(uname,newUsername));

		user1.addValue(number);

		String testMatch = user1.toJsonString();
		assertThat(testMatch)
				.as("Has new test given name")
				.contains(testGiven);
		assertThat(testMatch)
				.as("Does not have old name")
				.doesNotContain("\"Barbara\"");

		assertThat(testMatch)
				.as("Check new phone present")
				.contains(pnumber);
		assertThat(testMatch)
				.as("Check orig phone present")
				.contains("555-555-5555");
		// Note that Barb Jenson had an existing manager and so existing value should still be present.
		assertThat(testMatch)
				.as("Check new username present")
				.contains(newUsername);

		// Test remove
		user1.removeValue(uname);
		user1.removeValue(phoneNum);

		user1.addValue(number);
		testMatch = user1.toJsonString();
		assertThat(testMatch)
				.as("Check new phone present")
				.contains(pnumber);
		assertThat(testMatch)
				.as("Check orig phone NOT present")
				.doesNotContain("555-555-5555");
		// No usernames should be present
		assertThat(testMatch)
				.as("Check username not present")
				.doesNotContain("\"userName\"");




	}

	@Test
	public void f_AddRemoveExtValueTest() throws SchemaException {
		String refMatch = "/Users/dummyManager";
		String origMatch = "John Smith";
		String refMatchName = "Test Manager";
		Attribute manager = smgr.findAttribute("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:manager",null);
		StringValue displayName = new StringValue(manager.getSubAttribute("displayName"),refMatchName);
		ReferenceValue referenceValue = new ReferenceValue(manager.getSubAttribute("$ref"),refMatch);
		LinkedHashMap<Attribute,Value> map = new LinkedHashMap<>();
		map.put(manager.getSubAttribute("displayName"),displayName);
		map.put(manager.getSubAttribute("$ref"),referenceValue);
		ComplexValue newManager = new ComplexValue(manager,map);

		user1.addValue(newManager);

		String testMatch = user1.toJsonString();
		assertThat(testMatch)
				.as("Check test manager present")
				.contains(refMatch);
		assertThat(testMatch)
				.as("Check test manager present")
				.contains(refMatchName);
		// Note that Barb Jenson had an existing manager and so existing value should still be present.
		assertThat(testMatch)
				.as("Check John Smith still present")
				.contains(origMatch);

		// Now lets remove the attribute
		user1.removeValue(manager);
		testMatch = user1.toJsonString();
		assertThat(testMatch)
				.as("Check test manager present")
				.doesNotContain(refMatchName);
		// Note that Barb Jenson had an existing manager and so existing value should still be present.
		assertThat(testMatch)
				.as("Check John Smith still present")
				.doesNotContain(origMatch);

		// Now lets put the new value back
		user1.addValue(newManager);

		testMatch = user1.toJsonString();
		assertThat(testMatch)
				.as("Check test manager $ref present")
				.contains(refMatch);
		assertThat(testMatch)
				.as("Check test manager displayName present")
				.contains(refMatchName);
		// Note that Barb Jenson had an existing manager and so existing value should not be present.
		assertThat(testMatch)
				.as("Check manager John Smith not present")
				.doesNotContain(origMatch);
	}

	@Test
	public void g_RevisionTest() throws ScimException {
		RequestCtx ctx = new RequestCtx("/Uses",user1.getId(),null,smgr);

		try {
			Date curDate = Date.from(Instant.now());
			user1.getMeta().addRevision(ctx,handler.getProvider(),curDate);

			MultiValue revision = user1.getMeta().getRevisions();

			assertThat(revision.getRawValue().length).isEqualTo(1);

			// simulate new revision
			ctx = new RequestCtx("/Uses",user1.getId(),null,smgr);
			user1.getMeta().addRevision(ctx,handler.getProvider(), Date.from(Instant.now()));
			int cnt = 0;
			revision = user1.getMeta().getRevisions();
			for(Value val : revision.getRawValue()) {
				String record = val.toString(revision.getAttribute());
				logger.debug("Version: "+record);
				cnt++;
			}
			assertThat(cnt).isEqualTo(2);

			String rec = user1.toJsonString();
			assertThat(rec)
					.as("JsonNode of user1 has revisions")
					.contains(ctx.getTranId());

			assertThat(rec)
					.as("JsonNode of user1 has revisions")
					.contains("date\":\""+Meta.ScimDateFormat.format(curDate));
		} catch (BackendException e) {
			fail("Received erroneous duplicate transaction exception: "+e.getMessage(),e);
		}
	}

	@Test
	public void h_patchTests() throws ScimException, IOException, ParseException {
		logger.info("H. Checking Patch functions");

		//reload user1
		InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
		assert userStream != null;

		JsonNode node = JsonUtil.getJsonTree(userStream);
		user1 = new ScimResource(smgr,node, "Users");

		Attribute titleAttr = smgr.findAttribute("User:title",null);
		Attribute phoneAttr = smgr.findAttribute("User:phoneNumbers",null);
		Attribute valAttr = phoneAttr.getSubAttribute("value");
		Attribute typAttr = phoneAttr.getSubAttribute("type");
		StringValue val = new StringValue(valAttr,"987-654-3210");
		StringValue type = new StringValue(typAttr,"test");

		Map<Attribute,Value> map = new HashMap<>();
		map.put(valAttr,val);
		map.put(typAttr,type);
		ComplexValue phoneVal = new ComplexValue(phoneAttr,map);

		StringValue titleVal = new StringValue(titleAttr,"TEST TITLE");
		// Simple attribute add test
		JsonPatchOp addTitleOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_ADD,"User:title",titleVal); // this should convert to replace since title is single-value

		// CMV add value test
		JsonPatchOp addPhoneNumberOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_ADD,"User:phoneNumbers",phoneVal);

		// Resource level add (no path)
		String jvalStr = "{\"emails\": [{\"value\":\"test@example.com\",\"type\":\"TEST\"}],\"externalId\": \"1234567890\"}";
		String jsonOP = "{\"op\": \"add\",\"value\": "+jvalStr+"}";
		JsonNode opJsonNode = JsonUtil.getJsonTree(jsonOP);

		RequestCtx ctx = new RequestCtx("/Users",smgr);
		JsonPatchOp addResourceLevelOp = new JsonPatchOp(opJsonNode, ctx);

		logger.info("\t... checking JsonPatchOp serialization");
		JsonNode pnode = addPhoneNumberOp.toJsonNode();
		//logger.info("PatchOp:\n"+pnode.toPrettyString());

		JsonPatchOp patchOp2 = new JsonPatchOp(pnode, ctx);
		assertThat(addPhoneNumberOp.path)
				.as("Check paths are the same")
				.isEqualTo(patchOp2.path);
		assertThat(addPhoneNumberOp.op)
				.as("Check operation are the same")
				.isEqualTo(patchOp2.op);

		assertThat(addPhoneNumberOp.jsonValue.asText())
				.as("Check values are the same")
				.isEqualTo(patchOp2.jsonValue.asText());

		logger.info("\t... Checking JsonPatchRequest serialization");

		// build a json patch request from json
		ObjectNode reqJson = JsonUtil.getMapper().createObjectNode();
		ArrayNode snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
		snode.add(ScimParams.SCHEMA_API_PatchOp);

		ArrayNode anode = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);
		anode.add(addTitleOp.toJsonNode());
		anode.add(patchOp2.toJsonNode());
		anode.add(addResourceLevelOp.toJsonNode());


		logger.info("\t\t raw request:\n"+reqJson.toPrettyString());
		ctx = new RequestCtx(user1.getMeta().getLocation(),null,null,smgr);
		JsonPatchRequest jpr = new JsonPatchRequest(reqJson, ctx);

		assertThat(jpr.getSize())
				.as("Check three operations parsed")
				.isEqualTo(3);

		logger.info("\t... Performing patch add tests on user1");
		user1.modifyResource(jpr,ctx);


		StringValue titleValue = (StringValue) user1.getValue(titleAttr);
		assertThat(titleValue.getRawValue())
				.as("Check title was changed to TEST TITLE")
				.isEqualTo("TEST TITLE");
		Value phoneValues = user1.getValue(phoneAttr);
		assertThat(phoneValues)
				.as("phonenumbers is a multivalue")
				.isInstanceOf(MultiValue.class);
		MultiValue values = (MultiValue) phoneValues;
		assertThat(values.size())
				.as("There should be 3 phone numbers")
				.isEqualTo(3);
		Filter filter = Filter.parseFilter("type eq test","phoneNumbers",ctx);
		Value pval = values.getMatchValue(filter);
		assertThat(pval).isNotNull();
		assertThat(pval).isInstanceOf(ComplexValue.class);
		Value numbval = ((ComplexValue)pval).getValue(valAttr);
		assertThat(numbval).isInstanceOf(StringValue.class);
		StringValue numStringVal = (StringValue) numbval;
		assertThat(numStringVal.getRawValue())
				.as("Has correct value assigned")
				.isEqualTo("987-654-3210");

		Attribute emailAttr = smgr.findAttribute("emails",null);
		Value emailVals = user1.getValue(emailAttr);
		filter = Filter.parseFilter("type eq TEST","emails",ctx);
		assertThat(filter.isMatch(emailVals))
				.as("TEST number is present")
				.isTrue();

		Attribute extAttr = smgr.findAttribute("externalId",null);
		Value extVal = user1.getValue(extAttr);
		assertThat(extVal.getRawValue())
				.as("External id was set to 1234567890")
				.isEqualTo("1234567890");

		logger.info("\t... Performing patch replace test");

		jpr = new JsonPatchRequest();

		titleVal = new StringValue(titleAttr,"REP TITLE");
		JsonPatchOp replaceTitleOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE,"User:title",titleVal);

		jpr.addOperation(replaceTitleOp);

		map = new HashMap<>();
		map.put(valAttr,val);
		type = new StringValue(typAttr,"other");
		map.put(typAttr,type);
		phoneVal = new ComplexValue(phoneAttr,map);
		JsonPatchOp replacePhoneOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE,"phoneNumbers[type eq test]",phoneVal);
		jpr.addOperation(replacePhoneOp);

		val = new StringValue(valAttr,"111-1111");
		JsonPatchOp replacePhoneMobileOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE,"phoneNumbers[type eq mobile].value",val);
		jpr.addOperation(replacePhoneMobileOp);

		assertThat(jpr.getSize()).isEqualTo(3);
		user1.modifyResource(jpr,ctx);

		// Check replace results
		titleValue = (StringValue) user1.getValue(titleAttr);
		assertThat(titleValue.getRawValue())
				.as("Check title was changed to TEST TITLE")
				.isEqualTo("REP TITLE");
		phoneValues = user1.getValue(phoneAttr);
		assertThat(phoneValues)
				.as("phonenumbers is a multivalue")
				.isInstanceOf(MultiValue.class);
		values = (MultiValue) phoneValues;
		filter = Filter.parseFilter("type eq other","phoneNumbers",ctx);
		pval = values.getMatchValue(filter);
		assertThat(pval).isNotNull();
		assertThat(pval).isInstanceOf(ComplexValue.class);
		numbval = ((ComplexValue)pval).getValue(valAttr);
		assertThat(numbval).isInstanceOf(StringValue.class);
		numStringVal = (StringValue) numbval;
		assertThat(numStringVal.getRawValue())
				.as("Has correct value assigned")
				.isEqualTo("987-654-3210");

		filter = Filter.parseFilter("type eq mobile","phoneNumbers",ctx);
		pval = values.getMatchValue(filter);
		assertThat(pval).isNotNull();
		assertThat(pval).isInstanceOf(ComplexValue.class);
		numbval = ((ComplexValue)pval).getValue(valAttr);
		assertThat(numbval).isInstanceOf(StringValue.class);
		numStringVal = (StringValue) numbval;
		assertThat(numStringVal.getRawValue())
				.as("Has correct value assigned")
				.isEqualTo("111-1111");

		logger.info("\t... Performing patch delete tests");


		List<JsonPatchOp> ops = new ArrayList<>();

		JsonPatchOp deleteTitleOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE,"User:title",null);
		ops.add(deleteTitleOp);

		JsonPatchOp deletePhoneValOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE,"phoneNumbers[type eq mobile]",null);
		ops.add(deletePhoneValOp);

		JsonPatchOp deleteSubAttrOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE,"name.honorificSuffix",null);
		ops.add(deleteSubAttrOp);

		jpr = new JsonPatchRequest(ops);
		assertThat(jpr.getSize()).isEqualTo(3);
		user1.modifyResource(jpr,ctx);

		Value value = user1.getValue(titleAttr);
		assertThat(value)
				.as("Confirm title removed")
				.isNull();
		Attribute nameAttr = smgr.findAttribute("User:name",null);
		value = user1.getValue(nameAttr);
		assertThat(value)
				.isInstanceOf(ComplexValue.class);
		ComplexValue nval = (ComplexValue) value;
		Value honorsufficVal = nval.getValue(nameAttr.getSubAttribute("honorificSuffix"));
		assertThat(honorsufficVal)
				.as("Confirm sub attribute removed")
				.isNull();
		Value famNam = nval.getValue(nameAttr.getSubAttribute("familyName"));
		assertThat(famNam).isNotNull();
		StringValue famStringVal = (StringValue) famNam;
		assertThat(famStringVal.getRawValue())
				.as("Familiy name still present as Jensen")
				.isEqualTo("Jensen");

		phoneValues = user1.getValue(phoneAttr);
		assertThat(phoneValues)
				.as("phonenumbers is a multivalue")
				.isInstanceOf(MultiValue.class);

		values = (MultiValue) phoneValues;
		assertThat(values.size())
				.as("Should be 2 numbers left")
				.isEqualTo(2);
		filter = Filter.parseFilter("type eq other","phoneNumbers",ctx);
		pval = values.getMatchValue(filter);
		assertThat(pval).isNotNull();
		assertThat(pval).isInstanceOf(ComplexValue.class);
		numbval = ((ComplexValue)pval).getValue(valAttr);
		assertThat(numbval).isInstanceOf(StringValue.class);
		numStringVal = (StringValue) numbval;
		assertThat(numStringVal.getRawValue())
				.as("Other phone number is still present")
				.isEqualTo("987-654-3210");

		logger.info("... Testing faulty ops");
		ops.clear();

		JsonPatchOp faultyFilterOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REMOVE,"phoneNumbers[type eq blah].value",null);
		ops.add(faultyFilterOp);

		jpr = new JsonPatchRequest(ops);
		boolean hadCorrectError = false;
		try {
			user1.modifyResource(jpr,ctx);
		} catch (ScimException e) {
			assertThat(e)
					.as("check for NoTargetException")
					.isInstanceOf(NoTargetException.class);
			hadCorrectError = true;
		}
		assertThat(hadCorrectError)
				.as("Properly threw no targetexception")
				.isTrue();

		// Generate InvalidValueException
		reqJson = JsonUtil.getMapper().createObjectNode();
		snode = reqJson.putArray(ScimParams.ATTR_SCHEMAS);
		snode.add(ScimParams.SCHEMA_API_PatchOp);

		anode = reqJson.putArray(ScimParams.ATTR_PATCH_OPS);

		faultyFilterOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE,"phoneNumbers[type eq blah].value",null);
		anode.add(faultyFilterOp.toJsonNode());

		hadCorrectError = false;
		try {
			new JsonPatchRequest(reqJson, ctx);
		} catch (InvalidValueException e) {
			hadCorrectError = true;
		}
		assertThat(hadCorrectError)
				.as("Properly threw no InvalidValueException")
				.isTrue();

		// Generate NoTarget exception

		Attribute phoneValueAttr = smgr.findAttribute("phoneNumbers.value",null);
		Value testVal = new StringValue(phoneValueAttr, "633-1234");
		faultyFilterOp = new JsonPatchOp(JsonPatchOp.OP_ACTION_REPLACE,"phoneNumbers[type eq blah].value",testVal);

		jpr = new JsonPatchRequest();
		jpr.addOperation(faultyFilterOp);

		hadCorrectError = false;
		try {
			user1.modifyResource(jpr,ctx);
		} catch (ScimException e) {
			assertThat(e)
					.as("check for NoTargetException")
					.isInstanceOf(NoTargetException.class);
			hadCorrectError = true;
		}
		assertThat(hadCorrectError)
				.as("Properly threw no targetexception")
				.isTrue();
	}
	


}
