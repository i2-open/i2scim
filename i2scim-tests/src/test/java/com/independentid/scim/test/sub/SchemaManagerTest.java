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
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.*;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SchemaManagerTest {
	
	private final Logger logger = LoggerFactory.getLogger(SchemaManagerTest.class);

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	@Test
	public void a_ConfigTest() {
		
		
		logger.info("==========Schema Manager Tests==========");

		assertThat(smgr).isNotNull();

		logger.debug("\t\tChecking Schema");

		Collection<Schema> scol =  smgr.getSchemas();
		assertThat(scol).isNotNull();
		assertThat(scol).isNotEmpty();

		for (Schema type : scol) {
			logger.debug("\t\tSchema: "+type.getName()+", Id: "+type.getId());
		}
		logger.debug("\t\tSchema types loaded: "+scol.size());
		assertThat(scol.size()).isGreaterThan(1);

		logger.debug("\t\tChecking Resource Types");
		
		Collection<ResourceType> rcol = smgr.getResourceTypes();
		
		assertThat(rcol).isNotNull();
		assertThat(rcol).isNotEmpty();
		
		Iterator<ResourceType> iter = rcol.iterator();
		ResourceType uitem = null;
		int rCnt = 0;
		logger.debug("\tResource Types Loaded...");
		while (iter.hasNext()) {
			ResourceType item = iter.next();
			
			logger.debug("\t\tType: "+item.getName()+", Endpoint: " + item.getTypePath());
			if (item.getTypePath().equalsIgnoreCase("users")) {
				uitem = item;
			}
			rCnt++;
		}
		
		assertThat(rCnt).isGreaterThan(0);
		logger.debug("\t\tCounted "+rCnt+" resource types.");

		assertThat(rCnt).isEqualTo(5);
		
		logger.debug("\tChecking presence of User endpoint definition...");
		assertThat(uitem).isNotNull();
		assertThat(uitem.getTypePath()).isEqualTo("Users");

	}
	
	@Test
	public void b_findAttributeNoCtxTest() {
		logger.info("\tFind attribute with null RequestCtx test");
		Attribute attr = smgr.findAttribute("name.middleName", null);
		
		// Should be null because the wrong name will match
		assertThat(attr)
			.as("Check attribute returned")
			.isNotNull();
		assertThat(attr.getSchema())
			.as("Check that User schema id is urn:ietf:params:scim:schemas:core:2.0:User")
			.isEqualTo("urn:ietf:params:scim:schemas:core:2.0:User");
			
		
		Attribute aUserName = smgr.findAttribute("User:name", null);
		assertThat(aUserName)
		.as("Check User:name attribute returned")
		.isNotNull();
		
		Attribute aUserNameMiddle = smgr.findAttribute("User:name.middleName", null);
		assertThat(aUserNameMiddle)
		.as("Check User:name.middleName attribute returned")
		.isNotNull();
		
		assertThat(aUserName.getSubAttribute("middleName"))
			.as("Check that middleName is a sub attribute of User:name")
			.isEqualTo(aUserNameMiddle);
		
		Attribute afullpath = smgr.findAttribute("urn:ietf:params:scim:schemas:core:2.0:User:name.middleName", null);
		assertThat(afullpath)
			.as("Check urn:ietf:params:scim:schemas:core:2.0:User:name.middleName is same as name.middleName")
			.isEqualTo(attr);
		
		// Fetch the id attribute via its full path
		Attribute id = smgr.findAttribute("Common:id", null);
		assertThat(id)
			.as("Check the id attribute is defined")
			.isNotNull();
		assertThat(id.getReturned())
			.as("Id returnability is always")
			.isEqualTo("always");
		assertThat(id.getSchema())
			.as("Schema of id attribute is Common")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Common);
		
		// Now check that id is retrievable by short name
		id = smgr.findAttribute("id", null);
		assertThat(id)
			.as("Check the id attribute is found")
			.isNotNull();
		assertThat(id.getReturned())
			.as("Id returnability is always")
			.isEqualTo("always");
		assertThat(id.getSchema())
			.as("Schema of id attribute is Common")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Common);
		
		Attribute org = smgr.findAttribute("organization", null);
		
		assertThat(org)
			.as("Check that Enterprise Organization Fouund")
			.isNotNull();
		assertThat(org.getSchema())
			.as("Check enterprise schema for organization is correct.")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Ent_User);
		
	}
	
	@Test
	public void c_findAttributesCtxTest() {
		logger.info("\tFind Attributes with RequestCtx test");

		RequestCtx ctx = null;
		try {
			ctx = new RequestCtx("Users",null,"userName eq dummy", smgr);
		} catch (ScimException e) {
			fail("Error creating RequestCtx: "+e.getLocalizedMessage(),e);
		}
		
		assertThat(ctx)
			.as("Check RequestCtx is asserted")
			.isNotNull();
		
		Attribute attr = smgr.findAttribute("name.middleName", ctx);
		
		// Should be null because the wrong name will match
		assertThat(attr)
			.as("Check attribute returned")
			.isNotNull();
		assertThat(attr.getSchema())
			.as("Check that User schema id is urn:ietf:params:scim:schemas:core:2.0:User")
			.isEqualTo("urn:ietf:params:scim:schemas:core:2.0:User");
			
		
		Attribute aUserName = smgr.findAttribute("User:name", ctx);
		assertThat(aUserName)
		.as("Check User:name attribute returned")
		.isNotNull();
		
		Attribute aUserNameMiddle = smgr.findAttribute("User:name.middleName", ctx);
		assertThat(aUserNameMiddle)
		.as("Check User:name.middleName attribute returned")
		.isNotNull();
		
		assertThat(aUserName.getSubAttribute("middleName"))
			.as("Check that middleName is a sub attribute of User:name")
			.isEqualTo(aUserNameMiddle);
		
		Attribute afullpath = smgr.findAttribute("urn:ietf:params:scim:schemas:core:2.0:User:name.middleName", ctx);
		assertThat(afullpath)
			.as("Check urn:ietf:params:scim:schemas:core:2.0:User:name.middleName is same as name.middleName")
			.isEqualTo(attr);
		
		// Fetch the id attribute via its full path
		Attribute id = smgr.findAttribute("Common:id", ctx);
		assertThat(id)
			.as("Check the id attribute is defined")
			.isNotNull();
		assertThat(id.getReturned())
			.as("Id returnability is always")
			.isEqualTo("always");
		assertThat(id.getSchema())
			.as("Schema of id attribute is Common")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Common);
		
		// Now check that id is retrievable by short name
		id = smgr.findAttribute("id", ctx);
		assertThat(id)
			.as("Check the id attribute is found")
			.isNotNull();
		assertThat(id.getReturned())
			.as("Id returnability is always")
			.isEqualTo("always");
		assertThat(id.getSchema())
			.as("Schema of id attribute is Common")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Common);
		
		Attribute org = smgr.findAttribute("organization", ctx);
		
		assertThat(org)
			.as("Check that Enterprise Organization Fouund")
			.isNotNull();
		assertThat(org.getSchema())
			.as("Check enterprise schema for organization is correct.")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Ent_User);

	}

	@Test
	public void d_addSchemaAndResTypeTest()  {
		Schema a = new Schema(smgr);
		a.setId("urn:bla.de.blah.TEST");
		a.setName("TestSchema");
		Attribute name = smgr.findAttribute("User","name",null,null);


        //copy the name
		Attribute testAttr = null;
		try {  // yeah yeah...we don't have a clone method
			StringWriter writer = new StringWriter();
			JsonGenerator gen = JsonUtil.getGenerator(writer,true);
			name.serialize(gen,null,false);
			gen.close();
			writer.close();
			String jsonStr = writer.toString();
			JsonNode json = JsonUtil.getJsonTree(jsonStr);
			testAttr = new Attribute(json,null);
			testAttr.setPath(a.getId(),name.getName());
		} catch (SchemaException | IOException e) {
			e.printStackTrace();
		}
		assertThat(testAttr).isNotNull();

		a.putAttribute(testAttr);

		smgr.addSchema(a);

		Attribute nameCompare = smgr.findAttribute(a.getId(),"name",null,null);

		assertThat(nameCompare)
				.as("Added attribute was found")
				.isNotNull();
		assertThat(nameCompare.getSchema())
				.as("New name attribute associated with Test schema")
				.isEqualTo(a.getId());

		Attribute userName = smgr.findAttribute("User","name",null,null);
		assertThat(userName).isNotNull();
		assertThat(userName)
				.as("User name has not been changed")
				.isEqualTo(name);
		assertThat(userName)
				.as("Check test:Name not the same as User:Name")
				.isNotEqualTo(nameCompare);


		ResourceType type = new ResourceType(smgr);
		type.setId("urn:bla.de.blah.TEST");
		type.setSchema(a.getId());
		try {
			type.setEndpoint(new URI("/Tests"));
		} catch (URISyntaxException e) {
			e.printStackTrace();
			fail("Invalid URL");
		}
		type.setName("Test");

		try {
			smgr.addResourceType(type);
		} catch (SchemaException e) {
			e.printStackTrace();
			fail("Failed to add Test ResourceType");
		}

		ResourceType typeCopy = smgr.getResourceTypeById(type.getId());
		assertThat(typeCopy).isNotNull();
		assertThat(typeCopy)
				.as("Check new type is equal to the old")
				.isEqualTo(type);
	}

}
