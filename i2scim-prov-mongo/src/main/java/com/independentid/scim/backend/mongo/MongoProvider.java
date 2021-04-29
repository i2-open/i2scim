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
import com.independentid.scim.resource.TransactionRecord;
import com.independentid.scim.schema.*;
import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.runtime.Startup;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

//@ApplicationScoped
@Singleton
@Startup // required for proper injection of application.properties values.
@Priority(10)
public class MongoProvider implements IScimProvider {
	private final static Logger logger = LoggerFactory
			.getLogger(MongoProvider.class);

	public final static String PARAM_MONGO_URI = "scim.prov.mongo.uri";
	public final static String DEFAULT_MONGO_URI = "mongodb://localhost:27017";

	public final static String PARAM_MONGO_DBNAME = "scim.prov.mongo.dbname";
	public final static String DEFAULT_MONGO_DBNAME = "SCIM";

	public final static String PARAM_MONGO_INDEXES = "scim.prov.mongo.indexes";
	public final static String DEFAULT_MONGO_INDEXES = "User:userName,User:emails.value,Group:displayName";

	private static MongoProvider singleton = null;
	
	private static com.mongodb.client.MongoClient mclient;

	private boolean ready = false;

	@Inject
	SchemaManager schemaManager;

	@Inject
	MongoMapUtil mapUtil;

	@Inject
	MongoIdGenerator generator;

	@ConfigProperty(name = "scim.prov.mongo.uri", defaultValue="mongodb://localhost:27017")
	String dbUrl;

	//@Value("${scim.mongodb.dbname: SCIM}")
	@ConfigProperty(name = "scim.prov.mongo.dbname", defaultValue="SCIM")
	String scimDbName;

	@ConfigProperty(name = ConfigMgr.SCIM_QUERY_MAX_RESULTSIZE, defaultValue= ConfigMgr.SCIM_QUERY_MAX_RESULTS_DEFAULT)
	protected int maxResults;
	
	//@Value("${scim.mongodb.indexes: User:userName,User:emails.value,Group:displayName}")

	private MongoDatabase scimDb = null;
	
	public MongoProvider() {
	}
	
	public static IScimProvider getProvider() {
		if (singleton == null)
			singleton = new MongoProvider();
		// singleton.init();
		return singleton;
	}

	//Note: We don't want auto start. Normally Backendhandler invokes this.
	public synchronized void init() {

		if (singleton == null)
			singleton = this;
		if (this.ready)
			return; // only run once
		// this.scfg = cfg;
		logger.info("======Initializing SCIM MongoDB Provider======");
		logger.info("\tUsing MongoDB: "+this.dbUrl+", Database: "+this.scimDbName);

		// Connect to the instance define by injected dbUrl value
		if (mclient == null)
			mclient = MongoClients.create(this.dbUrl);

		this.scimDb = mclient.getDatabase(this.scimDbName);

		MongoIterable<String> colIter =  this.scimDb.listCollectionNames();
		if (colIter.first() == null) {
			logger.info("\tPreparing new database instance.");
			try {
				syncConfig(schemaManager.getSchemas(), schemaManager.getResourceTypes());
			} catch (IOException e) {
				logger.error("Error storing schema in Mongo: "+e.getMessage(),e);
			}
		} else {
			try {
				if (getConfigState() != null)
					schemaManager.loadConfigFromProvider();
			} catch (ScimException | IOException | ParseException e) {
				logger.error("Error loading schema from MemoryProvider. Using default schema. "+e.getMessage());
			}
		}

		if (mclient != null) {
			this.ready = true;
			logger.info("====== SCIM Mongo Provider initialized =======");
		}
	}
	
	@Override
	public ScimResponse create(RequestCtx ctx,final ScimResource res)
			throws ScimException {

		String container = ctx.getResourceContainer();
		if (container == null || container.equals("/")) {
			return new ScimResponse(ScimResponse.ST_NOSUPPORT,"Creating resource at root not supported",null);
		}

		if (res.getId() == null)  // in the case of replication, the id is already set
			res.setId(generator.getNewIdentifier());

		Meta meta = res.getMeta();
        if (meta == null) {
            meta = new Meta();
            res.setMeta(meta);
        }
        // Not needed for TransactionRecord type
        Date created = Date.from(Instant.now());
        if (meta.getCreatedDate() == null) // only set the created date if it does not already exist.
            meta.setCreatedDate(created);
        meta.setLastModifiedDate(created); // always set the modify date upon create.
        if (!container.equals(SystemSchemas.TRANS_CONTAINER))
            try {
                meta.addRevision(ctx, this, created);
            } catch (BackendException e) {
                return handleUnexpectedException(e);
            }
        meta.setLocation('/' + container + '/' + res.getId());

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

		MongoCollection<Document> col = sDb.getCollection(container);
		
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
			return handleUnexpectedException(e);
		}
		ctx.setEncodeExtensions(false);
		ResourceResponse resp = new ResourceResponse(res, ctx);
		resp.setStatus(ScimResponse.ST_CREATED);
		resp.setLocation(res.getMeta().getLocation());
		resp.setETag(res.getMeta().getVersion());

		return resp;
	}

	private ScimResponse handleUnexpectedException(Exception e) {
		logger.error("Unhandled exception: "+e.getLocalizedMessage(),e);
		return new ScimResponse(ScimResponse.ST_INTERNAL,e.getLocalizedMessage(),null);
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
		// Locate the correct Mongo Collection
		String type = ctx.getResourceContainer();

		// Update the modification date to now and set Etag version
		Meta meta = replacementResource.getMeta();
		Date modDate = new Date();
		meta.setLastModifiedDate(modDate);
		if (!type.equals(SystemSchemas.TRANS_CONTAINER)) // transaction records are not revisioned
			try {
				meta.addRevision(ctx, this, modDate);
			} catch (BackendException e) {
				return handleUnexpectedException(e);
			}
		// res.setId(id);
		//meta.setLocation(null);
		String etag = replacementResource.calcVersionHash();
		meta.setVersion(etag);

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
	
	public PersistStateResource getConfigState() throws ScimException, IOException, ParseException {

		
		Document query = new Document();
		query.put("id", ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);

		MongoCollection<Document> col =  getDbConnection().getCollection(SystemSchemas.RESTYPE_CONFIG);

		// If this is a brand new databse, return null
		if (col.countDocuments() == 0)
			return null;

		FindIterable<Document> iter = col.find(query);
		Document pdoc = iter.first();  // there should be only one document!


		return mapUtil.mapConfigState(pdoc);
	}

	/**
	 * The Mongo Provider implementation processes filter matching *after* the resource is returned.
	 * @param ctx The SCIM request context (includes HTTP Context). Defines the search filter (if any) along with other
	 *            search parameters like attributes requested. Filter matching is done after the resource is located and
	 *            converted.  Use get(ResourceCtx) to apply filter at the database level.
	 * @return The requested ScimResource or null if not matched
	 * @throws ScimException thrown if a SCIM protocol mapping error occurs (e.g. mapping filter)
	 * @throws BackendException thrown if a database exception occurs.
	 */
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
		
		ScimResource sres = mapUtil.mapScimResource(res,type);
		if (Filter.checkMatch(sres,ctx))
			return sres;
		return null;
	}

	@Override
	public ScimResponse get(RequestCtx ctx) throws ScimException,
			BackendException {
		ctx.setEncodeExtensions(false);
					
		// Querying for a specific resource
		if (ctx.getPathId() != null) {
			// Query for 1 object

			ScimResource res = getResource(ctx);
			if (res == null && ctx.hasNoClientFilter()) {
				return new ScimResponse(ScimResponse.ST_NOTFOUND,null,null);
			}

			//String json = JSON.serialize(res);
			
			// if this is a get of a specific resource return the object
			if (res != null && ctx.hasNoClientFilter()) {
				if (ctx.getFilter() != null) {
					//Apply the targetFilter
					if (Filter.checkMatch(res,ctx))
						return new ResourceResponse(res, ctx);
					else
						return new ScimResponse(ScimResponse.ST_NOTFOUND,null,null);
				}
				return new ResourceResponse(res, ctx);
			}

			// if this is a filtered request, must return a list response per RFC7644 Sec 3.4.2
			if (res != null)
				return new ListResponse(res, ctx);  // return the single item
			else
				return new ListResponse(ctx, maxResults);  // return an empty response
		}
		
		String type = ctx.getResourceContainer();
		if (type == null)
			throw new NotImplementedException("Root searching not implemented");
		
		MongoCollection<Document> col = this.scimDb.getCollection(type);

		Bson query;
		Filter filt = ctx.getFilter();
		
		if (filt == null)
			query = new Document();
		else
			query = MongoFilterMapper.mapFilter(filt, false, false);
		
		if (logger.isDebugEnabled())
			logger.debug("Query: "+query.toString());
		// TODO mapFilter could do imprecise mapping to handle unindexed
		// data since filter is checked after

		FindIterable<Document> fiter = col.find(query);
		MongoCursor<Document> iter = fiter.iterator();
		// If there are no results return empty set.
		if (!iter.hasNext())
			return new ListResponse(ctx, maxResults);

		// Multi-object response.
		ArrayList<ScimResource> vals = new ArrayList<>();

		while (iter.hasNext()) {
			Document res = iter.next();

			try {
				ScimResource sres = mapUtil.mapScimResource(res, type);
			
				// if (Filter.checkMatch(sres, ctx))
				vals.add(sres);
			} catch (SchemaException e) {
				logger.warn("Unhandled exception: "+e.getLocalizedMessage(),e);
				return new ScimResponse(ScimResponse.ST_INTERNAL,e.getLocalizedMessage(),null);
				/*
				throw new BackendException(
						"Unknown parsing exception parsing data from MongoDB."
								+ e.getMessage(), e);
								*/
			}
		}
		return new ListResponse(vals, ctx,maxResults);

	}

	@Override
	public ScimResponse put(RequestCtx ctx, final ScimResource replaceResource)
			throws ScimException, BackendException {
		MongoScimResource origRes = (MongoScimResource) getResource(ctx);
		if (origRes == null )
			return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);
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
		MongoScimResource mres = (MongoScimResource) getResource(ctx);
		mres.modifyResource(req, ctx);
		// Modify resource will update the meta.revision
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

		// First, if a filter specified (by user or by acis) check for a match.
		if (ctx.getFilter() != null)
			try {
				ScimResource original = getResource(ctx);
				if (original == null )
					return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);

			} catch (BackendException e) {
				e.printStackTrace();
			}

		// Given the match and aci filters succeeded, delete the original record.
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

	public String getMongoDbName() {
		return this.scimDbName;
	}

	@Override
	public boolean ready() {

		return this.ready;
	}

	@Override
	public void shutdown() {
		mclient.close();
		mclient = null; // null to allow for reset.
		logger.info("======SCIM MmongoDB Shutdown======");

		this.ready = false;
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
				Schema entry = mapUtil.mapSchema(doc);
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

				rcol.add(mapUtil.mapResourceType(doc));
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

		Collection<Attribute> indexed = mapUtil.getIndexedAttributes();
		for (Attribute attr: indexed) {
			ResourceType type, typematch = null;
			for (ResourceType resourceType : resTypeCol) {
				type = resourceType;

				if (type.getSchema().equals(attr.getSchema())) {
					typematch = type;
					break;
				}
			}

			if (typematch == null) {
				logger.warn("Schema configuration for " + attr.getSchema() + " was not found. Ignoring index: " + attr.getName());
				continue;
			}
			String dbName = typematch.getTypePath();

			MongoCollection<Document> col = sDb.getCollection(dbName);

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
			
		PersistStateResource confState = mapUtil.mapConfigState(rcnt,scnt);

		// Process the schemas
		
		MongoCollection<Document> col = getDbConnection().getCollection(ScimParams.PATH_TYPE_SCHEMAS);
		logger.debug("Clearing existing schemas from DB and re-writing");
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
		
		logger.debug("Clearing and initalizing resource types to DB");
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
		Document query = new Document().append("id", ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
		
		//Set options to create if it does not exist (upsert = true)
		ReplaceOptions options = new ReplaceOptions().upsert(true);
		col =  getDbConnection().getCollection(SystemSchemas.RESTYPE_CONFIG);
		try {
			Document cfgDoc = Document.parse(confState.toJsonString());
			col.replaceOne(query, cfgDoc,options);
		} catch (Exception e) {
			// should not happen
			logger.error("Error serializing config storage state: "+e.getMessage(),e);
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
	public ScimResource getTransactionRecord(String transid) throws BackendException {
		try {


			Document query = new Document();

			MongoCollection<Document> col = this.scimDb.getCollection(SystemSchemas.TRANS_CONTAINER);


			query.put("_id", new ObjectId(transid));
			FindIterable<Document> iter = col.find(query);
			Document doc = iter.first();

			if (doc == null)
				return null;

			//String json = JSON.serialize(res);

			return mapUtil.mapScimResource(doc, SystemSchemas.TRANS_CONTAINER);
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
	public boolean isTransactionPresent(String transid)  {
		Document query = new Document("_id", new ObjectId(transid));
		String type = SystemSchemas.TRANS_CONTAINER;
		MongoCollection<Document> col = this.scimDb.getCollection(type);

		long num = col.countDocuments(query);
		return (num > 0);
	}

	@Override
	public synchronized void storeTransactionRecord(TransactionRecord record) throws DuplicateTxnException {

		if (record.getId() == null)  // in the case of replication, the id is already set
			record.setId((new ObjectId()).toString());
		MongoDatabase sDb = getDbConnection();
		MongoCollection<Document> col = sDb.getCollection(SystemSchemas.TRANS_CONTAINER);

		// Check if the transaction is already stored.
		Document query = new Document();
		query.put("_id", new ObjectId(record.getId()));
		FindIterable<Document> iter = col.find(query);
		Document doc = iter.first();
		if (doc != null)
			throw new DuplicateTxnException("Transaction id "+record.getId()+" already exists.");

		Meta meta = record.getMeta();
		if (meta != null) {// Not needed for TransactionRecord type
			Date created = new Date(System.currentTimeMillis());
			if (meta.getCreatedDate() == null) // only set the created date if it does not already exist.
				meta.setCreatedDate(created);
			meta.setLastModifiedDate(created); // always set the modify date upon create.
			meta.setLocation('/' + SystemSchemas.TRANS_CONTAINER + '/' + record.getId());


			try {
				String etag = record.calcVersionHash();
				meta.setVersion(etag);
			} catch (ScimException ignored) {
			}

		}

		// Map and store the record
		doc = MongoMapUtil.mapResource(record);
		try {
			col.insertOne(doc);
		} catch (IllegalArgumentException e) {
			//Should not happen
			if (logger.isDebugEnabled())
				logger.debug("Bad argument exception: "+e.getLocalizedMessage(),e);
		} catch (MongoWriteException e) {
			logger.warn("Unexpected error writing transaction record. "+e.getMessage(),e);
		}
	}

}
