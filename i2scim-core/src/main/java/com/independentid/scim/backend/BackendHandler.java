/*
 * Copyright 2021.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.core.err.DuplicateTxnException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.TransactionRecord;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Startup;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

@Startup
@Singleton
@Default
public class BackendHandler {


	private final static InjectionManager injectionManager = InjectionManager.getInstance();

	private final static Logger logger = LoggerFactory.getLogger(BackendHandler.class);
	public static final String SCIM_PROVIDER_BEAN = "scim.prov.providerClass";
	public static final String DEFAULT_PROVIDER_BEAN = "com.independentid.scim.backend.mongo.MongoProvider";

	//private final static HashMap<ServletContext,BackendHandler> handlers = new HashMap<ServletContext,BackendHandler>();
	
	private static IScimProvider provider = null;

	private static IIdentifierGenerator generator = null;
	private static BackendHandler instance = null;

	private BackendHandler() {

	}

	public synchronized static BackendHandler getInstance() {
		if (instance != null)
			return instance;
		instance = new BackendHandler();
		// For CDI Injection timing reasons, we want to delay initialization
		/*
		try {
			instance.init();
		} catch (ClassNotFoundException | InstantiationException | BackendException ignored) {
		}*/
		return instance;
	}

	/**
	 * Causes the backend system to initialize by dynamically creating the configured provider as
	 * specified in the property scim.provider.bean. Once created, the init(config) method of the
	 * provider is called to initialize the provider.
	 * @throws ClassNotFoundException Thrown when the provider class (specified in scim.provider.bean property)
	 * could not be loaded.
	 * @throws InstantiationException Thrown when the provider specified failed to be created using newInstance() method.
	 * @throws BackendException An error thrown by the provider related to SCIM (e.g. parsing schema).
	 */

	public synchronized void init()
			throws ClassNotFoundException, InstantiationException, BackendException {
		if (isReady()) return;

		if (provider == null) {
			logger.info("=====BackendHandler initialization=====");
			provider = injectionManager.getProvider();
			logger.info("Starting IScimProvider Provider: " + injectionManager.getProviderName());
		}
		BackendHandler.provider.init();

		generator = injectionManager.getGenerator();
	}

	@PreDestroy
	public void shutdown() {
		if (provider != null)
			provider.shutdown();
	}

	public IScimProvider getProvider() {
		checkProvider();
		return provider;
	}
	
	public boolean isReady() {
		if (provider == null) return false;
		return provider.ready();
	}

	public void checkProvider() {

		if (provider == null) {
			try {
				this.init();
			} catch (ClassNotFoundException | InstantiationException | BackendException e) {
				logger.error(e.getMessage(),e);
			}
		}
			//provider = injectionManager.getProvider();
	}

	
	public ScimResponse create(RequestCtx ctx,final ScimResource res)
			throws ScimException, BackendException {
		checkProvider();
		return provider.create(ctx, res);
	}

	public ScimResponse get(RequestCtx ctx) throws ScimException, BackendException {
		checkProvider();
		return provider.get(ctx);
	}

	public ScimResponse replace(RequestCtx ctx, final ScimResource res)
			throws ScimException, BackendException {
		checkProvider();
		return provider.put(ctx, res);
	}

	public ScimResponse patch(RequestCtx ctx, JsonPatchRequest	req) throws ScimException, BackendException {
		checkProvider();
		return provider.patch(ctx, req);
	}

	public ScimResponse bulkRequest(RequestCtx ctx, JsonNode node)
			throws ScimException, BackendException {
		checkProvider();
		return provider.bulkRequest(ctx, node);
	}

	public ScimResponse delete(RequestCtx ctx) throws ScimException, BackendException {
		checkProvider();
		return provider.delete(ctx);
	}

	public void syncConfig(SchemaManager smgr) throws IOException {
		checkProvider();
		provider.syncConfig(smgr.getSchemas(), smgr.getResourceTypes());
	}
	
	public Collection<Schema> loadSchemas() throws ScimException {
		checkProvider();
		return provider.loadSchemas();
	}
	
	public Collection<ResourceType> loadResourceTypes() throws ScimException {
		checkProvider();
		return provider.loadResourceTypes();
	}

	public ScimResource getTransactionRecord(String transid) throws BackendException {
		checkProvider();
		return provider.getTransactionRecord(transid);
	}

	public void storeTransactionRecord(TransactionRecord record) throws DuplicateTxnException {
		checkProvider();
		provider.storeTransactionRecord(record);
	}

	
}
