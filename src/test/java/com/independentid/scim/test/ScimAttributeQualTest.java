package com.independentid.scim.test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.annotation.Resource;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit4.SpringRunner;

import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimBootApplication;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;


@ActiveProfiles("testing")
@RunWith(SpringRunner.class)
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = ScimBootApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ConfigMgr.class)
@TestMethodOrder(Alphanumeric.class)
@TestPropertySource(properties = {
		"scim.mongodb.test=true",
		"scim.mongodb.dbname=testCrudSCIM"
})
public class ScimAttributeQualTest {
	
	private static Logger logger = LoggerFactory.getLogger(ScimAttributeQualTest.class);
	
	//private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";
	
	@Autowired
	ScimBootApplication app;
	
	@Autowired
	private ResourceLoader resourceloader;

	@Resource(name="ConfigMgr")
	private ConfigMgr cmgr ;
	
	@Resource(name="MongoDao")
	public MongoProvider provider;
	
	@Value("${scim.mongodb.uri: mongodb://localhost:27017}")
	private String dbUrl;

	@Value("${scim.mongodb.dbname:SCIM}")
	private String scimDbName;
	
	private static MongoClient mclient = null;
	private static MongoDatabase scimDb = null;
	
	@LocalServerPort
	private int port;
	
	private static String user1url = "";
	
	private static String testUserFile1 = "classpath:/schema/TestUser-bjensen.json";
	
	private static boolean isInit = false;
	
	
	


	/**
	 * Do test setup by re-initializes the SCIM Mongo test database.
	 */
	@BeforeEach
	public synchronized void initializeMongo() {
		if (isInit)
			return;
		logger.info("========== Initializing Mongo For Test ==========");
		logger.info("\tA. Initializing test database: "+scimDbName);
		
		if (mclient == null)
			mclient = MongoClients.create(dbUrl);
		
	
		scimDb = mclient.getDatabase(scimDbName);
		
		scimDb.drop();
		
		try {
			provider.syncConfig(cmgr.getSchemas(), cmgr.getResourceTypes());
			loadTestUser();
		} catch (IOException e) {
			fail("Failed to initialize test Mongo DB: "+scimDbName);
		}
		
		Attribute mname = cmgr.findAttribute("User:name.middleName", null);
		if (mname != null) 
			logger.debug("User:name.middleName returnability is: "+mname.getReturned());
		
		isInit = true;
		
	}
	/**
	 * This test checks that a JSON user can be parsed into a SCIM Resource
	 */

	private void loadTestUser() {
		
		logger.info("\t * Add User BJensen...");
		CloseableHttpClient client = HttpClients.createDefault();

		try {
			InputStream userStream = resourceloader.getResource(testUserFile1).getInputStream();	
			
			String req = "http://localhost:"+port+"/Users";
			
			
			HttpPost post = new HttpPost(req);
				
			InputStreamEntity reqEntity = new InputStreamEntity(
	        userStream, -1, ContentType.create(ScimParams.SCIM_MIME_TYPE));
			reqEntity.setChunked(false);
			post.setEntity(reqEntity);
		
			CloseableHttpResponse resp = client.execute(post);
						
			assertThat(resp.getStatusLine().getStatusCode())
			.as("Create user response status of 201")
			.isEqualTo(ScimResponse.ST_CREATED);
			
			Header[] hloc = resp.getHeaders(HttpHeaders.LOCATION);
			
			user1url = hloc[0].getValue(); 
		} catch (IOException e) {
			Assertions.fail("Exception occured creating bjenson. "+e.getMessage(),e);
		} finally {
			try {
				client.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}				
	}

	/**
	 * This test attempts to retrieve the previously created user using the returned location.
	 */
	@Test
	public void a_ScimGetUserTest() {
		
		assertThat(isInit)
			.as("Check test databse initialized")
			.isTrue();
		
		String req = "http://localhost:"+port+user1url;
		
		logger.info("\tA. Retrieve user from backend using: "+req);
		
		CloseableHttpClient client = HttpClients.createDefault();
		
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check that it is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			
			// Check that the extension attributes were parsed and returned
			assertThat(body)
				.as("Contains the correct extension")
				.contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(body)
				.as("Contains an extension value Tour Operations")
				.contains("Tour Operations");

			// Now check for "middleName" which is not returned by default.
			assertThat(body)
				.as("name.middleName should not be present test")
				.doesNotContain("middleName");
			
			logger.debug("Entry returned with no middleName\n"+body);

			resp.close();
			
		} catch (IOException  e) {
			fail("Exception occured making GET request for bjensen",e);
		}
	}
	
	@Test
	public void b_ScimGetUserInclTest() {
		
		assertThat(isInit)
			.as("Check test databse initialized")
			.isTrue();
		
		String req = "http://localhost:"+port+user1url+"?attributes=userName,name.middleName";
		
		logger.info("\tB. Retrieve user from backend using: "+req);
		
		CloseableHttpClient client = HttpClients.createDefault();
		
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check that it is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			
			assertThat(body)
				.as("Contains name.middleName")
				.contains("\"middleName\"");
			
			assertThat(body)
				.as("Does not contain familyName")
				.doesNotContain("familyName");
			
			logger.debug("Entry returned with only userName and middleName\n"+body);
			// Check that the extension attributes were parsed and returned
		
			
			resp.close();
			
		} catch (IOException  e) {
			fail("Exception occured making GET request for bjensen",e);
		}
	}
	
	/**
	 * This test tries to search for the previously created user by searching on filter name
	 */
	@Test
	public void c_ScimSearchUserExcludeTest() {
		assertThat(isInit)
		.as("Check test databse initialized")
		.isTrue();
		
		logger.info("\tD. Searching user from backend with filter=UserName eq bjensen@example.com");
		CloseableHttpClient client = HttpClients.createDefault();
		
		String req = "http://localhost:"+port+"/Users?filter="+URLEncoder.encode("UserName eq bjensen@example.com",StandardCharsets.UTF_8)+"&excludedAttributes=meta,name";
		
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			
			assertThat(body)
				.as("Check query response is a ListResponse")
				.contains(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			logger.debug("Entry retrieved:\n"+body);
			
			// Check that the extension attributes were parsed and returned
			assertThat(body)
				.as("Contains the correct extension")
				.contains("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User");
			assertThat(body)
				.as("Contains an extension value Tour Operations")
				.contains("Tour Operations");
			
			assertThat(body)
				.as("Does not have a meta attribue")
				.doesNotContain("\"meta\"");
			assertThat(body)
			.as("Does not have a name attribue")
			.doesNotContain("\"name\"");
				
		    
			resp.close();
			
		} catch (IOException e) {
			fail("Exception occured making GET filter request for bjensen",e);
		}
	}
	
	@Test
	public void d_ScimGetUserInclExtTest() {
		
		assertThat(isInit)
			.as("Check test databse initialized")
			.isTrue();
		
		String req = "http://localhost:"+port+user1url+"?attributes=userName,name.middleName,organization,manager.displayName";
		
		logger.info("\tB. Retrieve user from backend using: "+req);
		
		CloseableHttpClient client = HttpClients.createDefault();
		
		HttpUriRequest request = new HttpGet(req);
		
		try {
			CloseableHttpResponse resp = client.execute(request);
			HttpEntity entity = resp.getEntity();
			
			assertThat(resp.getStatusLine().getStatusCode())
				.as("GET User - Check for status response 200 OK")
				.isEqualTo(ScimResponse.ST_OK);
			
			String body = EntityUtils.toString(entity);
			logger.debug("Entry returned with 4 attributes including extensions:\n"+body);
			
			assertThat(body)
				.as("Check that it is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);
			
			assertThat(body)
				.as("Is user bjensen")
				.contains("bjensen@example.com");
			
			assertThat(body)
				.as("Contains name.middleName")
				.contains("\"middleName\"");
			
			assertThat(body)
				.as("Does not contain familyName")
				.doesNotContain("familyName");
			
			assertThat(body)
				.as("Contains enterprise organization")
				.contains("\"organization\"");
			
			assertThat(body)
				.as("Contains manager.doisplayName")
				.contains("John Smith");
			
			assertThat(body)
				.as("Does not contain manager.$ref")
				.doesNotContain("/Users/26118915-6090-4610-87e4-49d8ca9f808d");
			
			assertThat(body)
				.as("Does not contain manager.$ref")
				.doesNotContain("\"$ref\"");
			
			resp.close();
			
		} catch (IOException  e) {
			fail("Exception occured making GET request for bjensen",e);
		}
	}


}
