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

import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.plugin.PluginHandler;
import com.independentid.scim.resource.ValueUtil;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AccessManager;
import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.Config;
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
import java.time.Instant;
import java.util.*;

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
@Startup
@ManagedBean
@Named("ConfigMgr")
public class ConfigMgr {
	
	public static final String SCIM_SERVER_PORT_DEF = "8080";

	//public static final String SCIM_SERVER_PORT = "scim.server.port";

	public static final String SCIM_SERVER_PATH_DEF = "/scim";

	public static final String SCIM_SERVER_HOST_DEF = "localhost";

	//public static final String SCIM_SERVER_PATH = "scim.server.path";

	//public static final String SCIM_SERVER_HOST = "scim.server.host";

	private final static Logger logger = LoggerFactory.getLogger(ConfigMgr.class);

	public static final String SCIM_PERSIST_SCHEMA = "scim.persist.schema";
	public static final String SCIM_JSON_PRETTY = "scim.json.pretty";
	public static final String SCIM_QUERY_MAX_RESULTSIZE = "scim.query.max.resultsize";
	public static final String SCIM_QUERY_MAX_RESULTS_DEFAULT = "1000";

	@ConfigProperty(name = "scim.json.pretty", defaultValue="false")
	boolean jsonPretty;
	
	@ConfigProperty(name = SCIM_PERSIST_SCHEMA, defaultValue="true")
	boolean persistSchema;

	//public final static String PARAM_MAX_RESULTS = "scim.query.max.resultsize";
	//public final static String DEFAULT_MAX_RESULTS = "1000";
	
	@ConfigProperty(name = "scim.query.max.resultsize", defaultValue= "1000")
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
	
	@ConfigProperty(name = "scim.server.port", defaultValue=SCIM_SERVER_PORT_DEF)
	int scimPort;
	
	@ConfigProperty(name = "scim.server.host", defaultValue=SCIM_SERVER_HOST_DEF)
	String scimHost;
	
	@ConfigProperty(name = "scim.server.path", defaultValue=SCIM_SERVER_PATH_DEF)
	String scimRoot;

	@ConfigProperty(name = "scim.test.configOnly",defaultValue = "false")
	boolean configOnly;

	@ConfigProperty(name= "scim.root.dir")
	String rootDir;

	@ConfigProperty(name= "quarkus.http.access-log.log-directory")
	String logDir;
	
	@Inject 
	@Resource(name="BackendHandler")
	BackendHandler backendHandler;

	@Inject
	@Resource(name="AccessMgr")
	AccessManager amgr;

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;

	@Inject
	@Resource(name="ScimPlugins")
	PluginHandler pluginHandler;

	@Inject
	ServletContext sctx;

	@Inject
	Config sysconf;
	
	/**
	 * @return the ctx
	 */
	public ServletContext getCtx()
	{
		return sctx;
	}

	public AccessManager getAccessManager() { return amgr; }

	public PluginHandler getPluginHandler() { return pluginHandler; }

	/**
	 * @param ctx the ctx to set
	 */
	public void setCtx(ServletContext ctx) {
		this.sctx = ctx;
	}

	private static ConfigMgr self=null;
	
	public ConfigMgr() {

			if (self == null)
				self = this;
	}
	
	@SuppressWarnings("BusyWait")
	public static ConfigMgr getConfig()  {
		if (self == null) {
			int i=0;
			while (self == null && i < 10) {
				try {
					System.err.println("Pausing configmgr startup until injection complete.");
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				i++;
			}
			if (self == null) {
				System.out.println("\nConfigMgr.getConfig() called before CDI??\n");
				return null;
			}
			//self = new ConfigMgr();
			try {
				self.init();
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
	public synchronized void init() throws ScimException,IOException {

		logger.info("======Initializing SCIM Config Mangaer=====");
		
		self = this;
		logger.info("Scim file system root: "+rootDir);
		File rootFile = new File(rootDir);
		if (!rootFile.exists())
			logger.error("Root directory for SCIM does not exist(scim.root.dir="+rootFile+").");

		if (isRootEnabled() && getRootPassword().equals("admin"))
			logger.warn("Server is configured with root access and default password!");

		ValueUtil.initialize(this);

		if (logger.isDebugEnabled()) {
			// Dump diagnostic information to the logs...
			Properties tprop = new Properties();
			logger.debug("SCIM Environment Run Properties");
			logger.debug(String.format("%-50s %s\n","Property","Value"));
			List<String> pvals = new ArrayList<>();
			for(String prop : sysconf.getPropertyNames())
				pvals.add(prop);
			Collections.sort(pvals);
			for(String prop : pvals) {
				try {
					Optional<String> val = sysconf.getOptionalValue(prop,String.class);
					String pval = "<optional>";
					if (val.isPresent()) {
						pval = val.get();
						tprop.put(prop,pval);
					}
					int len = prop.length();
					if (len >= 50) {
						int trim = len-50;

						prop = "< " + prop.substring(trim+2); // trim the left side
					}
					String mask = "********************************";
					if(prop.startsWith("scim.security.root") && !prop.equals("scim.security.root.enable") ||
							prop.contains("password"))
						pval = mask.substring(0,pval.length()-1);
					logger.debug(String.format("%-50s %s\n",prop,pval));
					///System.out.printf("%-50s %s\n",prop,pval);

				} catch (Exception ignore) {

				}
			}
			File cfile = new File(rootFile,"test.prop");
			Date now = Date.from(Instant.now());
			FileOutputStream writer = new FileOutputStream(cfile);
			tprop.store(writer,
					"# Properties captured "+now);
			writer.close();
		}

		File dir = new File(logDir);
		if (!dir.exists()) {
			logger.info(" Creating log directory: "+logDir);
			dir.mkdir();
		}
	}
	
	public int getPort() {
		return this.scimPort;
	}
	
	public String getHost() {
		return this.scimHost;
	}
	
	public String getServerRootPath() {
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

	/**
	 * Returns a File for a resource packaged with the SCIM server.
	 * @param file A <String> path containing the resourc to be located
	 * @return InputStream containing the located file.
	 * @throws IOException if not mappable or does not exist.
	 */
	public static InputStream findClassLoaderResource(final String file) throws IOException {
		// In Quarkus, the classloader doesn't seem to want "classpath:" prefix. Springboot does.

		String mapFile = file.strip();
		if (mapFile.startsWith("classpath:"))
			mapFile = mapFile.substring(10);
		//System.out.println("File requested:\t"+mapFile);

		logger.debug("Attempting to read: "+mapFile);
		InputStream input = ConfigMgr.class.getResourceAsStream(mapFile);
		if (input == null) {
			try {
				input = new FileInputStream(mapFile);
			} catch (FileNotFoundException e) {
				System.err.println("\tERROR: Unable to open file.");
				logger.error("Unable to open: "+file);
				return null;
			}
		}

		return input;
	}

	
	public boolean isPrettyJsonMode() {
		return this.jsonPretty;
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
