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

package com.independentid.scim.test.sub;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
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
import java.util.LinkedHashMap;
import java.util.LinkedList;

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

		try {
			InputStream userStream = ConfigMgr.getClassLoaderFile(testUserFile1);
			assert userStream != null;

			//InputStream userStream = this.resourceloader.getResource(testUserFile1).getInputStream();
			JsonNode node = JsonUtil.getJsonTree(userStream);
			user1 = new ScimResource(smgr,node, "Users");
			String outString = user1.toString();
			logger.debug("User loaded: \n"+ outString);
			assertThat(outString)
					.as("User1 contains the certificate")
					.contains(certMatch);

			userStream.close();
			
			userStream = ConfigMgr.getClassLoaderFile(testUserFile2);
			node = JsonUtil.getJsonTree(userStream);
			user2 = new ScimResource(smgr,node, "Users");
			logger.debug("User loaded: \n"+user2.toString());
			
			assertThat(user1)
				.as("SCIM User BJensen Parse Test")
				.isNotNull();
			
			assertThat(user2)
				.as("SCIM User JSmith Parse Test")
				.isNotNull();

			Attribute userAttr = user1.getAttribute("userName", null);
			StringValue userValue = (StringValue) user1.getValue(userAttr);
			assertThat(userValue.value)
				.as("Has username value of bjensen")
				.isEqualTo("bjensen@example.com");
			
			userValue = (StringValue) user2.getValue(userAttr);
			assertThat(userValue.value)
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
					logger.info("Difference: "+adif.toString());
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
			for (DiffMatchPatch.Diff adif : diff) {
				if (!adif.operation.equals(DiffMatchPatch.Operation.EQUAL))
					logger.info("Difference: "+adif.toString());
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
	public void d_copyTest() {
		try {
			JsonNode jnode = user1.toJsonNode(null);

			ScimResource copyRes = user1.copy(null);

			String original = user1.toString();
			String copy = copyRes.toString();

			DiffMatchPatch dmp = new DiffMatchPatch();
			LinkedList<DiffMatchPatch.Diff> diff = dmp.diffMain(original,copy,false);
			for (DiffMatchPatch.Diff adif : diff) {
				if (!adif.operation.equals(DiffMatchPatch.Operation.EQUAL))
					logger.info("Difference: "+adif.toString());
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
	


}
