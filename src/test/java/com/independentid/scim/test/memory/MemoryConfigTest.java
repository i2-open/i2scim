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

package com.independentid.scim.test.memory;


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Note this test may have some timing issues when run in debug stepping mode. Tests b thru d expects schema to be initialized fairly
 * quickly.
 */
@QuarkusTest
@TestProfile(ScimMemoryTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MemoryConfigTest {
	
	private static final Logger logger = LoggerFactory.getLogger(MemoryConfigTest.class);


	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	@Inject
	BackendHandler handler;

	@ConfigProperty(name=MemoryProvider.PARAM_PERSIST_DIR, defaultValue=MemoryProvider.DEFAULT_PERSIST_DIR)
	String storeDir;

	@ConfigProperty(name=MemoryProvider.PARAM_PERSIST_FILE, defaultValue=MemoryProvider.DEFAULT_FILE)
	String storeFile;

	static IScimProvider provider = null;

	static Instant startTime = Instant.now();

	@Test
	public void a_beanCheckTest() {

		provider = handler.getProvider();
		assertThat(provider).isNotNull();

		logger.info("==========   MemoryConfig Tests ==========");
		 
		logger.info("\t* Running initial persistance provider checks");
		assertThat(provider).as("MemoryProvider is defined.").isNotNull();
		
		assertThat(provider.ready()).as("MemoryProvider is ready").isTrue();

		logger.info("\t* Check that default schema was loaded by SchemaManager");
		Schema userById = smgr.getSchemaById("urn:ietf:params:scim:schemas:core:2.0:User");
		Schema userByName = smgr.getSchemaByName("User");

		assertThat(userById == userByName).as("Is the same Schema config instance").isTrue();
		assertThat(userById).as("Schema content equality is true").isEqualTo(userByName);

		assertThat(smgr.isSchemaLoadedFromProvider())
				.as("Confirm schema was loaded from default")
				.isFalse();
	}

	/**
	 * After the initial reset boot up, syncConfig should have been run. If so, a {@link PersistStateResource} should
	 * be available
	 */
	@Test
	public void b_checkForConfigState() throws ParseException, ScimException, IOException {
		logger.info("\t* Checking for stored PersistStateResource");
		PersistStateResource cfgState = waitForConfig();

		assertThat(cfgState)
			.as("Check configstate was persisted")
			.isNotNull();
	}

	private PersistStateResource waitForConfig() throws ParseException, ScimException, IOException {
		PersistStateResource cfgState = provider.getConfigState();
		int cnt = 0;
		while (cfgState==null && cnt < 5) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignored) {
			}
			cnt ++;
			cfgState = provider.getConfigState();
		}
		return cfgState;
	}

	/**
	 * Now that config state was persisted, check that SchemaManager and provider agree.
	 */
	@Test
	public void c_compareSchemaAndTypeCounts() throws ScimException, IOException, ParseException {
		logger.info("\t* Checking schema and type count matches");
		int cnt = 0;
		PersistStateResource cfgState = waitForConfig();

		Collection<Schema> mSchemas = provider.loadSchemas();
		Collection<ResourceType> mTypes = provider.loadResourceTypes();
		assertThat(mSchemas.size())
				.as("Schema count is the same")
				.isEqualTo(smgr.getSchemaCnt());

		assertThat(mTypes.size())
				.as("Check Resource Type count matches")
				.isEqualTo(smgr.getResourceTypeCnt());
		
	}


	@Test
	public void d_PersistedConfig() {
		
		logger.info("\t* Checking PersistStateResource");

		MemoryProvider mprovider = (MemoryProvider) provider;
		PersistStateResource cnfRes = mprovider.getConfigState();
		assertThat(cnfRes)
			.as("Check for persisted config resource not Null")
			.isNotNull();

		assertThat(cnfRes.getResTypeCnt())
				.as("Check type count matches")
				.isEqualTo(smgr.getResourceTypeCnt());
		assertThat(cnfRes.getSchemaCnt())
				.as("Check that schema count persisted matches")
				.isEqualTo(smgr.getSchemaCnt());

		assertThat(cnfRes.getLastSyncDate().toInstant())
				.as("Check date is before now")
				.isBefore(Instant.now());

		assertThat(cnfRes.getLastSyncDate().toInstant())
				.as("Check sync date is after start time of test.")
				.isAfter(startTime);

	}

	/**
	 * Check that a restart (withhout reset) properly loads the configs.
	 */
	@Test
	public void e_CheckRestart() throws ScimException, IOException, ParseException {
		logger.info("\t* Restart and re-load provider and SchemaManager");
		provider.shutdown();
		smgr.resetConfig();
		try {
			provider.init();
		} catch (BackendException e) {
			logger.error("Error while restarting provider",e);
			fail("Error while restarting provider",e);
		}

		try {
			// This time, the schema should be loaded from MemoryProvider
			smgr.init();
		} catch (ScimException | IOException | BackendException e) {
			logger.error("Error while restarting SchemaManager",e);
			fail("Error while restarting SchemaManager",e);
		}

		assertThat(smgr.isSchemaLoadedFromProvider())
				.as("Confirm schema was loaded from provider")
				.isTrue();
		//re-run the count test.
		c_compareSchemaAndTypeCounts();

	}
	

}
