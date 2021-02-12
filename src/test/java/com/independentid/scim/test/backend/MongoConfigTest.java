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

package com.independentid.scim.test.backend;


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.PersistStateResource;
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
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;


@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MongoConfigTest {
	
	private static final Logger logger = LoggerFactory.getLogger(MongoConfigTest.class);

	@Inject
	BackendHandler handler;

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	static IScimProvider provider = null;

	@Test
	public void a_beanCheckTest() {

		try {
			provider = handler.getProvider();
		} catch (InstantiationException | ClassNotFoundException | BackendException e) {
			fail(e);
		}
		logger.info("==========   MongoConfig Tests ==========");
		 
		logger.info("* Running initial persistance provider checks");
		assertThat(provider).as("MongoProvider is defined.").isNotNull();
		
		assertThat(provider.ready()).as("MongoProvider is ready").isTrue();
	}
	
	@Test
	public void b_SchemaTest() {
		Schema userById = smgr.getSchemaById("urn:ietf:params:scim:schemas:core:2.0:User");
		Schema userByName = smgr.getSchemaByName("User");
		
		assertThat(userById == userByName).as("Is the same Schema config instance").isTrue();
		
		assertThat(userById).as("Schema content equality is true").isEqualTo(userByName);
		
	}

	@Test 
	public void c_ResTypeTest() {
		ResourceType resByEp = smgr.getResourceTypeByPath("Users");
		ResourceType resByName = smgr.getResourceTypeById("User");
		
		assertThat(resByEp == resByName).as("Is the same ResourceType config instance").isTrue();
		
		assertThat(resByEp).as("ResourceType content equality is true").isEqualTo(resByName);
		
	}

	@Test
	public void d_PersistedConfig() {
		
		logger.info("* Checking schema");

		Collection<Schema> schCol = smgr.getSchemas();
		Collection<ResourceType> resTypeCol = smgr.getResourceTypes();
		//schCol.forEach(sch -> logger.debug("Cfg Schema: {}", sch.getName()));
		
		int confSchCnt = schCol.size();
		logger.debug("  ConfigMgr loaded Schema count: "+confSchCnt);
		
		Collection<Schema> scol = null;
		try {
			scol = provider.loadSchemas();
			// Note; Config Schema always has one extra - "Common" for common attributes!
			//scol.forEach(sch -> logger.debug("Per Schema: {}", sch.getName()));
			assertThat(scol.size())
				.as("Persisted schema count matches ConfigMgr count: "+scol.size())
				.isEqualTo(confSchCnt-1); 
			
		} catch (ScimException e) {
			logger.error("Error while loading schema from Mongo: "+e.getMessage(),e);
			fail("ScimException while loading schema from Mongo: "+e.getMessage(),e);
		}
		
		int confResTypeCnt = resTypeCol.size();
		logger.debug("  ConfigMgr loaded Resource Type count: "+confResTypeCnt);
		
		Collection<ResourceType> rcol = null;
		try {
			rcol = provider.loadResourceTypes();
			assertThat(rcol.size())
				.as("Persisted resource type count matches ConfigMgr count: "+rcol.size())
				.isEqualTo(confResTypeCnt);
		} catch (ScimException e) {
			logger.error("Error while loading resource types: "+e.getMessage(),e);
			fail("Error while loading resource types: "+e.getMessage(),e);
		}
		
		try {
			MongoProvider mprovider = (MongoProvider) provider;
			PersistStateResource cnfRes = mprovider.getConfigState();
			assertThat(cnfRes)
				.as("Check for persisted config resource not Null")
				.isNotNull();
			assertThat(cnfRes.getSchemaCnt())
				.as("Check state schema count matches")
				.isEqualByComparingTo(scol.size());
			assertThat(cnfRes.getResTypeCnt())
				.as("Check state resource type count matches")
				.isEqualByComparingTo(rcol.size());
			
			assertThat(cnfRes.getLastSyncDate())
				.as("Check last sync date not null")
				.isNotNull();
			
			logger.debug("  Last sync date is "+cnfRes.getLastSync());
			
		} catch (ScimException | IOException | ParseException e) {
			logger.error("Error while loading persistant state config resource",e);
			fail("Error while loading persistant state config resource",e);
		}
	}
	

}
