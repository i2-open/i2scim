/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015,2020 Phillip Hunt, All Rights Reserved                   *
 *                                                                    *
 *  Confidential and Proprietary                                      *
 *                                                                    *
 *  This unpublished source code may not be distributed outside       *
 *  “Independent Identity Org”. without express written permission of *
 *  Phillip Hunt.                                                     *
 *                                                                    *
 *  People at companies that have signed necessary non-disclosure     *
 *  agreements may only distribute to others in the company that are  *
 *  bound by the same confidentiality agreement and distribution is   *
 *  subject to the terms of such agreement.                           *
 **********************************************************************/

package com.independentid.scim.backend;

import java.io.IOException;
import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimException;

@Component("BackendHandler")
public class BackendHandler {

	private final static Logger logger = LoggerFactory.getLogger(BackendHandler.class);
	
	//private final static HashMap<ServletContext,BackendHandler> handlers = new HashMap<ServletContext,BackendHandler>();

	@Autowired
	ApplicationContext ctx;
	
	private IScimProvider provider = null;
	
	@Value("${scim.provider.bean:MongoDao}")
	private String providerBean;
	
	private static BackendHandler self = null;
	
	public BackendHandler() {
		
		
	}
	
	@PostConstruct
	public synchronized boolean init() throws ClassNotFoundException, InstantiationException, IllegalAccessException, BackendException {
		if (provider == null) {
			logger.info("=====BackendHandler initialization=====");;
			logger.debug("Configured IScimProvider Provider: "+providerBean);
			
			provider = (IScimProvider) ctx.getBean(providerBean);
			if (provider == null) {
				logger.error("Undefined backend provider: scim.provider.bean");
				throw new InstantiationException("IScimProvider provider not initialized error");
			}
		}
		
		self = (BackendHandler) this.ctx.getBean("BackendHandler");
		
		if (!provider.ready())
			provider.init();
		
		return provider.ready();
		
	}
	
	@PreDestroy
	public void shutdown() {
		if (this.provider != null)
			this.provider.shutdown();
	}
	
	public static BackendHandler getInstance() {
		
		return self;
	}
	
	public IScimProvider getProvider() {
		return this.provider;
	}
	
	public boolean isReady() {
		return this.provider.ready();
	}

	
	public ScimResponse create(RequestCtx ctx,final ScimResource res)
			throws ScimException, BackendException {
		return this.provider.create(ctx, res);
	}

	public ScimResponse get(RequestCtx ctx) throws ScimException, BackendException {
		return this.provider.get(ctx);
	}

	public ScimResponse replace(RequestCtx ctx, final ScimResource res)
			throws ScimException, BackendException {
		return this.provider.replace(ctx, res);
	}

	public ScimResponse patch(RequestCtx ctx, JsonPatchRequest	req) throws ScimException, BackendException {
		return this.provider.patch(ctx, req);
	}

	public ScimResponse bulkRequest(RequestCtx ctx, JsonNode node)
			throws ScimException, BackendException {
		return this.provider.bulkRequest(ctx, node);
	}

	public ScimResponse delete(RequestCtx ctx) throws ScimException, BackendException {
		return this.provider.delete(ctx);
	}

	public void syncConfig(ConfigMgr cfgmgr) throws IOException {
		this.provider.syncConfig(cfgmgr.getSchemas(), cfgmgr.getResourceTypes());
	}
	
	public Collection<Schema> loadSchemas() throws ScimException {
		return this.provider.loadSchemas();
	}
	
	public Collection<ResourceType> loadResourceTypes() throws ScimException {
		return this.provider.loadResourceTypes();
	}

	
}
