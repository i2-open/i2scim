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
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ConfigMgrTest {
	
	private final Logger logger = LoggerFactory.getLogger(ConfigMgrTest.class);

	@Inject
	@Resource(name="ConfigMgr")
	ConfigMgr cmgr;

	@Test
	public void a_ConfigTest() {
		
		
		logger.info("==========ConfigMgr Tests==========");
		logger.info("  Checking ConfigMgr bean instantiated.");
		
		assertThat(cmgr).isNotNull();
		
		// These values should be returning default values and should not fail.
		assertThat(cmgr.getResourceTypePath()).isNotNull();
		logger.info("\tResource Type file path="+cmgr.getResourceTypePath());
		
		assertThat(cmgr.getSchemaPath()).isNotNull();
				
		logger.debug("\t\tGetting Resource Types");
		
		Collection<ResourceType> rcol = cmgr.getResourceTypes();
		
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
		Attribute attr = cmgr.findAttribute("name.middleName", null);
		
		// Should be null because the wrong name will match
		assertThat(attr)
			.as("Check attribute returned")
			.isNotNull();
		assertThat(attr.getSchema())
			.as("Check that User schema id is urn:ietf:params:scim:schemas:core:2.0:User")
			.isEqualTo("urn:ietf:params:scim:schemas:core:2.0:User");
			
		
		Attribute aUserName = cmgr.findAttribute("User:name", null);
		assertThat(aUserName)
		.as("Check User:name attribute returned")
		.isNotNull();
		
		Attribute aUserNameMiddle = cmgr.findAttribute("User:name.middleName", null);
		assertThat(aUserNameMiddle)
		.as("Check User:name.middleName attribute returned")
		.isNotNull();
		
		assertThat(aUserName.getSubAttribute("middleName"))
			.as("Check that middleName is a sub attribute of User:name")
			.isEqualTo(aUserNameMiddle);
		
		Attribute afullpath = cmgr.findAttribute("urn:ietf:params:scim:schemas:core:2.0:User:name.middleName", null);
		assertThat(afullpath)
			.as("Check urn:ietf:params:scim:schemas:core:2.0:User:name.middleName is same as name.middleName")
			.isEqualTo(attr);
		
		// Fetch the id attribute via its full path
		Attribute id = cmgr.findAttribute("Common:id", null);
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
		id = cmgr.findAttribute("id", null);
		assertThat(id)
			.as("Check the id attribute is found")
			.isNotNull();
		assertThat(id.getReturned())
			.as("Id returnability is always")
			.isEqualTo("always");
		assertThat(id.getSchema())
			.as("Schema of id attribute is Common")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Common);
		
		Attribute org = cmgr.findAttribute("organization", null);
		
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
			ctx = new RequestCtx("Users",null,"userName eq dummy", cmgr);
		} catch (ScimException e) {
			fail("Error creating RequestCtx: "+e.getLocalizedMessage(),e);
		}
		
		assertThat(ctx)
			.as("Check RequestCtx is asserted")
			.isNotNull();
		
		Attribute attr = cmgr.findAttribute("name.middleName", ctx);
		
		// Should be null because the wrong name will match
		assertThat(attr)
			.as("Check attribute returned")
			.isNotNull();
		assertThat(attr.getSchema())
			.as("Check that User schema id is urn:ietf:params:scim:schemas:core:2.0:User")
			.isEqualTo("urn:ietf:params:scim:schemas:core:2.0:User");
			
		
		Attribute aUserName = cmgr.findAttribute("User:name", ctx);
		assertThat(aUserName)
		.as("Check User:name attribute returned")
		.isNotNull();
		
		Attribute aUserNameMiddle = cmgr.findAttribute("User:name.middleName", ctx);
		assertThat(aUserNameMiddle)
		.as("Check User:name.middleName attribute returned")
		.isNotNull();
		
		assertThat(aUserName.getSubAttribute("middleName"))
			.as("Check that middleName is a sub attribute of User:name")
			.isEqualTo(aUserNameMiddle);
		
		Attribute afullpath = cmgr.findAttribute("urn:ietf:params:scim:schemas:core:2.0:User:name.middleName", ctx);
		assertThat(afullpath)
			.as("Check urn:ietf:params:scim:schemas:core:2.0:User:name.middleName is same as name.middleName")
			.isEqualTo(attr);
		
		// Fetch the id attribute via its full path
		Attribute id = cmgr.findAttribute("Common:id", ctx);
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
		id = cmgr.findAttribute("id", ctx);
		assertThat(id)
			.as("Check the id attribute is found")
			.isNotNull();
		assertThat(id.getReturned())
			.as("Id returnability is always")
			.isEqualTo("always");
		assertThat(id.getSchema())
			.as("Schema of id attribute is Common")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Common);
		
		Attribute org = cmgr.findAttribute("organization", ctx);
		
		assertThat(org)
			.as("Check that Enterprise Organization Fouund")
			.isNotNull();
		assertThat(org.getSchema())
			.as("Check enterprise schema for organization is correct.")
			.isEqualTo(ScimParams.SCHEMA_SCHEMA_Ent_User);

	}

	@Test
	public void d_findResourceTest() {
		logger.info("\tTesting file loaders");

		String schemaPath = cmgr.getSchemaPath();

		assertThat(schemaPath).isNotNull();

		try {
			File sFile = cmgr.findClassLoaderResource(schemaPath);
			assertThat(sFile).isNotNull();

			assertThat(sFile.exists())
					.as("Schema File located")
					.isTrue();

			InputStream stream = cmgr.getClassLoaderFile(schemaPath);
			byte[] bytes = stream.readAllBytes();
			stream.close();
			assertThat(bytes.length)
					.as("Schema file readable")
					.isGreaterThan(10);
		} catch (IOException e) {
			fail(e.getLocalizedMessage());
		}

	}

}
