package com.independentid.scim.test;


import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;

import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimBootApplication;

@ActiveProfiles("testing")
@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ScimBootApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ConfigMgr.class)
@TestMethodOrder(Alphanumeric.class)
public class MongoProviderTest {
	
	private Logger logger = LoggerFactory.getLogger(ConfigMgrTest.class);
	
	//@Autowired 
	//private ScimBootApplication scimApp;
	
	//@Autowired
	//private ScimV2Controller scimController;
	
	@Autowired ApplicationContext ctx;
	
	@Resource(name="ConfigMgr")
	private ConfigMgr cmgr ;
	
	@Value("${scim.mongodb.uri: mongodb://localhost:27017}")
	private String dbUrl;

	@Value("${scim.mongodb.dbname: SCIM}")
	private String scimDbName;
	
	@Test()
	public void mongoDaoTest() {
		
		logger.info("========== MongoDAO Test ==========");

		//ConfigMgr mgr = (ConfigMgr) ctx.getBean("Configmgr");
		
		Object bean = (IScimProvider) ctx.getBean("MongoDao");
		assertThat(bean).isInstanceOf(MongoProvider.class);
		
		MongoProvider mp = (MongoProvider) bean;

		assertThat(mp.ready()).isTrue();
		assertThat(mp.getMongoDbName()).isEqualTo(scimDbName);
		
		logger.debug("Current collections: \n"+mp.getDbConnection().listCollections());
		
		logger.info("==========   Complete    ==========");
		
	}
	

}
