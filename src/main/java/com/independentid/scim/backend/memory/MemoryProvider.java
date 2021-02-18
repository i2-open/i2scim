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
import com.independentid.scim.core.err.*;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.TransactionRecord;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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
@Priority(50)
@Named("MemoryProvider")
public class MemoryProvider implements IScimProvider {

	private static final Logger logger = LoggerFactory.getLogger(MemoryProvider.class);
	
	private static IScimProvider singleton = null;

	public final static String PARAM_PERSIST_FILE = "scim.memory.persist.file";
	public final static String DEFAULT_FILE = "~/scimdata.json";
	
	private final HashMap<String,ScimResource> mainMap;

	@Inject
	ConfigMgr configMgr;

	@Inject
	SchemaManager schemaManager;
	
	private boolean ready;
	
	@ConfigProperty(name =PARAM_PERSIST_FILE, defaultValue=DEFAULT_FILE)
	String storeFile;

	@ConfigProperty(name = ConfigMgr.SCIM_QUERY_MAX_RESULTSIZE, defaultValue= ConfigMgr.SCIM_QUERY_MAX_RESULTS_DEFAULT)
	protected int maxResults;

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
	public ScimResponse create(RequestCtx ctx, ScimResource res) throws ScimException, BackendException {
		if (res.getId() == null) {
			UUID uid = UUID.randomUUID();
			String sid = uid.toString();
			res.setId(sid);
		}
		
		Meta meta = res.getMeta();
		Date created = new Date();
		meta.setCreatedDate(created);
		meta.setLastModifiedDate(created);
		String buf = '/' +
				ctx.getResourceContainer() +
				'/' +
				res.getId();
		meta.setLocation(buf);
		
		
		//ServletContext sctx = ctx.getServletContext();
						
		String path = ctx.getResourceContainer();
		ResourceType type = schemaManager.getResourceTypeByPath(path);
		if (type != null)
			meta.setResourceType(type.getName());

		if (!path.equals(TransactionRecord.TRANS_CONTAINER))
			try {
				meta.addRevision(ctx, this, created);
			} catch (BackendException e) {
				return handleUnexpectedException(e);
			}
		
		if (meta.getResourceType() == null)
			throw new BackendException("Unable to determine object resource endpoint");
		
		this.mainMap.put(res.getId(), res);
		
		ListResponse resp = new ListResponse(res,ctx);
		resp.setStatus(ScimResponse.ST_CREATED);
		resp.setLocation(res.getMeta().getLocation());
		if (logger.isDebugEnabled())
			logger.debug("Added: \n"+res.toString()+"\n");
		return resp;
	}

	private ScimResponse handleUnexpectedException(Exception e) {
		logger.error("Unhandled exception: "+e.getLocalizedMessage(),e);
		return new ScimResponse(ScimResponse.ST_INTERNAL,e.getLocalizedMessage(),null);
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
			return new ListResponse(vals,ctx, maxResults);
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
		return new ListResponse(res,ctx);
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
	public ScimResponse replace(RequestCtx ctx, ScimResource replaceResource) throws ScimException {
		ScimResource origRes = getResource(ctx);
		if (origRes == null)
			return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);

		if (!origRes.checkPreCondition(ctx))
			return new ScimResponse(new PreconditionFailException(
					"ETag predcondition does not match"));
		origRes.replaceResAttributes(replaceResource, ctx);
		// Nothing to persist as this is in Memory

		return completeResponse(origRes,ctx);

	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#patch(com.independentid.scim.protocol.RequestCtx, com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	public ScimResponse patch(RequestCtx ctx, JsonPatchRequest req) throws ScimException {
		ScimResource res = getResource(ctx);
		if (res == null)
			return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);
		res.modifyResource(req, ctx);

		return completeResponse(res, ctx);

	}

	@SuppressWarnings("DuplicatedCode")
	private ScimResponse completeResponse(ScimResource res, RequestCtx ctx) throws ScimException {
		String path = ctx.getResourceContainer();
		Meta meta = res.getMeta();
		Date modDate = new Date();
		meta.setLastModifiedDate(modDate);

		if (!path.equals(TransactionRecord.TRANS_CONTAINER))
			try {
				meta.addRevision(ctx, this, modDate);
			} catch (BackendException  e) {
				return handleUnexpectedException(e);
			}

		String etag = res.calcVersionHash();
		meta.setVersion(etag);

		// Nothing needs to be done as the original object modified directly.

		ResourceResponse resp = new ResourceResponse(res, ctx);
		resp.setStatus(ScimResponse.ST_OK);
		resp.setLocation(res.getMeta().getLocation());
		resp.setETag(res.getMeta().getVersion());

		return resp;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#bulkRequest(com.independentid.scim.protocol.RequestCtx, com.fasterxml.jackson.databind.JsonNode)
	 */
	
	public ScimResponse bulkRequest(RequestCtx ctx, JsonNode node) throws ScimException {
		throw new NotImplementedException("Bulk not implemented for MemoryProvider");
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#delete(com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public ScimResponse delete(RequestCtx ctx) {
		ScimResource res = this.mainMap.remove(ctx.getPathId());

		if (res == null)
			return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);

		// return success
		return new ScimResponse(ScimResponse.ST_NOCONTENT, null, null);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#init(javax.servlet.ServletConfig)
	 */
	@Override
	//@PostConstruct  We do not want auto start..backendhandler will call this.
	public void init() {
		//this.configMgr = configMgr;
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
					res = new ScimResource(schemaManager, resNode, null, null);
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
			return new PersistStateResource(schemaManager, schemaManager.getResourceTypeCnt(), schemaManager.getSchemaCnt());
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

	/**
	 * Used to obtain information about the transaction previously committed in the provider. The value in
	 * Meta.revisions can be used as the query term.
	 * @param transid The transaction id (e.g. from Meta.revisions.value or RequestCtx.getTranID().
	 * @return The TransactionRecord in the form of {@link ScimResource} containing information about the transaction (a
	 * ScimResource).
	 */
	@Override
	public ScimResource getTransactionRecord(String transid) {
		try {
			RequestCtx ctx = new RequestCtx(TransactionRecord.TRANS_CONTAINER,transid,null,schemaManager);

			return getResource(ctx);
		} catch (ScimException e) {
			// Would only expect a parsing error resulting from external data.
			logger.error("Unexpected scim error: "+e.getMessage(),e);
		}
		return null;
	}

	/**
	 * Used by the replication event processing system to detect if the cluster has already processed a transaction in
	 * the case of 1 or more cluster members receiving the same event.
	 * @param transid The transaction UUID string value to be checked (from {@link RequestCtx#getTranId()}).
	 * @return true if present in the transaction store of the provider.
	 */
	@Override
	public boolean isTransactionPresent(String transid) {
		return this.mainMap.containsKey(transid);
	}

	/**
	 * This method is typically called by a CreateOp, DeleteOp, Put or Patch Op, after any write transaction.
	 * @param record A {@link TransactionRecord} containing information about the update.
	 * @throws DuplicateTxnException if the transactionId already exists in the provider, an exception is thrown.
	 */
	@Override
	public void storeTransactionRecord(TransactionRecord record) throws DuplicateTxnException {
		ScimResponse resp = null;
		try {
			RequestCtx ctx = new RequestCtx(TransactionRecord.TRANS_CONTAINER,null,null,schemaManager);
			// ScimResponse(ScimResponse.ST_BAD_REQUEST,e.getLocalizedMessage(),ScimResponse.ERR_TYPE_UNIQUENESS);
			resp = create(ctx,record);

		} catch (BackendException | ScimException e) {
			logger.error("Unexpected scim error: "+e.getMessage(),e);
		}
		if (resp != null && resp.getScimErrorType().equals(ScimResponse.ERR_TYPE_UNIQUENESS))
			throw new DuplicateTxnException("Transactionid is not unique.");
	}

}
