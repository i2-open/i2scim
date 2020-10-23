package com.independentid.scim.test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.annotation.Resource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.PoolManager;
import com.independentid.scim.server.ScimBootApplication;
import com.independentid.scim.server.ScimV2Servlet;


@ActiveProfiles("testing")
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ScimBootApplication.class,ScimV2Servlet.class,ConfigMgr.class,BackendHandler.class,PoolManager.class}, webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = ConfigMgr.class)
@TestPropertySource(properties = {
		"scim.mongodb.test=true",
		"scim.mongodb.dbname=testSCIM",
		"scim.security.enable=false"
})
@TestMethodOrder(Alphanumeric.class)
public class ScimConfigEndpointTest {
	
	private Logger logger = LoggerFactory.getLogger(ScimConfigEndpointTest.class);
	
	@Autowired
	ScimBootApplication app;
	
	@Autowired
	private ScimV2Servlet servlet;
	
	@Resource(name="ConfigMgr")
	private ConfigMgr cfgMgr;
	
	@LocalServerPort
	private int port;
	
	@Autowired
	private TestRestTemplate restTemplate;	

	private HttpResponse executeGet(String req) {
		try {
			HttpUriRequest request = new HttpGet( req );
			return HttpClientBuilder.create().build().execute(request);
		} catch (IOException e) {
			fail("Failed request: "+req+"\n"+e.getLocalizedMessage(),e);
		}
		return null;
	}
		
	/**
	 * This test just checks that spring boot injection environment worked and the basic test
	 * endpoint test works.
	 */
	@Test
	public void a_basic_webTest() {
		logger.info("========= SCIM Servlet Basic Test          =========");

		logger.debug("  Starting Web Test");
		
		assertThat(servlet).isNotNull();
		
		assertThat(port).isNotEqualTo(0);
		
		//String name = servlet.getServletName();
				//logger.debug("Servlet name:\t"+servlet.getServletName());
		//ServletContext ctx = servlet.getServletContext();
		//logger.debug("Servlet Context Path:\t"+ctx.getContextPath());
		
		
		//We need a rest template to run
		assertThat(restTemplate).isNotNull();
		String res = this.restTemplate.getForObject("http://localhost:"+port+"/test",String.class);
		assertThat(res)
			.as("Default test endpoint works").isEqualTo("Hello Tester!");
		
		res = this.restTemplate.getForObject("http://localhost:"+port+"/v2/test",String.class);
		assertThat(res)
			.as("V2 endpoint works").isEqualTo("Hello Tester!");
	}
	
	@Test
	public void b_SchemasEndpoint() {
		logger.info("=========      Schemas Endpoint Test       =========");
		String res = this.restTemplate.getForObject("http://localhost:"+port+"/Schemas", String.class);
		
		assertThat(res).isNotNull();
		
		assertThat(res)
			.as("Contains correct items per page")
			.contains("\"itemsPerPage\" : 6,");
		assertThat(res)
		.as("Contains correct items per page")
		.contains("\"totalResults\" : 6,");
		assertThat(res)
			.as("Confirtm List Response Type")
			.contains(ListResponse.SCHEMA_LISTRESP);
		
		String req = "http://localhost:"+port+"/Schemas?filter="+URLEncoder.encode("id eq \"Service\"",StandardCharsets.UTF_8);
		
		HttpResponse httpResponse = executeGet(req);
		assertThat(httpResponse.getStatusLine().getStatusCode())
			.as("Check filter on schema is forbidden per RFC7644 Sec 4, pg 73.")
			.isEqualTo(ScimResponse.ST_FORBIDDEN);
		
		req = "http://localhost:"+port+"/Schemas/"+ScimParams.SCHEMA_SCHEMA_User;
		httpResponse = executeGet(req);
		HttpEntity entity = httpResponse.getEntity();
		try {
			res = EntityUtils.toString(entity, StandardCharsets.UTF_8);
		} catch (ParseException | IOException e) {
			fail("Failed to parse response body for schemas endpoint"+e.getLocalizedMessage(),e);
		}
		assertThat(httpResponse.getStatusLine().getStatusCode())
		.as("Check for status response 200 OK")
		.isEqualTo(ScimResponse.ST_OK);
		
		
		assertThat(res)
			.as("Check that it is not a ListResponse")
			.doesNotContain(ScimParams.SCHEMA_API_ListResponse);
		assertThat(res)
			.as("Check the requested object returned")
			.contains(ScimParams.SCHEMA_SCHEMA_User);
	}
	
	@Test
	public void c_ServiceProviderConfigTest() {
		logger.info("=========      ServiceProviderConfig Test  =========");
		String res = this.restTemplate.getForObject("http://localhost:"+port+"/ServiceProviderConfig", String.class);
		logger.debug("ServiceProviderConfig res:\n"+res);
		assertThat(res)
		  .as("ServiceProviderConfig does not contain ListResponse")
		  .doesNotContain(ScimParams.SCHEMA_API_ListResponse);
		
		assertThat(res)
			.as("Has schemas ServiceProviderConfig")
			.contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);
		
		String req = null;
		
		try {
			req = "http://localhost:"+port+"/ServiceProviderConfig/test";
			HttpResponse httpResponse = executeGet(req);
			
			assertThat(httpResponse.getStatusLine().getStatusCode())
				.as("Check for not found response to /ServiceProviderConfigs/test")
				.isEqualTo(ScimResponse.ST_NOTFOUND);
			
			// This tests an incorrect request to ServiceProviderConfigs rather than ServiceProviderrConfig
			req = "http://localhost:"+port+"/ServiceProviderConfigs?filter="+URLEncoder.encode("patch.supported eq true",StandardCharsets.UTF_8);
			httpResponse = executeGet(req);
			
			assertThat(httpResponse.getStatusLine().getStatusCode())
				.as("Check incorrect endpoint returns NOT FOUND")
				.isEqualTo(ScimResponse.ST_NOTFOUND);
			
			// This tests that a filter can be executed against ServiceProviderConfig
			req = "http://localhost:"+port+"/ServiceProviderConfig?filter="+URLEncoder.encode("patch.supported eq true",StandardCharsets.UTF_8);
			httpResponse = executeGet(req);

			HttpEntity entity = httpResponse.getEntity();
			assertThat(httpResponse.getStatusLine().getStatusCode())
				.as("Check if a filter was accepted (should not be error 400")
				.isNotEqualTo(ScimResponse.ST_BAD_REQUEST);
			assertThat(httpResponse.getStatusLine().getStatusCode())
				.as("Check for normal 200 response to filtered SPC request")
				.isEqualByComparingTo(ScimResponse.ST_OK);
			
			res = EntityUtils.toString(entity, StandardCharsets.UTF_8);
			
			logger.debug("Filter reject result:\n"+res);
			assertThat(httpResponse.getStatusLine().getStatusCode())
			.as("Check for normal 200 response to filtered SPC request")
			.isEqualByComparingTo(ScimResponse.ST_OK);
			assertThat(res)
				.as("Check ServiceProviderConfig actually returned")
				.contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);
			assertThat(res)
			.as("Confirm patch supported response")
			.contains("patch");
			assertThat(res)
				.as("Check that SPC response is not a ListResponse")
				.contains(ScimResponse.SCHEMA_LISTRESP);
			
			//This should return an empty ListResponse (no match)
			req = "http://localhost:"+port+"/ServiceProviderConfig?filter="+URLEncoder.encode("patch.supported eq false",StandardCharsets.UTF_8);
			httpResponse = executeGet(req);

			entity = httpResponse.getEntity();
			assertThat(httpResponse.getStatusLine().getStatusCode())
				.as("Check for normal 200 response to filtered SPC request")
				.isEqualByComparingTo(ScimResponse.ST_OK);		
			
			res = EntityUtils.toString(entity, StandardCharsets.UTF_8);
			assertThat(res)
				.as("Check ServiceProviderConfig not returned")
				.doesNotContain(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);
			assertThat(res)
				.as("Check that SPC response is an empty ListResponse")
				.contains(ScimResponse.SCHEMA_LISTRESP);
			assertThat(res)
				.as("Contains correct items per page")
				.contains("\"totalResults\" : 0,");
			
		} catch (IOException e) {
			fail("Failed to call serviceproviderconfig endpoint"+e.getLocalizedMessage(),e);
		}
		  
		
		
	}
	
	@Test
	public void d_ResourceTypesTest() {
		logger.info("=========      ResourceTypes Endpoint Test =========");
		
		try {
			// Return all Resource Types
			String req = "http://localhost:"+port+"/ResourceTypes";
			HttpResponse httpResponse = executeGet(req);
			
			assertThat(httpResponse.getStatusLine().getStatusCode())
				.as("Check for OK response to ResourceTypes endpoint")
				.isEqualTo(ScimResponse.ST_OK);
			
			String res = EntityUtils.toString(httpResponse.getEntity());
			
			logger.debug("ResourceTypes test:\n"+res);
					
			assertThat(res).isNotNull();
			
			assertThat(res)
				.as("Contains correct items per page")
				.contains("\"itemsPerPage\" : 5,");
			assertThat(res)
			.as("Contains correct items per page")
			.contains("\"totalResults\" : 5,");
			assertThat(res)
				.as("Confirtm List Response Type")
				.contains(ListResponse.SCHEMA_LISTRESP);
			
			// Perform a filtered search that should be forbidden
			req = "http://localhost:"+port+"/"+
					ScimParams.PATH_TYPE_RESOURCETYPE + "?filter="+
					URLEncoder.encode("name eq \"User\"",StandardCharsets.UTF_8);
			
			httpResponse = executeGet(req);
			assertThat(httpResponse.getStatusLine().getStatusCode())
				.as("Check filter on ResourceTypes is forbidden per RFC7644 Sec 4, pg 73.")
				.isEqualTo(ScimResponse.ST_FORBIDDEN);
			
			// Return a specific ResourceType
			req = "http://localhost:"+port+"/ResourceTypes/User";
			httpResponse = executeGet(req);
			assertThat(httpResponse.getStatusLine().getStatusCode())
			.as("Check for status response 200 OK")
			.isEqualTo(ScimResponse.ST_OK);
			
			HttpEntity entity = httpResponse.getEntity();		
			res = EntityUtils.toString(entity, StandardCharsets.UTF_8);
			assertThat(res)
				.as("Check that returned ResourceType is not a ListResponse")
				.doesNotContain(ScimParams.SCHEMA_API_ListResponse);
			assertThat(res)
				.as("Check the requested User resource type returned")
				.contains("\"/Users\"");
		} catch (ParseException | IOException e) {
			fail("Failed retrieving resource type information:"+e.getLocalizedMessage(),e);
		}
		
	}
}
