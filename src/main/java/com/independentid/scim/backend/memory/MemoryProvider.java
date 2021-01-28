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
package com.independentid.scim.backend.memory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.InvalidValueException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Named;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;

/**
 * @author pjdhunt
 *
 */
@ApplicationScoped
@Alternative
@Named("MemoryProvider")
public class MemoryProvider implements IScimProvider {

	private static final Logger logger = LoggerFactory.getLogger(MemoryProvider.class);
	
	private static IScimProvider singleton = null;

	public final static String PARAM_PERSIST_FILE = "scim.memory.persist.file";
	public final static String DEFAULT_FILE = "~/scimdata.json";
	
	private final HashMap<String,ScimResource> mainMap;

	static ConfigMgr config =  null;
	SchemaManager smgr;
	
	private boolean ready;
	
	//@ConfigProperty(name = "scim.memdao.memoryFile", defaultValue="scimdata.json")
	String storeFile = System.getProperty(PARAM_PERSIST_FILE, DEFAULT_FILE);
	
	/**
	 * 
	 */
	public MemoryProvider() {
		this.mainMap = new HashMap<>();
		this.ready = false;
	}
	
	public static IScimProvider getProvider() {
		if (singleton == null)
			singleton = new MemoryProvider();
		// singleton.init();
		return singleton;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#create(com.independentid.scim.protocol.RequestCtx, com.independentid.scim.resource.ScimResource)
	 */
	@Override
	public ScimResponse create(RequestCtx ctx, ScimResource res) throws BackendException {
		UUID uid = UUID.randomUUID();
		String sid = uid.toString();
		res.setId(sid);
		
		Meta meta = res.getMeta();
		Date created = new Date();
		meta.setCreatedDate(created);
		meta.setLastModifiedDate(created);
		String buf = '/' +
				ctx.getResourceContainer() +
				'/' +
				sid;
		meta.setLocation(buf);
		
		
		//ServletContext sctx = ctx.getServletContext();
						
		String path = ctx.getResourceContainer();
		ResourceType type = smgr.getResourceTypeByPath(path);
		if (type != null)
			meta.setResourceType(type.getName());
		
		
		if (meta.getResourceType() == null)
			throw new BackendException("Unable to determine object resource endpoint");
		
		this.mainMap.put(res.getId(), res);
		
		ListResponse resp = new ListResponse(res,ctx, config);
		resp.setStatus(ScimResponse.ST_CREATED);
		resp.setLocation(res.getMeta().getLocation());
		if (logger.isDebugEnabled())
			logger.debug("Added: \n"+res.toString()+"\n");
		return resp;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#get(com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public ScimResponse get(RequestCtx ctx) throws ScimException {
		String id = ctx.getPathId();
		if (id == null) {
			
			// TODO Filtered search not implemented on GET!!

			ArrayList<ScimResource> vals = new ArrayList<>(this.mainMap.values());
			if (logger.isDebugEnabled()) {
				String buf = "Search: \nURL:\t" + ctx.getPath() +
						"\nMatches:\t" + vals.size();
				logger.debug(buf);
			}
			return new ListResponse(vals,ctx, config);
		}
		ScimResource res = this.mainMap.get(id);
		if (res == null) {
			ScimResponse resp = new ScimResponse();
			resp.setStatus(ScimResponse.ST_NOTFOUND);
			resp.setDetail("Search Path: "+ctx.getPath()+" Filter="+
			((ctx.getFilter() != null)?ctx.getFilter().getFilterStr():"NONE"));
			if (logger.isDebugEnabled()) {
				String buf = "Search: \nURL:\t" + ctx.getPath() +
						"\nMatches:\t" + "NO MATCH";
				logger.debug(buf);
			}
			return resp;
		}
		
		if (logger.isDebugEnabled()) {
			String buf = "Search: \nURL:\t" + ctx.getPath() +
					"\nMatches:\t" + "1";
			logger.debug(buf);
		}
		return new ListResponse(res,ctx, config);
	}
	
	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#getResource(com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public ScimResource getResource(RequestCtx ctx) throws ScimException {
		String id = ctx.getPathId();
		if (id == null)
			throw new InvalidValueException("Missing resource identifier exception");
		
		ScimResource res = this.mainMap.get(id);
		if (logger.isDebugEnabled())
			logger.debug("Get Resource: \n"+res.toString()+"\n");
		return res;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#replace(com.independentid.scim.protocol.RequestCtx, com.independentid.scim.resource.ScimResource)
	 */
	@Override
	public ScimResponse replace(RequestCtx ctx, ScimResource replaceResource) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#patch(com.independentid.scim.protocol.RequestCtx, com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	public ScimResponse patch(RequestCtx ctx, JsonPatchRequest req) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#bulkRequest(com.independentid.scim.protocol.RequestCtx, com.fasterxml.jackson.databind.JsonNode)
	 */
	
	public ScimResponse bulkRequest(RequestCtx ctx, JsonNode node) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#delete(com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public ScimResponse delete(RequestCtx ctx) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#init(javax.servlet.ServletConfig)
	 */
	@Override
	@PostConstruct
	public void init(ConfigMgr cfg) {
		config = cfg;
		smgr = cfg.getSchemaManager();
		if (singleton == null)
			singleton = this;
		//String file = cfg.getInitParameter(PARAM_PERSIST_FILE);
	    String file = this.storeFile;
		if (file == null)
			file = DEFAULT_FILE;
		
		this.storeFile = file;
		
		File data = new File(file);
		if (!data.exists()) {
			System.err.println("Memory store file not found. Initializing new file: "+file);
			return;  // assume it is a new file
		}
				
		try {
			JsonNode node = JsonUtil.getJsonTree(data);
			for (JsonNode resNode : node) {
				ScimResource res;
				try {
					res = new ScimResource(smgr, resNode, null, null);
					this.mainMap.put(res.getId(), res);
				} catch (ScimException | ParseException e) {
					System.err.println("Error parsing SCIM resource");
					e.printStackTrace();
				}

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.ready = true;

	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#ready()
	 */
	@Override
	public boolean ready() {
		return this.ready;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#shutdown()
	 */
	@Override
	public synchronized void shutdown() {
		this.ready = false;
		
		if (storeFile == null)
			storeFile = DEFAULT_FILE;
		
		File data = new File(storeFile);
		if (!data.exists()) {
			try {
				data.createNewFile();

			} catch (IOException e) {
				logger.error("Unable to persist Memory provider data to: "+storeFile+". Error: "+e.getLocalizedMessage(),e);
			}
		}
		try {
			FileWriter writer = new FileWriter(data);
						
			JsonGenerator gen = JsonUtil.getGenerator(writer, false);

			for (ScimResource scimResource : this.mainMap.values())
				try {
					scimResource.serialize(gen, null, false);
				} catch (ScimException e) {
					logger.error("Error during shutdown. " + e.getLocalizedMessage(), e);
				}
			gen.close();
			writer.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

	@Override
	public PersistStateResource getConfigState()  {
		//TODO This is just temporary to satisfy the interface requirements for health check
		if (ready())
			return new PersistStateResource(smgr,smgr.getResourceTypeCnt(), smgr.getSchemaCnt());
		return null;
	}

	@Override
	public Collection<Schema> loadSchemas() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<ResourceType> loadResourceTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void syncConfig(Collection<Schema> schemaCol, Collection<ResourceType> resTypeCol) {
		// TODO Auto-generated method stub
		
	}

}
