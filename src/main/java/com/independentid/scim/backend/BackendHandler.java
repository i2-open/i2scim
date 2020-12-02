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

package com.independentid.scim.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

//@ApplicationScoped
@Singleton
@Startup
@Named("BackendHandler")
public class BackendHandler {

	private final static Logger logger = LoggerFactory.getLogger(BackendHandler.class);
		
	//private final static HashMap<ServletContext,BackendHandler> handlers = new HashMap<ServletContext,BackendHandler>();
	
	static IScimProvider provider = null;

	@ConfigProperty(name = "scim.provider.bean", defaultValue="com.independentid.scim.backend.mongo.MongoProvider")
	String providerName;
	
	public BackendHandler() {
		// Can't do anything here that involves injected properties etc!
	}

	/**
	 * Causes the backend system to initialize by dynamically creating the configured provider as
	 * specified in the property scim.provider.bean. Once created, the init(config) method of the
	 * provider is called to initialize the provider.
	 * @param config Provides the schema and resource definitions that a provider may need to start up.
	 * @return true if provider is ready.
	 * @throws ClassNotFoundException Thrown when the provider class (specified in scim.provider.bean property)
	 * could not be loaded.
	 * @throws InstantiationException Thrown when the provider specified failed to be created using newInstance() method.
	 * @throws BackendException An error thrown by the provider related to SCIM (e.g. parsing schema).
	 */
	@PostConstruct
	public synchronized boolean init(ConfigMgr config)
			throws ClassNotFoundException, InstantiationException, BackendException {
		if (provider == null) {
			logger.info("=====BackendHandler initialization=====");
			logger.info("Starting IScimProvider Provider: " + providerName);

			getProvider();
		}

		if (!provider.ready())
			provider.init(config);

		return provider.ready();

	}
	
	
	@PreDestroy
	public void shutdown() {
		if (provider != null)
			provider.shutdown();
	}
	
	/*
	@Produces
	public static BackendHandler getInstance() {
		if (self == null) {
			self = new BackendHandler();
			try {
				self.init();
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | BackendException e) {
				logger.error("Error starting backend handler: "+e.getLocalizedMessage(),e);
				return null;
			}
		}
		return self;
	}*/

	public synchronized IScimProvider getProvider() throws InstantiationException, ClassNotFoundException {
		if (provider != null)
			return provider;
		
		if (providerName == null)
			throw new InstantiationException("Unable to instantiate, IScimProvider bean class property (scim.provider.bean) not defined.");

		if (providerName.startsWith("class"))
			providerName = providerName.substring(6);

		Class<?> provClass = Class.forName(providerName);
		try {
			provider = (IScimProvider) provClass.getDeclaredConstructor().newInstance();
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			throw new InstantiationException("Unable to instantiate, IScimProvider bean class "+providerName+ ": "+e.getLocalizedMessage());
		}

		return provider;
	}
	
	public boolean isReady() {
		if (provider == null) return false;
		return provider.ready();
	}

	
	public ScimResponse create(RequestCtx ctx,final ScimResource res)
			throws ScimException, BackendException {
		return provider.create(ctx, res);
	}

	public ScimResponse get(RequestCtx ctx) throws ScimException, BackendException {
		return provider.get(ctx);
	}

	public ScimResponse replace(RequestCtx ctx, final ScimResource res)
			throws ScimException, BackendException {
		return provider.replace(ctx, res);
	}

	public ScimResponse patch(RequestCtx ctx, JsonPatchRequest	req) throws ScimException, BackendException {
		return provider.patch(ctx, req);
	}

	public ScimResponse bulkRequest(RequestCtx ctx, JsonNode node)
			throws ScimException, BackendException {
		return provider.bulkRequest(ctx, node);
	}

	public ScimResponse delete(RequestCtx ctx) throws ScimException, BackendException {
		return provider.delete(ctx);
	}

	public void syncConfig(ConfigMgr cfgmgr) throws IOException {
		provider.syncConfig(cfgmgr.getSchemas(), cfgmgr.getResourceTypes());
	}
	
	public Collection<Schema> loadSchemas() throws ScimException {
		return provider.loadSchemas();
	}
	
	public Collection<ResourceType> loadResourceTypes() throws ScimException {
		return provider.loadResourceTypes();
	}

	
}
