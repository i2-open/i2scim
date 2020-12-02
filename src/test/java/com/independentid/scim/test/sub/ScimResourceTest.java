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


import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ExtensionValues;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;



@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ScimResourceTest {
	
	private final Logger logger = LoggerFactory.getLogger(ScimResourceTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

	@Inject
	@Resource(name="ConfigMgr")
	ConfigMgr cmgr ;
	
	final static String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	final static String testUserFile2 = "classpath:/schema/TestUser-jsmith.json";
	
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
			InputStream userStream = cmgr.getClassLoaderFile(testUserFile1);
			//InputStream userStream = this.resourceloader.getResource(testUserFile1).getInputStream();
			JsonNode node = JsonUtil.getJsonTree(userStream);
			user1 = new ScimResource(cmgr,node, "Users");
			logger.debug("User loaded: \n"+user1.toString());

			assert userStream != null;
			userStream.close();
			
			userStream = cmgr.getClassLoaderFile(testUserFile2);
			node = JsonUtil.getJsonTree(userStream);
			user2 = new ScimResource(cmgr,node, "Users");
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
		
			assertThat(sval.getValueArray())
				.as("Check that department is Tour Operations")
				.isEqualTo("Tour Operations");
			
		} catch (IOException | ParseException | ScimException e) {
			Assertions.fail("Exception occured while parsing test user bjensen. "+e.getMessage(),e);
		}
		
		
	}
	


}
