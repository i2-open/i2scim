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

package com.independentid.scim.backend.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.*;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.*;
import com.independentid.scim.serializer.JsonUtil;
import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

@Singleton
public class MongoProvider implements IScimProvider {
	private final static Logger logger = LoggerFactory
			.getLogger(MongoProvider.class);

	//public static final String MONGO_PROVIDER = "MongoProvider";

	private static MongoProvider singleton = null;
	
	private static com.mongodb.client.MongoClient mclient;

	// private ServletConfig scfg = null;
	// private DB sDb = null;
	private boolean ready = false;

	ConfigMgr cfgMgr;

	final String dbUrl = System.getProperty("scim.mongodb.uri", "mongodb://localhost:27017");

	//@Value("${scim.mongodb.dbname: SCIM}")
	//@ConfigProperty(name = "scim.mongodb.dbname", defaultValue="SCIM")
	final String scimDbName = System.getProperty("scim.mongodb.dbname", "SCIM");
	
	//@Value("${scim.mongodb.indexes: User:userName,User:emails.value,Group:displayName}")
	//@ConfigProperty(name = "scim.mongodb.indexes", defaultValue="User:userName,User:emails.value,Group:displayName")
	final String[] indexes = System.getProperty("scim.mongodb.indexes", "User:userName,User:emails.value,Group:displayName").split(",");

	/**
	 *  When set true, causes the configuration stored to be erased
	 *  to ensure full reset to configuration and test data.
	 */
	//@Value("${scim.mongodb.test: false}")
	//@ConfigProperty(name = "scim.mongodb.test", defaultValue="false")
	boolean resetDb = Boolean.parseBoolean(System.getProperty("scim.mongodb.test", "false"));
	
	private MongoDatabase scimDb = null;
	
	private PersistStateResource stateResource = null;
	
	public MongoProvider() {
	}
	
	public static IScimProvider getProvider()  {
		if (singleton == null)
			singleton = new MongoProvider();
		// singleton.init();
		return singleton;
	}

	@PostConstruct
	public synchronized void init(ConfigMgr cfg) {
		cfgMgr = cfg;
		if (singleton == null)
			singleton = this;
		if (this.ready)
			return; // only run once
		// this.scfg = cfg;
		logger.info("======Initializing SCIM MongoDB Provider======");
		// Connect to the instance define by injected dbUrl value
		if (this.mclient == null)
			this.mclient = MongoClients.create(this.dbUrl);
		
		
		this.scimDb = mclient.getDatabase(this.scimDbName);
		
		if (resetDb) {
			this.scimDb.drop();
			this.resetDb = false;
		}
		
		MongoIterable<String> colIter =  this.scimDb.listCollectionNames();
		if (colIter.first() == null)
			logger.info("Initializing new SCIM database.");
		else {
			try {
				PersistStateResource cres = this.getConfigState();
				if (cres == null) 
					logger.warn("Missing configuration state resource. Recommend reseting database.");
				else {
					logger.info("Restoring configuration data from "+cres.getLastSync());
					logger.debug("  PState Resource Type Count: "+cres.getResTypeCnt());
					logger.debug("  PState Schema Count:        "+cres.getSchemaCnt());
				}
			} catch (ScimException | IOException | ParseException e) {
				logger.error("Unexpected error processing Persisted State Resource: "+e.getLocalizedMessage(),e);
			}
			logger.info("Existing Persisted SCIM Types: ");
			colIter.forEach(name ->
					logger.info("Resource Type: "+name));
		}
		if (this.mclient != null) {
			this.ready = true;
			logger.info("================ SCIM Mongo Provider initialized ================");
		}
	}
	
	@Override
	public ScimResponse create(RequestCtx ctx,final ScimResource res)
			throws ScimException {

		String type = ctx.getResourceContainer();
		
		res.setId((new ObjectId()).toString());
		Meta meta = res.getMeta();
		Date created = new Date(System.currentTimeMillis());
		meta.setCreatedDate(created);
		meta.setLastModifiedDate(created);
		meta.setLocation('/' + type + '/' + res.getId());
		
		String etag = res.calcVersionHash();
		meta.setVersion(etag);
		/*
		 * StringBuffer buf = new StringBuffer(); buf.append('/');
		 * buf.append(ctx.endpoint); buf.append('/'); buf.append(id);
		 * meta.setLocation(buf.toString());
		 */

		Document doc = MongoMapUtil.mapResource(res);

		// resobj.put("_id", new ObjectId(id));
		// resobj.removeField("id"); // don't need duplicate

		/*
		 * Set<String> fields = resobj.keySet(); for (String field : fields) {
		 * System.err.println("Field: " + field); }
		 */
		

		MongoDatabase sDb = getDbConnection();

		MongoCollection<Document> col = sDb.getCollection(type);
		
		try {
			col.insertOne(doc);  
		
			//col.insert(resobj, WriteConcern.ACKNOWLEDGED);

		} catch (IllegalArgumentException e) {
			
			//Should not happen
			if (logger.isDebugEnabled())
				logger.debug("Bad argument exception: "+e.getLocalizedMessage(),e);
			return new ScimResponse(ScimResponse.ST_BAD_REQUEST,e.getLocalizedMessage(),ScimResponse.ERR_TYPE_BADVAL);
		} catch (MongoWriteException e) {
			
			if (e.getCode() == 11000)
				return new ScimResponse(ScimResponse.ST_BAD_REQUEST,e.getLocalizedMessage(),ScimResponse.ERR_TYPE_UNIQUENESS);
			logger.error("Unhandled exception: "+e.getLocalizedMessage(),e);
			return new ScimResponse(ScimResponse.ST_INTERNAL,e.getLocalizedMessage(),null);
		}
		ctx.setEncodeExtensions(false);
		ResourceResponse resp = new ResourceResponse(res, ctx);
		resp.setStatus(ScimResponse.ST_CREATED);
		resp.setLocation(res.getMeta().getLocation());
		resp.setETag(res.getMeta().getVersion());

		return resp;
	}

	/**
	 * Internal PUT operation takes the ScimResource in its final state and
	 * replaces the existing document in the database.
	 * 
	 * @param replacementResource The new Mongo Resource document to be used to replace the existing Document
	 * @param ctx The request CTX which is used to locate the appropriate container (endpoint)
	 * @return ScimResponse The resulting resource response after put logic applied.
	 * @throws ScimException Thrown if Mongo returns an illegal arguement exception
	 */
	protected ScimResponse putResource(MongoScimResource replacementResource, RequestCtx ctx)
			throws ScimException {
	
		//Document orig = replacementResource.getOriginalDBObject();
		ctx.setEncodeExtensions(true);

		// Update the modification date to now and set Etag version
		Meta meta = replacementResource.getMeta();
		Date modDate = new Date();
		meta.setLastModifiedDate(modDate);
		// res.setId(id);
		//meta.setLocation(null);
		String etag = replacementResource.calcVersionHash();
		meta.setVersion(etag);

		// Locate the correct Mongo Collection
		String type = ctx.getResourceContainer();

		MongoDatabase sDb = getDbConnection();

		MongoCollection<Document> col = sDb.getCollection(type);

		// Convert the MongoScimResource back to a DB Object

		Document replaceDoc = replacementResource.toMongoDocument(ctx);
		
		try {		
		
			col.replaceOne(Filters.eq("_id",replaceDoc.get("_id")), replaceDoc); 
		
		
		} catch (IllegalArgumentException e) {
			return new ScimResponse(new InternalException("Mongo PUT exception: "+e.getLocalizedMessage(), e));
		}
		
		// meta.setVersion(etag);
		ctx.setEncodeExtensions(false);
		ResourceResponse resp = new ResourceResponse(replacementResource, ctx);
		resp.setStatus(ScimResponse.ST_OK);
		resp.setLocation(replacementResource.getMeta().getLocation());
		resp.setETag(replacementResource.getMeta().getVersion());

		return resp;
	}
	
	public PersistStateResource getConfigState() throws ScimException, IOException, SchemaException, ParseException {
		if (stateResource == null) {
		
			Document query = new Document();
			query.put("id",PersistStateResource.CONFIG_ID);
			
			MongoCollection<Document> col =  getDbConnection().getCollection(PersistStateResource.RESTYPE_CONFIG);
			
			// If this is a brand new databse, return null
			if (col.countDocuments() == 0)
				return null;
			
			FindIterable<Document> iter = col.find(query);
			Document pdoc = iter.first();  // there should be only one document!

			if(pdoc == null)
				return null;
			String jsonstr = pdoc.toJson();
			JsonNode jdoc = JsonUtil.getJsonTree(jsonstr);
			
			stateResource = new PersistStateResource(cfgMgr,jdoc,null, PersistStateResource.RESTYPE_CONFIG);
		}
		
		return stateResource;
	}

	@Override
	public ScimResource getResource(RequestCtx ctx) throws ScimException,
			BackendException {
		//ctx.setEncodeExtensions(true);
		Document query;
				
		String type = ctx.getResourceContainer();
		if (type == null || type.equals("/"))
			throw new NotImplementedException("Root searching not implemented");

		if (ctx.getPathId() == null && !ctx.getResourceContainer().equalsIgnoreCase(ScimParams.PATH_SERV_PROV_CFG))
			return null;
			
		query = new Document();
		
		MongoCollection<Document> col = this.scimDb.getCollection(type);

		if (ConfigResponse.isConfigEndpoint(ctx.getResourceContainer())) {
			query.put("id",ctx.getPathId());
		} else
			query.put("_id", new ObjectId(ctx.getPathId()));
		FindIterable<Document> iter = col.find(query);
		Document res = iter.first();

		if (res == null) {
			return null;
		}

		//String json = JSON.serialize(res);
		
		try {
			return new MongoScimResource(cfgMgr, res, type);
			
		} catch (SchemaException | ParseException e) {
			throw new BackendException(
					"Unknown parsing exception parsing data from MongoDB."
							+ e.getMessage(), e);
		}

	}

	@Override
	public ScimResponse get(RequestCtx ctx) throws ScimException,
			BackendException {
		ctx.setEncodeExtensions(false);
					
		// Querying for a specific resource
		if (ctx.getPathId() != null) {
			// Query for 1 object

			ScimResource res = getResource(ctx);
			if (res == null) {
				return new ScimResponse(ScimResponse.ST_NOTFOUND,null,null);
			}

			//String json = JSON.serialize(res);
			
			// if this is a get of a specific resource return the object
			if (ctx.getFilter() == null)
				return new ResourceResponse(res,ctx);
			
			// if this is a filtered request, must return a list response per RFC7644 Sec 3.4.2
			if (Filter.checkMatch(res, ctx)) 
				return new ListResponse(res, ctx);  // return the single item
			else
				return new ListResponse(ctx);  // return an empty response			
		}
		
		String type = ctx.getResourceContainer();
		if (type == null)
			throw new NotImplementedException("Root searching not implemented");
		
		MongoCollection<Document> col = this.scimDb.getCollection(type);

		if (logger.isDebugEnabled())
			logger.debug(type + " record count: " + col.countDocuments());

		Document query;
		Filter filt = ctx.getFilter();
		
		if (filt == null)
			query = new Document();
		else
			query = FilterMapper.mapFilter(ctx.getFilter(), false);
		
		if (logger.isDebugEnabled())
			logger.debug("Query: "+query.toJson());
		// TODO mapFilter could do imprecise mapping to handle unindexed
		// data since filter is checked after
		
		FindIterable<Document> fiter = col.find(query);
		MongoCursor<Document> iter = fiter.iterator();
		// If there are no results return empty set.
		if (!iter.hasNext())
			return new ListResponse(ctx);

		// Multi-object response.
		ArrayList<ScimResource> vals = new ArrayList<>();

		while (iter.hasNext()) {
			Document res = iter.next();

			try {
				ScimResource sres = new MongoScimResource(cfgMgr, res, type);
			
				// if (Filter.checkMatch(sres, ctx))
				vals.add(sres);
			} catch (SchemaException | ParseException e) {
				logger.warn("Unhandled exception: "+e.getLocalizedMessage(),e);
				return new ScimResponse(ScimResponse.ST_INTERNAL,e.getLocalizedMessage(),null);
				/*
				throw new BackendException(
						"Unknown parsing exception parsing data from MongoDB."
								+ e.getMessage(), e);
								*/
			}
		}
		return new ListResponse(vals, ctx);

	}

	@Override
	public ScimResponse replace(RequestCtx ctx, final ScimResource replaceResource)
			throws ScimException, BackendException {
		MongoScimResource origRes = (MongoScimResource) this.getResource(ctx);
		if (!origRes.checkPreCondition(ctx))
			return new ScimResponse(new PreconditionFailException(
					"ETag predcondition does not match"));
		origRes.replaceResAttributes(replaceResource, ctx);  
		return this.putResource(origRes, ctx);
	}

	@Override
	public ScimResponse patch(RequestCtx ctx, final JsonPatchRequest req)
			throws ScimException, BackendException {
		ctx.setEncodeExtensions(true);
		MongoScimResource mres = (MongoScimResource) this.getResource(ctx);
		mres.modifyResource(req, ctx);
		return this.putResource(mres, ctx);
	}

	@Override
	public ScimResponse bulkRequest(RequestCtx ctx, JsonNode node) {
		return new ScimResponse(ScimResponse.ST_NOSUPPORT, null, null);
	}

	@Override
	public ScimResponse delete(RequestCtx ctx) throws ScimException {
		//ctx.setEncodeExtensions(true);
		String id = ctx.getPathId();
		String type = ctx.getResourceContainer();
		if (id == null)
			throw new InvalidValueException(
					"Missing resource identifier exception");

		if (type == null)
			throw new NotImplementedException("Root searching not implemented");

		MongoCollection<Document> col = this.scimDb.getCollection(type);
		

		Document query = new Document();

		query.put("_id", new ObjectId(id));
		Document res = col.findOneAndDelete(query);
				//col.findOne(query);
		if (res == null) {
			return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);
		}

		//col.remove(res, WriteConcern.ACKNOWLEDGED);

		// return success
		return new ScimResponse(ScimResponse.ST_NOCONTENT, null, null);
	}

	public void eraseDatabase() {
		System.err.println("  Initiating reset of database collection: "
				+ getMongoDbName());
		logger.warn("Database provider reset requested. Dropping");
		
		MongoDatabase db = mclient.getDatabase(this.getMongoDbName());
		db.drop();
		
		System.err.println("  **Reset was requested by:");
		Thread.dumpStack();
	}

	public String getMongoDbName() {
		return this.scimDbName;
	}

	@Override
	public boolean ready() {

		return this.ready;
	}

	@Override
	public void shutdown() {
		logger.debug("======SCIM MmongoDB Shutdown======");
		
	}

	public MongoDatabase getDbConnection() {
		
	    return this.scimDb;
		
		//return mclient.getDB(this.scimDb);
	}

	protected String genEtag(String jsonVal) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");

			md.update(jsonVal.getBytes());
			byte[] hashBytes = md.digest();
			// convert the byte to hex format method 1
			StringBuilder sb = new StringBuilder();
			for (byte hashByte : hashBytes) {
				sb.append(Integer.toString((hashByte & 0xff) + 0x100, 16)
						.substring(1));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e1) {
			logger.warn("Unexpected error generating ETag: "+e1.getLocalizedMessage());
		}
		return null;

	}

	@Override
	public Collection<Schema> loadSchemas() throws ScimException {

		PersistStateResource configState;
		try {
			configState = getConfigState();
			if (configState == null)
				return null;  // This is a brand new database
			
			MongoCollection<Document> col =  getDbConnection().getCollection(ScimParams.PATH_TYPE_SCHEMAS);
			if (col.countDocuments() == 0) return null;
			
			Collection<Schema> scol = new ArrayList<>();
			FindIterable<Document> iter = col.find();
			
			for (Document doc : iter) {
				JsonNode jdoc = JsonUtil.getJsonTree(doc.toJson());
				Schema entry = new Schema(jdoc);
				//  map.put(entry.getName(), entry);  // Map by both id and name
				scol.add(entry);
			}
			
			return scol;
			
		
		} catch (IOException | SchemaException | ParseException e) {
			logger.error("Exception loading Schema from Mongo: "+e.getLocalizedMessage(),e);
		}
			
		return null;
	}

	@Override
	public Collection<ResourceType> loadResourceTypes() throws ScimException {
		PersistStateResource config;
		try {
			config = getConfigState();
			if (config == null)
				return null;  // This is a brand new database
			
			MongoCollection<Document> col =  getDbConnection().getCollection(ScimParams.PATH_TYPE_RESOURCETYPE);
			if (col.countDocuments() == 0) return null;
			
			Collection<ResourceType> rcol = new ArrayList<>();
			FindIterable<Document> iter = col.find();
			
			for (Document doc : iter) {
				JsonNode jdoc = JsonUtil.getJsonTree(doc.toJson());
				ResourceType entry = new ResourceType(jdoc);
				rcol.add(entry);  
			}
			
			return rcol;
			
		
		} catch (IOException | SchemaException | ParseException e) {
			logger.error("Exception loading Schema from Mongo: "+e.getLocalizedMessage(),e);
		}
			
		return null;
	}
	
	/**
	 * When the database is first set up, this runs through the ResourceTypes and Schemas to initialize Mongo
	 * with indexed fields and uniqueness settings.
	 * @param resTypeCol The collection of {@link ResourceType} types to be defined in the database
	 */
	private void initDbSchema(Collection<ResourceType> resTypeCol) {
		MongoDatabase sDb = getDbConnection();

		//Initialize "id" index is not required as Mongo will auto index "_id" (which is mapped from id)


		for (String index : this.indexes) {
			String schema = index.substring(0, index.indexOf(':'));
			String attrName = index.substring(index.indexOf(':') + 1);

			ResourceType type, typematch = null;
			for (ResourceType resourceType : resTypeCol) {
				type = resourceType;

				if (type.getId().equals(schema) || type.getName().equals(schema)) {
					typematch = type;
					break;
				}
			}

			if (typematch == null) {
				logger.warn("Schema configuration for " + schema + " was not found. Ignoring index: " + index);
				continue;
			}
			String dbName = typematch.getTypePath();


			MongoCollection<Document> col = sDb.getCollection(dbName);
			Attribute attr = cfgMgr.findAttribute(index, null);
			if (attr == null) {
				logger.warn("Attribute configuration for " + attrName + " was not found. Ignoring index: " + index);
				continue;
			}

			if (logger.isDebugEnabled())
				logger.debug("Creating index for " + attr.getRelativePath() + ", unique: " + attr.getUniqueness());
			// According to MongoDB driver, if index already exists this should not re-create it.
			if (attr.getUniqueness().contentEquals(Attribute.UNIQUE_none))
				col.createIndex(Indexes.ascending(attr.getRelativePath()));
			else {
				IndexOptions opt = new IndexOptions().unique(true);
				col.createIndex(Indexes.ascending(attr.getRelativePath()), opt);
			}
			if (logger.isDebugEnabled()) {
				for (Document doc : col.listIndexes()) {
					logger.debug(doc.toJson());
				}
			}

		}
	}

	@Override
	public void syncConfig(Collection<Schema> schemaCol, Collection<ResourceType> resTypeCol) throws IOException {
		
		initDbSchema(resTypeCol);
		
		Iterator<Schema> siter = schemaCol.iterator();

		Iterator<ResourceType> riter = resTypeCol.iterator();
		
		int scnt = schemaCol.size();
		int rcnt = resTypeCol.size();
			
		PersistStateResource confState = new PersistStateResource(cfgMgr,rcnt,scnt);
		
		// Process the schemas
		
		MongoCollection<Document> col = getDbConnection().getCollection(ScimParams.PATH_TYPE_SCHEMAS);
		logger.debug("Clearing existing schemas from DB");
		col.drop();
		while (siter.hasNext()) {
			Schema entry = siter.next();
			String entryStr;
			try {
				entryStr = entry.toJsonString();
				Document replDoc = Document.parse(entryStr);
				logger.debug("Persisting schema: "+entry.getId());
				col.insertOne(replDoc);

			} catch (NullPointerException ne) {
				logger.error("Null Pointer Error serializing Schema entry: "+entry.getId(),ne);
		
			}
			
			
		}
		
		// Now process the resource types
		
		col = getDbConnection().getCollection(ScimParams.PATH_TYPE_RESOURCETYPE);
		
		logger.debug("Clearing existing resource types from DB");
		col.drop();
		while (riter.hasNext()) {
			ResourceType entry = riter.next();
			try {
				String entryStr = entry.toJsonString();
				Document replDoc = Document.parse(entryStr);
				if (logger.isDebugEnabled())
					logger.debug("Persisting schema: "+entry.getId());
				col.insertOne(replDoc);
			} catch (NullPointerException ne) {
				logger.error("Null Pointer Error serializing ResourceType: "+entry.getId(),ne);
			}
		}
		
		// Write out the current sync information.
		Document query = new Document().append("id", PersistStateResource.CONFIG_ID);
		
		//Set options to create if it does not exist (upsert = true)
		ReplaceOptions options = new ReplaceOptions().upsert(true);
		col =  getDbConnection().getCollection(PersistStateResource.RESTYPE_CONFIG);
		try {
			Document cfgDoc = Document.parse(confState.toJsonString());
			col.replaceOne(query, cfgDoc,options);
		} catch (Exception e) {
			// should not happen
			logger.error("Error serializing config storage state: "+e.getMessage(),e);
		}
		

	}
		
	
}
