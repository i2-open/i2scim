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
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author pjdhunt
 *
 */
//@ApplicationScoped
@Singleton
@Priority(50)
@Named("MemoryProvider")
public class MemoryProvider implements IScimProvider {

	private static final Logger logger = LoggerFactory.getLogger(MemoryProvider.class);
	public static final String SCHEMA_JSON = "schema.json";
	public static final String RESOURCE_TYPES_JSON = "resourceTypes.json";

	public final static String PARAM_MAX_BACKUPS = "scim.prov.memory.maxbackups";
	public final static String DEFAULT_MAX_BACKUPS = "24";
	public final static String PARAM_BACKUP_MINS = "scim.prov.memorybackup.mins";
	public final static String DEFAULT_BACKUP_MINS = "60";
	public final static String PARAM_PERSIST_DIR = "scim.prov.memory.persist.dir";
	public final static String DEFAULT_PERSIST_DIR = "./scimdata";
	public final static String PARAM_PERSIST_FILE = "scim.prov.memory.persist.file";
	public final static String DEFAULT_FILE = "scimdata.json";
	
	protected final ConcurrentHashMap<String,ScimResource> mainMap;
	protected final HashMap<String,Map<String,ScimResource>> containerMaps;

	@Inject
	SchemaManager schemaManager;

	@Inject
	MemoryIdGenerator generator;
	
	private boolean ready;

	@ConfigProperty(name = "scim.prov.memory.persist.dir", defaultValue = "./scimdata")
	String storeDir;

	@ConfigProperty(name = "scim.prov.memory.persist.file", defaultValue="scimdata.json")
	String storeFile;

	File dataFile = null;

	@ConfigProperty(name = ConfigMgr.SCIM_QUERY_MAX_RESULTSIZE, defaultValue= ConfigMgr.SCIM_QUERY_MAX_RESULTS_DEFAULT)
	protected int maxResults;

	@ConfigProperty(name = "scim.prov.memory.maxbackups", defaultValue= "24")
	protected int maxBackups;

	@ConfigProperty(name = "scim.prov.memory.backup.mins", defaultValue = "60")
	protected int backupMins;

	@ConfigProperty(name = "scim.prov.memory.test", defaultValue="false")
	boolean resetDb;

	Timer timer = new Timer("MemBackupTimer");

	boolean isModified = false;

	/**
	 * 
	 */
	public MemoryProvider() {
		this.mainMap = new ConcurrentHashMap<>();
		this.containerMaps = new HashMap<>();  // Concurrency issues are not expected in the map of maps (just the internal maps)
		this.ready = false;
	}

	private void resetMemDirectory() {
		// Reset the memory provider
		logger.warn("*** Resetting Memory database files ***");
		File memdir = new File (storeDir);
		File[] files = memdir.listFiles();
		if (files != null)
			for (File afile: files)
				afile.delete();
	}

	@Override
	//@PostConstruct  We do not want auto start..backendhandler will call this.
	public synchronized void init() {

		//this.configMgr = configMgr;

		if (this.ready)
			return;  // run only once!
		File directory = new File(storeDir);
		if (!directory.exists()) {
			directory.mkdir();
		}
		if (resetDb)
			resetMemDirectory();

		dataFile = new File(storeDir,storeFile);

		logger.info("======Initializing SCIM Memory Provider======");
		logger.info("\tUsing storage file: "+dataFile.toString());
		logger.info("\tBackups refreshing every "+backupMins+" minutes to a max of "+maxBackups+ " files.");

		if (!dataFile.exists())
			logger.warn("\tMemory store file not found. Initializing new file: "+dataFile.toString());
		else {
			// load the existing data
			try {
				logger.debug("\tLoading data file");
				JsonNode node = JsonUtil.getJsonTree(dataFile);
				for (JsonNode resNode : node) {
					ScimResource res;
					try {
						res = new ScimResource(schemaManager, resNode, null, null);
						addResource(res);
					} catch (ScimException | ParseException e) {
						logger.error("Unexpected error parsing SCIM JSON database: " + e.getMessage(), e);
						this.ready = false;
						return;
					}

				}
			} catch (IOException e) {
				logger.error("Unexpected IO error reading SCIM JSON database: " + e.getMessage(), e);
				this.ready = false;
				return;
			}

		}

		if (backupMins == 0)
			logger.warn("\tPeriodic backups to disk *disabled* due to " + PARAM_BACKUP_MINS + " set to 0.");
		else {
			TimerTask backupTask = new PersistTask(this);

			TimeUnit milliTime = TimeUnit.MILLISECONDS;
			long delay = milliTime.convert(backupMins, TimeUnit.MINUTES);
			timer.scheduleAtFixedRate(backupTask, delay, delay);
		}
		this.ready = true;
		logger.info("======SCIM Memory Provider initialized ======");

	}

	static class PersistTask extends TimerTask {
		MemoryProvider prov;
		PersistTask(MemoryProvider handle) {
			prov = handle;
		}

		public void run() {
			if (prov.isModified)
				prov.writeDatabase();
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#create(com.independentid.scim.protocol.RequestCtx, com.independentid.scim.resource.ScimResource)
	 */
	@Override
	public ScimResponse create(RequestCtx ctx, ScimResource res) throws ScimException, BackendException {
		String path = ctx.getResourceContainer();
		if (path == null || path.equals("/"))
			return new ScimResponse(ScimResponse.ST_NOSUPPORT,"Creating resource at root not supported",null);

		if (res.getId() == null)
			res.setId(generator.getNewIdentifier());

		Meta meta = res.getMeta();
		Date created = new Date();
		meta.setCreatedDate(created);
		meta.setLastModifiedDate(created);
		if (!path.equals(TransactionRecord.TRANS_CONTAINER))
			try {
				meta.addRevision(ctx, this, created);
			} catch (BackendException e) {
				return handleUnexpectedException(e);
			}
		String buf = '/' + path + '/' + res.getId();
		meta.setLocation(buf);

		ResourceType type = schemaManager.getResourceTypeByPath(path);
		if (type != null)
			meta.setResourceType(type.getName());

		addResource(res);

		ListResponse resp = new ListResponse(res,ctx);
		resp.setStatus(ScimResponse.ST_CREATED);
		resp.setLocation(res.getMeta().getLocation());

		return resp;
	}

	private void addResource(ScimResource res) {
		this.mainMap.put(res.getId(),res);

		Map<String, ScimResource> cmap = this.containerMaps.computeIfAbsent(res.getContainer(), k -> new ConcurrentHashMap<>());
		cmap.put(res.getId(),res);
		isModified = true;  // set memory as modified compared to disk
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

		if (ctx.getPathId() == null) {
			String type = ctx.getResourceContainer();

			Collection<ScimResource> candidates = null;
			if (type == null || type.equals("/"))
				candidates = this.mainMap.values();
			else {
				Map<String, ScimResource> map = this.containerMaps.get(type);
				if (map != null)
					candidates = map.values();
			}

			if (candidates == null || candidates.isEmpty())
				return new ScimResponse(ScimResponse.ST_NOTFOUND,null,null);

			if (ctx.getFilter() == null)
				return new ListResponse(new ArrayList<>(candidates),ctx, maxResults);

			//Iterate through the candidates to locate matches.
			ArrayList<ScimResource> results = new ArrayList<>();
			for (ScimResource res : candidates) {
				if (Filter.checkMatch(res, ctx))
					results.add(res);
				if (results.size() > maxResults)
					break;
			}

			return new ListResponse(results,ctx, maxResults);

		}

		ScimResource res = getResource(ctx);  // this has the filter processed
		if (res == null && ctx.hasNoClientFilter())
			return new ScimResponse(ScimResponse.ST_NOTFOUND,null,null);

		// if this is a get of a specific resource return the object
		if (res != null && ctx.hasNoClientFilter()) {
			if (ctx.getFilter() != null) {
				//Apply the targetFilter
				if (Filter.checkMatch(res,ctx))
					return new ResourceResponse(res, ctx);
				else
					return new ScimResponse(ScimResponse.ST_NOTFOUND,null,null);
			}
		}

		if (res == null)
			return new ListResponse(ctx, maxResults);  // return an empty response
		else
			// if this is a filtered request, must return a list response per RFC7644 Sec 3.4.2
			return new ListResponse(res, ctx);  // return the single item

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
		if (Filter.checkMatch(res,ctx))
			return res;
		return null;

	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#replace(com.independentid.scim.protocol.RequestCtx, com.independentid.scim.resource.ScimResource)
	 */
	@Override
	public ScimResponse put(RequestCtx ctx, ScimResource replaceResource) throws ScimException {
		ScimResource origRes = getResource(ctx);
		if (origRes == null)
			return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);

		if (!origRes.checkPreCondition(ctx))
			return new ScimResponse(new PreconditionFailException(
					"ETag predcondition does not match"));
		origRes.replaceResAttributes(replaceResource, ctx);
		// Nothing to persist as this is in Memory
		isModified = true;  // set memory as modified compared to disk
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
		isModified = true;  // set memory as modified compared to disk
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

		String type = ctx.getResourceContainer();
		this.containerMaps.get(type).remove(ctx.getPathId());
		isModified = true;  // set memory as modified compared to disk
		// return success
		return new ScimResponse(ScimResponse.ST_NOCONTENT, null, null);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#ready()
	 */
	@Override
	public boolean ready() {
		return this.ready;
	}

	private void rollFile() {
		if (dataFile.exists()) {
			Format formatter = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");

			String[] parts = dataFile.getName().split("\\.");
			String newName = parts[0]+"_"+formatter.format(new Date()) + ".json";
			File rollName = new File(storeDir,newName);
			logger.info("Rolling database file to: "+rollName.toString());
			dataFile.renameTo(rollName);

			File directory = new File(storeDir);

			File[] files = directory.listFiles((f, name) -> name.startsWith(parts[0]));

			if (files != null && files.length > maxBackups) {
				logger.info("Purging old data files");
				for (int i = maxBackups; i < files.length; i++) {
					logger.debug("\tDeleteing data file "+files[i]);
					files[i].delete();
				}
			}
		}



	}

	protected synchronized void writeDatabase() {

		if (dataFile.exists())
			rollFile();

		try {
			dataFile.createNewFile();
			FileWriter writer = new FileWriter(dataFile);

			JsonGenerator gen = JsonUtil.getGenerator(writer, false);
			gen.writeStartArray();
			for (ScimResource scimResource : this.mainMap.values())
				try {
					scimResource.serialize(gen, null, false);
				} catch (ScimException e) {
					logger.error("Unexpected error serializing resource: " + e.getLocalizedMessage(), e);
				}
			gen.writeEndArray();
			gen.close();
			writer.close();
			isModified = false;  // set memory as modified compared to disk
			logger.info("Memory provider successfully saved to: "+this.storeFile);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#shutdown()
	 */
	@Override
	public synchronized void shutdown() {
		timer.cancel();
		if (isModified)
			writeDatabase();

		this.mainMap.clear();
		this.containerMaps.clear();
		this.ready = false;

	}

	@Override
	public PersistStateResource getConfigState()  {

		return (PersistStateResource) this.mainMap.get(PersistStateResource.CONFIG_ID);
	}

	@Override
	public Collection<Schema> loadSchemas()  {
		File schemaFile = new File(storeDir,SCHEMA_JSON);
		if (!schemaFile.exists()) {
			logger.warn("Scheam file not found: "+schemaFile.toString());
			return null;
		}

		Collection<Schema> schemas = new ArrayList<>();

		JsonNode node;
		try {
			node = JsonUtil.getJsonTree(schemaFile);
		} catch (IOException e) {
			logger.error("Unexpected error parsing: "+schemaFile.toString()+", error: "+e.getMessage());
			return null;
		}
		if (node.isObject())
			node = node.get(0);
		if (node.isArray()) {
			for (JsonNode schemaNode : node) {
				Schema schema;
				try {
					schema = new Schema(schemaManager, schemaNode);
					schemas.add(schema);

				} catch (SchemaException e) {
					logger.warn("SchemaException while parsing schema config: "+e.getMessage(), e);
				}
			}

		} else {
			logger.warn("Dectected unexpected node while parsing Schema: " + node.getNodeType().toString());
			//System.out.println("Detected node endpoint "+node.getNodeType().toString());
		}

		return schemas;
	}


	@Override
	public Collection<ResourceType> loadResourceTypes() {
		File typeFile = new File(storeDir,RESOURCE_TYPES_JSON);
		if (!typeFile.exists()) {
			logger.warn("Scheam file not found: "+typeFile.toString());
			return null;
		}

		Collection<ResourceType> types = new ArrayList<>();

		JsonNode node;
		try {
			node = JsonUtil.getJsonTree(typeFile);
		} catch (IOException e) {
			logger.error("Unexpected error parsing: "+typeFile.toString()+", error: "+e.getMessage());
			return null;
		}
		if (node.isObject())
			node = node.get(0);

		if (node.isArray()) {
			for (JsonNode typeNode : node) {
				ResourceType type;
				try {
					type = new ResourceType( typeNode,schemaManager);
					types.add(type);

				} catch (SchemaException e) {
					logger.warn("SchemaException while parsing Resource type: "+e.getMessage(), e);
				}
			}

		} else {
			logger.warn("Dectected unexpected node while parsing Resource Types: " + node.getNodeType().toString());
			//System.out.println("Detected node endpoint "+node.getNodeType().toString());
		}

		return types;
	}

	@Override
	public void syncConfig(Collection<Schema> schemaCol, Collection<ResourceType> resTypeCol) {
		int scnt = schemaCol.size();
		int rcnt = resTypeCol.size();

		PersistStateResource confState = new PersistStateResource(schemaManager,rcnt,scnt);

		addResource(confState);

		File schemaFile = new File(storeDir, SCHEMA_JSON);
		try {
			schemaFile.createNewFile();
			FileWriter writer = new FileWriter(schemaFile);

			JsonGenerator gen = JsonUtil.getGenerator(writer, false);
			gen.writeStartArray();
			for (Schema schema : schemaCol)
				schema.serialize(gen,null,false);

			gen.writeEndArray();
			gen.close();
			writer.close();

		} catch (IOException e) {
			logger.error("Error writing schema to disk at: "+schemaFile.toString()+" error: "+e.getMessage());
		}

		File resFile = new File(storeDir, RESOURCE_TYPES_JSON);
		try {
			resFile.createNewFile();
			FileWriter writer = new FileWriter(resFile);

			JsonGenerator gen = JsonUtil.getGenerator(writer, false);
			gen.writeStartArray();
			for (ResourceType type : resTypeCol)
				type.serialize(gen,null);
			gen.writeEndArray();
			gen.close();
			writer.close();

		} catch (IOException e) {
			logger.error("Error writing resourceType to disk at: "+resFile.toString()+" error: "+e.getMessage());
		}

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
		if (this.mainMap.containsKey(record.getId()))
			throw new DuplicateTxnException("Transactionid is not unique.");

		addResource(record);
	}

}
