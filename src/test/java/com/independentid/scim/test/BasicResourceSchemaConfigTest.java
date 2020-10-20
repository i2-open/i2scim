package com.independentid.scim.test;


import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimBootApplication;


@ActiveProfiles("testing")
@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ScimBootApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ConfigMgr.class)
public class BasicResourceSchemaConfigTest {
	
	private Logger logger = LoggerFactory.getLogger(BasicResourceSchemaConfigTest.class);
	
	private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";
	

	@Resource(name="ConfigMgr")
	private ConfigMgr cmgr ;
	
	
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
