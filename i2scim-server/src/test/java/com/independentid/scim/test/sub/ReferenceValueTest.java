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

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.resource.ReferenceValue;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ReferenceValueTest {

	//private Logger logger = LoggerFactory.getLogger(ReferenceValueTest.class);
	
	static String refAttrString = " {\n" + 
			"            \"name\" : \"$ref\",\n" + 
			"            \"type\" : \"reference\",\n" + 
			"            \"referenceTypes\" : [\n" + 
			"              \"User\",\n" + 
			"              \"Group\"\n" + 
			"            ],\n" + 
			"            \"multiValued\" : false,\n" + 
			"            \"description\" : \"The URI of the corresponding Group resource to which the user belongs\",\n" + 
			"            \"readOnly\" : false,\n" + 
			"            \"required\" : false,\n" + 
			"            \"caseExact\" : false,\n" + 
			"            \"mutability\" : \"readOnly\",\n" + 
			"            \"returned\" : \"default\",\n" + 
			"            \"uniqueness\" : \"none\"\n" + 
			"          }";
	static String inputString1 = "{\"$ref\": \"/Groups/e9e30dba-f08f-4109-8486-d5c6a331660a\"}";
	static String matchString1 = "/Groups/e9e30dba-f08f-4109-8486-d5c6a331660a";
	static String inputString2 = "{\"$ref\": \"https://example.com/v2/Groups/e9e30dba-f08f-4109-8486-d5c6a331660a\"}";
	//static String matchString2 = "https://example.com/v2/Groups/e9e30dba-f08f-4109-8486-d5c6a331660a";
		
	
	static JsonNode jnodetest1, jnodetest2 = null;
	static ReferenceValue refval1,refval2 = null;
	static Attribute refAttr = null;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		JsonNode refNode = JsonUtil.getJsonTree(refAttrString);
		refAttr = new Attribute(refNode);
		
		jnodetest1 = JsonUtil.getJsonTree(inputString1);
		jnodetest2 = JsonUtil.getJsonTree(inputString2);
	}

	@Test
	void a_testReferenceValueAttributeJsonNode() {
		assertThat(jnodetest1).isNotNull();
		
		try {
			JsonNode node = jnodetest1.get("$ref");
			refval1 = new ReferenceValue(refAttr,node);
			node = jnodetest2.get("$ref");
			refval2 = new ReferenceValue(refAttr,node);
		} catch (SchemaException | ParseException e) {
			fail("Exception constructing ReferenceValue: "+e.getMessage(),e);
		}
		assertThat(refval1)
		  .isNotNull();
	}
	
	@Test
	void b_testEquals() {
		//Test that two difference URLs do not match
		assertThat(refval1)
			.as("ReferenceValue.equals(obj) test")
			.isNotEqualTo(refval2);
		
		//Test that two independent ReferenceValues for the same URL are a match
		JsonNode node = jnodetest2.get("$ref");
		try {
			ReferenceValue dup =  new ReferenceValue(refAttr,node);
			assertThat(refval2)
				.as("Reference Value equality test")
				.isEqualTo(dup);
		} catch (SchemaException | ParseException e) {
			//should not happen if previous test succeeded.
			fail("Failed attempting to instantiate ReferenceValue during equality test",e);
		}
	}
	
	@Test
	void c_testJsonOut() {
		//This exercises toString and the serialize function
		JsonNode jout;
		JsonNode valueComp = jnodetest1.get("$ref");
		jout = refval1.toJsonNode(null,"$ref");

		assertThat(jout.get("$ref"))
			.as("JsonNode value objects match.")
			.isEqualTo(valueComp);

		assertThat(jout)
				.as("Reference value JsonNodes match")
				.isEqualTo(jnodetest1);

	}

	@Test
	void d_testValue() {
		Object obj = refval1.getRawValue(); //pull the internal formatted URI
		Assertions.assertThat(obj)
			.as("Check value type is correct.")
			.isInstanceOf(java.net.URI.class);
		
		String compStr = refval1.toString(); //confirm that serialization produces relative address
		Assertions.assertThat(compStr)
			.as("Compare value string values")
			.isEqualTo(matchString1);
	}


}
