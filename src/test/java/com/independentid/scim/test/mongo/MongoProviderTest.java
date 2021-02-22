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

package com.independentid.scim.test.mongo;


import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.PersistStateResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MongoProviderTest {
	
	private static final Logger logger = LoggerFactory.getLogger(MongoProviderTest.class);
	
	@Inject
	@Resource(name="ConfigMgr")
	ConfigMgr cmgr;

	@Inject
	BackendHandler handler;

	@ConfigProperty(name="scim.mongodb.dbname",defaultValue = "testSCIM")
	String scimDbName;

	static MongoProvider mp = null;

	@Test
	public void a_mongoProviderTest() {

		logger.info("========== MongoProvider Basic Test ==========");

		mp = (MongoProvider) handler.getProvider();
		//ConfigMgr mgr = (ConfigMgr) ctx.getBean("Configmgr");
		//MongoProvider mp = (MongoProvider) MongoProvider.getProvider();
		assertThat(mp).isNotNull();

		assertThat(mp.ready()).isTrue();
		assertThat(mp.getMongoDbName()).isEqualTo(scimDbName);
		
		logger.debug("Current collections: \n"+mp.getDbConnection().listCollections());
		
		logger.info("==========   Complete    ==========");
		
	}

	@Test
	public void b_configTest() {
		assert mp != null;
		assertThat(mp.ready()).as("Check provider ready").isTrue();
		try {
			PersistStateResource state = mp.getConfigState();
			assertThat(state.getSchemaCnt())
					.as("More than 2 schema")
					.isGreaterThan(2);
			assertThat(state.getResTypeCnt())
					.as("More than 1 Resource type")
					.isGreaterThan(1);

			Date syncDate = state.getLastSyncDate();
			assertThat(syncDate)
					.as("Has a sync date")
					.isNotNull();
		} catch (ScimException | IOException | ParseException e) {
			e.printStackTrace();
		}
	}
	

}
