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


import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class BasicResourceSchemaConfigTest {
	
	private final Logger logger = LoggerFactory.getLogger(BasicResourceSchemaConfigTest.class);
	
	private final static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";


	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	/**
	 * This test checks that the resource types file was parsed and that at least one object parsed correctly.
	 */
	@Test
	public void a_resourceTypeTest() {
		
		logger.info("========== Resource Test ==========");

		//ConfigMgr mgr = (ConfigMgr) ctx.getBean("Configmgr");
		
		// Test the User object
		ResourceType typ = smgr.getResourceTypeById("User");
		assertThat(typ.getEndpoint()).isEqualTo(URI.create("/Users"));
		assertThat(typ.getSchema()).isEqualTo("urn:ietf:params:scim:schemas:core:2.0:User");
		String[] schemaExts = typ.getSchemaExtension();
		assertThat(schemaExts).hasSize(1);
		assertThat(schemaExts[0]).isEqualTo("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
		
		
	}
	
	/**
	 * This test checks the schema was loaded into config mgr and checks one of the definitions.
	 */
	@Test
	public void b_schemaTest() {
		logger.info("==========  Schema Test  ==========");
		Schema userSchema = smgr.getSchemaById(userSchemaId);
		
		assertThat(userSchema).isNotNull();
		
		assertThat(userSchema.getName()).isEqualTo("User");
		
		Attribute name = userSchema.getAttribute("name");
		assertThat(name).isNotNull();
		
		Map<String,Attribute> subs = name.getSubAttributesMap();
		//There should be 6 parts to the complex attribute name (formatted, familyName, givenName, middleName, honorificPrefix, honorificSuffic)
		assertThat(subs).isNotNull().hasSize(6);
		
	}
	

}
