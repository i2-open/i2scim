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
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.Startup;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Supplier;

//@ApplicationScoped
@Singleton
@Startup
@Named("BackendHandler")
public class BackendHandler {

	private final static Logger logger = LoggerFactory.getLogger(BackendHandler.class);
	public static final String SCIM_PROVIDER_BEAN = "scim.prov.providerClass";
	public static final String DEFAULT_PROVIDER_BEAN = "com.independentid.scim.backend.mongo.MongoProvider";

	//private final static HashMap<ServletContext,BackendHandler> handlers = new HashMap<ServletContext,BackendHandler>();
	
	private static IScimProvider provider = null;

	@ConfigProperty(name = "scim.prov.providerClass", defaultValue="com.independentid.scim.backend.mongo.MongoProvider")
	String providerName;

	@Inject
	Instance<IScimProvider> providers;

	@Inject
	Instance<IIdentifierGenerator> generators;

	IIdentifierGenerator generator = null;

	private final Supplier<IScimProvider> providerSupplier;
	
	public BackendHandler(){
		// Can't do anything here that involves injected properties etc!
		this.providerSupplier = new CachedProviderSupplier<>(this::getProvider);
	}

	/**
	 * Causes the backend system to initialize by dynamically creating the configured provider as
	 * specified in the property scim.provider.bean. Once created, the init(config) method of the
	 * provider is called to initialize the provider.
	 * @return true if provider is ready.
	 * @throws ClassNotFoundException Thrown when the provider class (specified in scim.provider.bean property)
	 * could not be loaded.
	 * @throws InstantiationException Thrown when the provider specified failed to be created using newInstance() method.
	 * @throws BackendException An error thrown by the provider related to SCIM (e.g. parsing schema).
	 */
	@PostConstruct
	public synchronized boolean init()
			throws ClassNotFoundException, InstantiationException, BackendException {
		if (provider == null) {
			logger.info("=====BackendHandler initialization=====");
			logger.info("Starting IScimProvider Provider: " + providerName);

			provider = this.providerSupplier.get();
		}



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

	public IIdentifierGenerator getGenerator() {
		if (generator == null)
			initGenerator();
		return generator;
	}

	private void initGenerator() {
		for (IIdentifierGenerator gen : generators) {
			String provName = gen.getProviderClass();
			if (provName.equals(providerName)) {
				generator = gen;
				return;
			}
		}
	}

	public synchronized IScimProvider getProvider() {
		if (BackendHandler.provider != null)
			return BackendHandler.provider;

		if (providerName == null)
			logger.error("Unable to instantiate, IScimProvider bean class property (scim.provider.bean) not defined.");


		for (IScimProvider item : providers) {
			String cname = item.getClass().toString();
			if (cname.contains(providerName)) {

				try {
					item.init();
				} catch (BackendException e) {
					logger.error("Eror initializing provider: "+e.getMessage(),e);
					return null;
				}
				initGenerator();
				BackendHandler.provider = item;
				return item;
			}
		}
		return null;
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
		return provider.put(ctx, res);
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

	public void syncConfig(SchemaManager smgr) throws IOException {
		if (provider == null)
			provider = providerSupplier.get();
		provider.syncConfig(smgr.getSchemas(), smgr.getResourceTypes());
	}
	
	public Collection<Schema> loadSchemas() throws ScimException {
		return provider.loadSchemas();
	}
	
	public Collection<ResourceType> loadResourceTypes() throws ScimException {
		return provider.loadResourceTypes();
	}

	
}
