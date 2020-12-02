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


import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
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
	@Resource(name="ConfigMgr")
	ConfigMgr cmgr ;
	
	
	/**
	 * This test checks that the resource types file was parsed and that at least one object parsed correctly.
	 */
	@Test
	public void a_resourceTypeTest() {
		
		logger.info("========== Resource Test ==========");

		//ConfigMgr mgr = (ConfigMgr) ctx.getBean("Configmgr");
		
		// Test the User object
		ResourceType typ = cmgr.getResourceType("User");
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
		Schema userSchema = cmgr.getSchemaById(userSchemaId);
		
		assertThat(userSchema).isNotNull();
		
		assertThat(userSchema.getName()).isEqualTo("User");
		
		Attribute name = userSchema.getAttribute("name");
		assertThat(name).isNotNull();
		
		Map<String,Attribute> subs = name.getSubAttributesMap();
		//There should be 6 parts to the complex attribute name (formatted, familyName, givenName, middleName, honorificPrefix, honorificSuffic)
		assertThat(subs).isNotNull().hasSize(6);
		
	}
	

}
