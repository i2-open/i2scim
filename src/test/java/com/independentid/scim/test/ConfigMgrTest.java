package com.independentid.scim.test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Resource;

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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimBootApplication;
import com.independentid.scim.server.ScimException;


@ActiveProfiles("testing")
@RunWith(SpringRunner.class)
//@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ScimBootApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@TestMethodOrder(Alphanumeric.class)
@ContextConfiguration(classes = ConfigMgr.class)
public class ConfigMgrTest {
	
	private Logger logger = LoggerFactory.getLogger(ConfigMgrTest.class);
		
	@Autowired ApplicationContext ctx;
	
	@Resource(name="ConfigMgr")
	private ConfigMgr cmgr ;

	@Test
	public void a_beanTest() {
		logger.info("==========   List Beans  ==========");
		 
		//System.out.println("Let's inspect the beans provided by Spring Boot:");

		StringBuffer buf = new StringBuffer();
		buf.append("  Beans discovered:\n");
		String[] beanNames = ctx.getBeanDefinitionNames();
		
		assertThat(beanNames).isNotNull().isNotEmpty();

		Arrays.sort(beanNames);
		for (String beanName : beanNames) {
			buf.append("  * ").append(beanName).append('\n');
		}
		logger.info(buf.toString());
	}
	
	@Test
	public void b_ConfigTest() {
		
		
		logger.info("==========ConfigMgr Tests==========");
		logger.debug("  Checking ConfigMgr bean instantiated.");
		
		assertThat(cmgr).isNotNull();
		
		// These values should be returning default values and should not fail.
		assertThat(cmgr.getResourceTypePath()).isNotNull();
		logger.info("  Resource Type file path="+cmgr.getResourceTypePath());
		
		assertThat(cmgr.getSchemaPath()).isNotNull();
				
		logger.debug("  Getting Resource Types");
		
		Collection<ResourceType> rcol = cmgr.getResourceTypes();
		
		assertThat(rcol).isNotNull();
		assertThat(rcol).isNotEmpty();
		
		Iterator<ResourceType> iter = rcol.iterator();
		ResourceType uitem = null;
		int rCnt = 0;
		while (iter.hasNext()) {
			ResourceType item = iter.next();
			
			logger.info("      Type: "+item.getName()+", Endpoint: " + item.getTypePath());
			if (item.getTypePath().equalsIgnoreCase("users")) {
				uitem = item;
			}
			rCnt++;
		};
		
		assertThat(rCnt).isGreaterThan(0);
		logger.debug("  Counted "+rCnt+" resource types.");

		assertThat(rCnt).isEqualTo(5);
		
		logger.debug("  Checking presence of User endpoint definition...");
		assertThat(uitem).isNotNull();
		assertThat(uitem.getTypePath()).isEqualTo("Users");
		
		//logger.info("==========   Complete    ==========");
		
	}
	
	@Test
	public void c_findAttributeNoCtxTest() {
		
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
	
	@Test void d_findAttributesCtxTest() {
		RequestCtx ctx = null;
		try {
			ctx = new RequestCtx("Users",null,"userName eq dummy");
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
	

}
