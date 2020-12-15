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
package com.independentid.scim.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AccessManager;
import com.independentid.scim.serializer.JsonUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.ManagedBean;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * @author pjdhunt
 * ConfigMgr is a centralized SCIM server configuration service. It loads the schema from
 * JSON files or pulls them from a configured persistence provider. ConfigMgr can be
 * injected as a bean (name="ConfigMgr") or using the {@link #getConfig()} method.
 *
 */

//@Component("ConfigMgr")
//@ComponentScan(basePackages= {"com.independentid.scim.server","com.independentid.scim.backend"})

//@ApplicationScoped
@Singleton
@ManagedBean
@Named("ConfigMgr")
public class ConfigMgr {
	
	public static final String SCIM_SERVER_PORT_DEF = "8080";

	public static final String SCIM_SERVER_PORT = "scim.server.port";

	public static final String SCIM_SERVER_PATH_DEF = "/scim";

	public static final String SCIM_SERVER_HOST_DEF = "localhost";

	public static final String SCIM_SERVER_PATH = "scim.server.path";

	public static final String SCIM_SERVER_HOST = "scim.server.host";

	public final static List<String> SCIM_CORE_ATTRS = Arrays.asList(
			"id","externalid","schemas","meta");

	private final static Logger logger = LoggerFactory.getLogger(ConfigMgr.class);

	@ConfigProperty(name = "scim.json.pretty", defaultValue="false")
	boolean jsonPretty;
	
	@ConfigProperty(name = "scim.persist.schema", defaultValue="true")
	boolean persistSchema;

	//public final static String PARAM_PROVIDER_CLASS = "scim.provider.class";
	//public final static String DEFAULT_PROVIDER = MongoProvider.class.getName();

	//public final static String PARAM_MAX_RESULTS = "scim.query.max.resultsize";
	//public final static String DEFAULT_MAX_RESULTS = "1000";
	
	@ConfigProperty(name = "scim.query.max.resultsize", defaultValue="1000")
	int maxResults; 
	
	//public final static String PARAM_BULK_MAX_OPS = "scim.bulk.max.ops";
	//public final static String DEFAULT_BULK_MAX_OPS = "1000";
	
	@ConfigProperty(name = "scim.bulk.max.ops", defaultValue="1000")
	int bulkMaxOps;
	
	//public final static String PARAM_BULK_MAX_ERRS = "scim.bulk.max.errors";
	//public final static String DEFAULT_BULK_MAX_ERRS = "50";

	@ConfigProperty(name = "scim.bulk.max.errors", defaultValue="5")
	int bulkMaxErrors;
	
	@ConfigProperty(name = "scim.thread.count", defaultValue="5")
	int threadCount;
	
	@ConfigProperty(name = "scim.security.enable", defaultValue="true")
	boolean isSecurityEnabled;

	@ConfigProperty(name = "scim.security.authen.jwt", defaultValue="true")
	boolean authJwt;

	@ConfigProperty(name = "scim.security.authen.jwt.claim.scope", defaultValue = "scope")
	String jwtScopeClaim;
	
	@ConfigProperty(name = "scim.security.authen.opaque", defaultValue="false")
	boolean authOpaque;
	
	@ConfigProperty(name = "scim.security.authen.basic", defaultValue="false")
	boolean authBasic;
	
	@ConfigProperty(name = "scim.security.root.enable", defaultValue="false")
	boolean rootEnable;
	
	@ConfigProperty(name = "scim.security.root.username", defaultValue="admin")
	String rootUser;
	
	@ConfigProperty(name = "scim.security.root.password", defaultValue="admin")
	String rootPassword;
	
	@ConfigProperty(name = SCIM_SERVER_PORT, defaultValue=SCIM_SERVER_PORT_DEF)
	int scimPort;
	
	@ConfigProperty(name = SCIM_SERVER_HOST, defaultValue=SCIM_SERVER_HOST_DEF)
	String scimHost;
	
	@ConfigProperty(name = SCIM_SERVER_PATH, defaultValue=SCIM_SERVER_PATH_DEF)
	String scimRoot;

	@ConfigProperty(name = "scim.test.configOnly",defaultValue = "false")
	boolean configOnly;
	
	@Inject 
	@Resource(name="BackendHandler")
	BackendHandler backendHandler;

	@Inject
	@Resource(name="AccessMgr")
	AccessManager amgr;

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	private static boolean initialized = false;

	@Inject
	ServletContext sctx;
	
	/**
	 * @return the ctx
	 */
	public ServletContext getCtx()
	{
		return sctx;
	}

	public AccessManager getAccessManager() { return amgr; }

	/**
	 * @param ctx the ctx to set
	 */
	public void setCtx(ServletContext ctx) {
		this.sctx = ctx;
	}

	private static ConfigMgr self=null;
	
	public ConfigMgr() {
			System.out.println("Config Mgr created");
			if (self != null)
				System.err.println("...multiple instances of ConfigMgr detected!");
			else
				self = this;
	}
	
	public static ConfigMgr getConfig()  {
		if (self == null) {
			System.out.println("\nConfigMgr.getConfig() called before CDI??\n");
			self = new ConfigMgr();
			try {
				self.initializeConfiguration();
			} catch (ScimException | IOException e) {
				e.printStackTrace();
			}
		}
		return self;
	}

	public BackendHandler getBackendHandler() {
		return this.backendHandler;
	}
	
	@PostConstruct
	public synchronized void initializeConfiguration() throws ScimException,IOException {
		if (initialized) {
			logger.debug("ERROR: Multiple initializations detected");
			return;
		}

		logger.info("======Initializing SCIM Config Mangaer=====");
		
		self = this;

		if (!configOnly && !backendHandler.isReady())
			try {
				backendHandler.init(this);
			} catch (ClassNotFoundException | InstantiationException | BackendException e) {
				throw new ScimException("Fatal error starting backend handler: "+e.getLocalizedMessage(),e);
			}
		
		//If the server will not persist its schema in the database, then load directly into config.

		initialized = true;
		
		if (isRootEnabled() && getRootPassword().equals("admin"))
			logger.warn("Server is configured with root access and default password!");
	}
	
	public int getPort() {
		return this.scimPort;
	}
	
	public String getHost() {
		return this.scimHost;
	}
	
	public String getRoot() {
		return this.scimRoot;
	}


	/**
	 * @return the isSecure
	 */
	public boolean isSecurityEnabled() {
		return isSecurityEnabled;
	}

	/**
	 * @return the authJwt
	 */
	public boolean isAuthJwt() {
		return authJwt;
	}

	public String getJwtScopeClaim() { return jwtScopeClaim; }

	/**
	 * @return the authOpaque
	 */
	public boolean isAuthOpaque() {
		return authOpaque;
	}

	/**
	 * @return the authBasic
	 */
	public boolean isAuthBasic() {
		return authBasic;
	}
	
	public boolean isRootEnabled() {
		return rootEnable;
	}
	
	public String getRootUser() {
		return rootUser;
	}
	
	public String getRootPassword() {
		return rootPassword;
	}
	


	public static FileInputStream getClassLoaderFile(final String file) throws IOException {
		File inFile = findClassLoaderResource(file);

		return inFile==null? null : new FileInputStream(inFile);
	}

	/**
	 * Returns a File for a resource packaged with the SCIM server.
	 * @param file A <String> path containing the resourc to be located
	 * @return File containing the located file.
	 * @throws IOException if not mappable or does not exist.
	 */
	public static File findClassLoaderResource(final String file) throws IOException {
		// In Quarkus, the classloader doesn't seem to want "classpath:" prefix. Springboot does.
		String mapFile;
		if (file.startsWith("classpath:"))
			mapFile = file.substring(10);
		else
			mapFile = file;

		URL fUrl = ConfigMgr.class.getClassLoader().getResource(mapFile);
		if (fUrl != null)
			try {
				return new File(fUrl.toURI());
			} catch (URISyntaxException e) {
				// SHOULD NOT HAPPEN as this is from classloader
				throw new IOException("Unable to map URI returned from classloader.");
			}
		return  null;
	}

	public void serializeServiceProviderConfig(RequestCtx ctx, JsonGenerator gen) throws IOException {
		
        gen.writeStartObject();
        
        gen.writeArrayFieldStart("schemas");
        gen.writeString(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);
        gen.writeEndArray();
        
        // Identify the server
        gen.writeStringField("ProductName", "IndependentId SCIM Test Directory");
        //gen.writeStringField("ProductId", "BigDirectory");
        gen.writeStringField("ProductVersion", "V1.0");
        
        /* Not defined in standard schema.
        gen.writeArrayFieldStart("ScimVersionSupport");
        gen.writeString("2.0");
        gen.writeEndArray();
        */
        
        // Documentation
        // TODO set up web documentation URL
        
        gen.writeStringField("documentationUri", "https://independentid.com/scim");
        
        
        // Indicate Patch supported
        gen.writeFieldName("patch");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeEndObject();
        
        // Indicate Bulk support
        gen.writeFieldName("bulk");
        gen.writeStartObject();
        gen.writeBooleanField("supported", false);
        gen.writeNumberField("maxOperations", 0);
        gen.writeNumberField("maxPayloadSize", 0);
        gen.writeEndObject();
        
        // Indicate Filter support
        gen.writeFieldName("filter");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeNumberField("maxResults", 0);
        gen.writeEndObject();
        
        // Change Password support
        gen.writeFieldName("changePassword");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeEndObject();

        // Sorting
        gen.writeFieldName("sort");
        gen.writeStartObject();
        gen.writeBooleanField("supported", false);
        gen.writeEndObject();


        // ETag
        gen.writeFieldName("etag");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeEndObject();
        
        // Authentication Schemes
        gen.writeArrayFieldStart("authenticationSchemes");
        gen.writeStartObject();
        gen.writeStringField("name", "httpbasic");
        gen.writeStringField("description", "HTTP Basic Authentication");
        gen.writeStringField("specUri", "https://www.ietf.org/rfc/rfc2617.txt");
        gen.writeEndObject();
        gen.writeEndArray();

	}

	public String serializeServiceProviderConfig(RequestCtx ctx, Writer writer) throws IOException {
		
		//TODO: Check for filter and apply to response
		
		Writer swriter;
		if (writer == null)
			swriter = new StringWriter();
		else
			swriter = writer;
		
        JsonGenerator gen = JsonUtil.getGenerator(swriter, false);
		
        serializeServiceProviderConfig(ctx,gen);
        
        gen.close();
        
        return swriter.toString();

	}
	
	public boolean isPrettyJsonMode() {
		return this.jsonPretty;
	}
	
	/**
	 * @return Boolean contains true if ConfigMgr is fully initialized.
	 */
	public boolean isReady() {
		return initialized;
	}

	
	public void setMaxResults(int max) {
		this.maxResults = max;
	}
	
	public void setBulkMaxOps(int max) {
		this.bulkMaxOps = max;
	}
	
	public void setBulkMaxErrors(int max) {
		this.bulkMaxErrors = max;
	}
	
	public int getMaxResults() {
		return this.maxResults;
	}
	
	public boolean persistSchemaMode() {
		return this.persistSchema;
	}
	
	public int getBulkMaxOps() {
		return this.bulkMaxOps; 
	}
	
	public int getBulkMaxErrors() {
		return this.bulkMaxErrors; 
	}
	
	public int getPoolThreadCount() {
		return this.threadCount;
	}

	public SchemaManager getSchemaManager() { return this.smgr; }
	
}
