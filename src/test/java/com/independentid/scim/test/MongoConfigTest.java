package com.independentid.scim.test;


import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;

import javax.annotation.Resource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimBootApplication;
import com.independentid.scim.server.ScimException;


@ActiveProfiles("testing")
@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ScimBootApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ConfigMgr.class)
@TestMethodOrder(Alphanumeric.class)
@TestPropertySource(properties = {
		"scim.mongodb.test=true",
		"scim.mongodb.dbname=testSCIM"
})
public class MongoConfigTest {
	
	private Logger logger = LoggerFactory.getLogger(MongoConfigTest.class);
		
	@Autowired 
	public ApplicationContext ctx;
	
	@Resource(name="MongoDao")
	public MongoProvider provider;
	
	@Resource(name="ConfigMgr")
	private ConfigMgr cmgr ;

	@Test
	public void a_beanCheckTest() {
		logger.info("==========   MongoConfig Tests ==========");
		 
		logger.info("* Running initial persistance provider checks");
		assertThat(provider).as("MongoProvider is defined.").isNotNull();
		
		assertThat(provider.ready()).as("MongoProvider is ready").isTrue();

		
	}
	
	@Test
	public void b_SchemaTest() {
		Schema userById = cmgr.getSchemaById("urn:ietf:params:scim:schemas:core:2.0:User");
		Schema userByName = cmgr.getSchemaByName("User");
		
		assertThat(userById == userByName).as("Is the same Schema config instance").isTrue();
		
		assertThat(userById).as("Schema content equality is true").isEqualTo(userByName);
		
	}

	@Test 
	public void c_ResTypeTest() {
		ResourceType resByEp = cmgr.getResourceTypeByPath("Users");
		ResourceType resByName = cmgr.getResourceType("User");
		
		assertThat(resByEp == resByName).as("Is the same ResourceType config instance").isTrue();
		
		assertThat(resByEp).as("ResourceType content equality is true").isEqualTo(resByName);
		
	}

	@Test
	public void d_PersistedConfig() {
		
		logger.info("* Checking schema");

		Collection<Schema> schCol = cmgr.getSchemas();
		Collection<ResourceType> resTypeCol = cmgr.getResourceTypes();
		
		int confSchCnt = schCol.size();
		logger.debug("  ConfigMgr loaded Schema count: "+confSchCnt);
		
		Collection<Schema> scol = null;
		try {
			scol = provider.loadSchemas();
			assertThat(scol.size())
				.as("Persisted schema count matches ConfigMgr count: "+scol.size())
				.isEqualTo(confSchCnt);
		} catch (ScimException e) {
			logger.error("Error while loading schema from Mongo: "+e.getMessage(),e);
			Assertions.fail("ScimException while loading schema from Mongo: "+e.getMessage(),e);
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
			Assertions.fail("Error while loading resource types: "+e.getMessage(),e);
		}
		
		try {
			PersistStateResource cnfRes = provider.getConfig();
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
			
		} catch (ScimException | IOException | SchemaException | ParseException e) {
			logger.error("Error while loading persistant state config resource",e);
			Assertions.fail("Error while loading persistant state config resource",e);
		}
	}
	

}
