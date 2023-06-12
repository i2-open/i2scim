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
package com.independentid.scim.backend.memory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.*;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.*;
import com.independentid.scim.schema.*;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.runtime.Startup;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author pjdhunt
 */


@ApplicationScoped
@Startup // this is required or configproperty injection won't pick up application.properties ??!!
@Priority(10)
@Default
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

    protected final ConcurrentHashMap<String, ScimResource> mainMap;
    protected final HashMap<String, Map<String, ScimResource>> containerMaps;
    //protected final HashMap<Attribute,Map<Object,String>> indexes;
    protected final List<Attribute> indexAttrs = new ArrayList<>();

    @Inject
    SchemaManager schemaManager;

    @Inject
    MemoryIdGenerator generator;

    private boolean ready;

    //@ConfigProperty(name = "scim.root.dir")
    //String rootDir;

    @Inject
    @ConfigProperty(name = "scim.prov.memory.dir", defaultValue = "./scimdata")
    String storeDir;

    @Inject
    @ConfigProperty(name = "scim.prov.memory.file", defaultValue = "scimdata.json")
    String storeFile;

    File dataFile = null;


    @ConfigProperty(name = ConfigMgr.SCIM_QUERY_MAX_RESULTSIZE, defaultValue = ConfigMgr.SCIM_QUERY_MAX_RESULTS_DEFAULT)
    protected int maxResults;

    @ConfigProperty(name = "scim.prov.memory.maxbackups", defaultValue = "24")
    protected int maxBackups;

    @ConfigProperty(name = "scim.prov.memory.backup.mins", defaultValue = "60")
    protected int backupMins;

    @ConfigProperty(name = "scim.prov.memory.indexes", defaultValue = "User:userName,User:emails.value,Group:displayName")
    String[] indexCfg;


    Timer timer = null;

    boolean isModified = false;

    Map<String, ResourceType> types = new HashMap<>();
    Map<String, IndexResourceType> containerIndexes = new HashMap<>();

    /**
     *
     */
    public MemoryProvider() {
        this.mainMap = new ConcurrentHashMap<>();
        this.containerMaps = new HashMap<>();  // Concurrency issues are not expected in the map of maps (just the internal maps)
        //this.indexes = new HashMap<>();
        this.ready = false;
    }

    public Map<String, IndexResourceType> getIndexes() {
        return containerIndexes;
    }

    public Map<String, ScimResource> getData() {
        return mainMap;

    }

    @Override
    //@PostConstruct  We do not want auto start..backendhandler will call this.
    public synchronized void init() {
        JsonFactory jsonFactory = new JsonFactory();

        //this.configMgr = configMgr;
        if (this.ready)
            return;  // run only once!
        //logger.info("rootDir:   "+rootDir);
        //logger.info("storeDir:  "+storeDir);
        //logger.info("storeFile: "+storeFile);
        Collection<ResourceType> smgrTypes = schemaManager.getResourceTypes();
        for (ResourceType atype : smgrTypes)
            types.put(atype.getName(), atype);

        File directory = new File(storeDir);
        if (!directory.exists()) {
            logger.info("Creating memory store directory: " + directory);
            directory.mkdir();
        }

        dataFile = new File(storeDir, storeFile);

        logger.info("======Initializing SCIM Memory Provider======");
        logger.info("\tUsing storage file: " + dataFile.toString());
        logger.info("\tBackups refreshing every " + backupMins + " minutes to a max of " + maxBackups + " files.");

        if (!dataFile.exists()) {
            logger.warn("\tMemory store file not found. Initializing new file: " + dataFile.toString());
            initializeIndexes();
            syncConfig(schemaManager.getSchemas(), schemaManager.getResourceTypes());

        } else {
            ObjectNode node;

            try {
                JsonParser parser = null;
                ObjectMapper mapper = JsonUtil.getMapper();
                if (dataFile.exists()) {
                    InputStream stream = new FileInputStream(dataFile);
                    parser = jsonFactory.createParser(stream);
                }

                if (parser == null || parser.isClosed()) {  // file exists but was empty
                    logger.warn("\tMemory store file empty/invalid. Initializing new file: " + dataFile.toString());
                    initializeIndexes();
                    syncConfig(schemaManager.getSchemas(), schemaManager.getResourceTypes());

                } else {
                    initializeIndexes();
                    // load the existing data
                    try {
                        schemaManager.loadConfigFromProvider();
                    } catch (ScimException e) {
                        logger.error("Error loading schema from MemoryProvider. Using default schema.");
                    }
                    int cnt = 0;
                    logger.debug("\tLoading data file");
                    if (parser.nextToken() == JsonToken.START_ARRAY) {
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            String name = parser.currentName();
                            node = mapper.readTree(parser);
                            try {
                                parseResource(node);
                                cnt++;
                            } catch (ScimException | ParseException e) {
                                logger.error("Unexpected error parsing SCIM JSON database: " + e.getMessage(), e);
                                this.ready = false;
                            }
                        }
                        logger.info("\tLoaded " + cnt + " resources.");
                    }
                }
            } catch (IOException e) {
                logger.error("Unexpected IO error reading SCIM JSON database: " + e.getMessage(), e);
                this.ready = false;
                return;
            }
        }
        isModified = false;

        timer = new Timer("MemBackupTimer");
        if (backupMins == 0)
            logger.warn("\tPeriodic backups to disk *disabled* due to " + PARAM_BACKUP_MINS + " set to 0.");
        else {
            logger.debug("\tScheduling backups every " + backupMins + " mins.");
            TimerTask backupTask = new PersistTask(this);

            TimeUnit milliTime = TimeUnit.MILLISECONDS;
            long delay = milliTime.convert(backupMins, TimeUnit.MINUTES);
            timer.scheduleAtFixedRate(backupTask, delay, delay);
        }
        this.ready = true;
        logger.info("======SCIM Memory Provider initialized ======");

    }

    private void initializeIndexes() {
        for (String index : indexCfg) {
            Attribute attr = schemaManager.findAttribute(index, null);
            if (attr != null)
                indexAttrs.add(attr);

        }
        for (ResourceType type : this.types.values()) {
            Map<String, ScimResource> map = this.containerMaps
                    .computeIfAbsent(type.getTypePath(), k -> new ConcurrentHashMap<>());
            containerIndexes.put(type.getTypePath(),
                    new IndexResourceType(schemaManager, type, map, this.indexAttrs, schemaManager.getUniqueAttributes(type)));
        }
    }

    private boolean checkUniqueAttrConflict(String container, Value val) {
        IndexResourceType index = containerIndexes.get(container);
        if (index == null)
            return false;
        return index.checkUniqueAttr(val);
    }

    /**
     * @param res Resource to check
     * @return true if a conflict exists
     */
    private boolean checkUniqueConflict(ScimResource res) {
        IndexResourceType index = containerIndexes.get(res.getContainer());
        return index.checkUniques(res);
    }

    private void deIndexResource(ScimResource res) {
        IndexResourceType index = containerIndexes.get(res.getContainer());
        index.deIndexResource(res);
    }

    private void indexResource(ScimResource res) {
        IndexResourceType index = containerIndexes.get(res.getContainer());
        if (index == null)
            return;  // this likely only happens for config resources
        index.indexResource(res);
    }

    public void parseResource(JsonNode resNode) throws ScimException, ParseException {
        ScimResource res;

        JsonNode sNode = resNode.get(ScimParams.ATTR_SCHEMAS);
        String schemaId = sNode.get(0).asText();
        String container = null;
        if (schemaId.equals(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE)) {
            container = ScimParams.PATH_SERV_PROV_CFG;
            res = new PersistStateResource(schemaManager, resNode, null, container);
        } else if (schemaId.equals(ScimParams.SCHEMA_SCHEMA_SYNCREC)) {
            container = SystemSchemas.TRANS_CONTAINER;
            res = new TransactionRecord(schemaManager, resNode, container);
        } else {
            JsonNode meta = resNode.get(Meta.META);
            if (meta != null) {
                JsonNode metatype = meta.get(Meta.META_RESOURCE_TYPE);
                if (metatype != null)
                    container = types.get(metatype.asText()).getTypePath();
            }
            res = new ScimResource(schemaManager, resNode, null, container);
        }


        storeResource(res);

    }

    static class PersistTask extends TimerTask {
        MemoryProvider prov;

        PersistTask(MemoryProvider handle) {
            prov = handle;
        }

        public void run() {
            if (prov.isModified) {
                logger.debug("Backing up Memory Provider");
                prov.writeDatabase();
            } else
                logger.debug("Skipping Memory Provider backup due to unmodified state.");
        }
    }

    @Override
    public String getGeneratorClass() {
        return MemoryIdGenerator.class.getName();
    }

    /* (non-Javadoc)
     * @see com.independentid.scim.backend.PersistenceProvider#create(com.independentid.scim.protocol.RequestCtx, com.independentid.scim.resource.ScimResource)
     */
    @Override
    public ScimResponse create(RequestCtx ctx, ScimResource res) throws ScimException, BackendException {
        String container = ctx.getResourceContainer();
        if (container == null || container.equals("/"))
            return new ScimResponse(ScimResponse.ST_NOSUPPORT, "Creating resource at root not supported", null);

        if (res.getId() == null)
            res.setId(generator.getNewIdentifier());
        if (checkUniqueConflict(res))
            return new ScimResponse(ScimResponse.ST_BAD_REQUEST, null, ScimResponse.ERR_TYPE_UNIQUENESS);

        Meta meta = res.getMeta();
        if (meta == null) {
            meta = new Meta();
            res.setMeta(meta);
        }

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

        ResourceType type = schemaManager.getResourceTypeByPath(container);
        if (type != null)
            meta.setResourceType(type.getName());

        if (checkUniqueConflict(res))
            return new ScimResponse(ScimResponse.ST_BAD_REQUEST, "Attribute with uniqueness conflict detected.", ScimResponse.ERR_TYPE_UNIQUENESS);
        storeResource(res);

        ctx.setEncodeExtensions(false);
        ResourceResponse resp = new ResourceResponse(res, ctx);
        resp.setStatus(ScimResponse.ST_CREATED);
        resp.setLocation(res.getMeta().getLocation());
        resp.setETag(res.getMeta().getVersion());

        return resp;
    }

    /**
     * Stores the resource by indexing the resource adding it to the main hash set.
     * @param res The ScimResource to be stored in memory
     */
    private void storeResource(ScimResource res) {
        res.refreshVirtualAttrs();
        this.mainMap.put(res.getId(), res);
        indexResource(res);
        Map<String, ScimResource> cmap = this.containerMaps.computeIfAbsent(res.getContainer(), k -> new ConcurrentHashMap<>());
        cmap.put(res.getId(), res);
        isModified = true;  // set memory as modified compared to disk
    }

    private ScimResponse handleUnexpectedException(Exception e) {
        logger.error("Unhandled exception: " + e.getLocalizedMessage(), e);
        return new ScimResponse(ScimResponse.ST_INTERNAL, e.getLocalizedMessage(), null);
    }

    /* (non-Javadoc)
     * @see com.independentid.scim.backend.PersistenceProvider#get(com.independentid.scim.protocol.RequestCtx)
     */
    @Override
    public ScimResponse get(RequestCtx ctx) throws ScimException {

        if (ctx.getPathId() == null) {

            Filter filter = ctx.getBackendFilter();
            Filter reqFilter = ctx.getFilter();

            ArrayList<ScimResource> results = new ArrayList<>();
            if (filter == null) {

                String path = ctx.getResourceContainer();
                if (path == null || path.equals("/")) {
                    for (ScimResource res : this.mainMap.values()) {
                        if (res.getId().equals(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE))
                            continue; // ConfigState canoot be returned externally.
                        if (results.size() < maxResults) {
                            res.refreshVirtualAttrs();
                            if (reqFilter != null && !Filter.checkMatch(res,ctx))
                                continue;

                            results.add(res); // return raw copy to trigger virtual values
                        } else
                            break;
                    }
                } else {
                    Map<String, ScimResource> map = this.containerMaps.get(path);
                    if (map != null)
                        for (ScimResource res : map.values()) {
                            if (results.size() < maxResults) {
                                res.refreshVirtualAttrs();
                                if (reqFilter != null && !Filter.checkMatch(res,ctx))
                                    continue;
                                results.add(res); // return raw copy to trigger virtual values
                            }
                        }
                }
            } else {
                // this method supports root base searches where path is "/"
                Set<String> candidates = evaluateFilter(filter, ctx.getResourceContainer());

                for (String id : candidates) {
                    ScimResource candidate = this.mainMap.get(id);
                    candidate.refreshVirtualAttrs();
                    if (reqFilter.isMatch(candidate)) {
                        results.add(candidate);// return raw copy to trigger virtual values and protected stored values
                    }
                    if (results.size() > maxResults)
                        break;
                }
            }

            return new ListResponse(results, ctx, maxResults);

        }

        ScimResource res = getResource(ctx);  // this has the filter processed
        if (res == null && ctx.hasNoClientFilter()) {
            return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);
        }

        // Process etag If-None-Match & lastmodified header if present
        if (res != null && res.checkGetPreConditionFail(ctx))
            return new ScimResponse(ScimResponse.ST_NOTMODIFIED, null, null);

        // if this is a get of a specific resource return the object
        if (res != null && ctx.hasNoClientFilter()) {
            if (ctx.getFilter() != null) {
                //Apply the targetFilter
                if (Filter.checkMatch(res, ctx))
                    return new ResourceResponse(res, ctx);
                else
                    return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);
            }
            return new ResourceResponse(res, ctx);
        }

        // if this is a filtered request, must return a list response per RFC7644 Sec 3.4.2
        if (res != null)
            return new ListResponse(res, ctx);  // return the single item
        else
            return new ListResponse(ctx, maxResults);  // return an empty response

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
        if (res == null) return null;

        res.refreshVirtualAttrs();
        if (Filter.checkMatch(res, ctx))
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

        if (origRes.checkModPreConditionFail(ctx))
            return new ScimResponse(new PreconditionFailException(
                    "Predcondition does not match"));

        ScimResource temp;
        try {
            temp = origRes.copy(null);
        } catch (ParseException e) {
            logger.error("Unexpected error creating temporary copy of " + ctx.getPath() + ", " + e.getMessage());
            return new ScimResponse(new InternalException("Unexpected parsing error: " + e.getMessage(), e));
        }
        temp.replaceResAttributes(replaceResource, ctx);

        return processModifyScimResponse(ctx, origRes, temp);

    }

    /* (non-Javadoc)
     * @see com.independentid.scim.backend.PersistenceProvider#patch(com.independentid.scim.protocol.RequestCtx, com.fasterxml.jackson.databind.JsonNode)
     */
    @Override
    public ScimResponse patch(RequestCtx ctx, JsonPatchRequest req) throws ScimException {
        ScimResource origRes = getResource(ctx);

        if (origRes == null)
            return new ScimResponse(ScimResponse.ST_NOTFOUND, null, null);
        if (origRes.checkModPreConditionFail(ctx))
            return new ScimResponse(new PreconditionFailException(
                    "Predcondition does not match"));
        ScimResource temp;
        try {
            temp = origRes.copy(null);
        } catch (ParseException e) {
            logger.error("Unexpected error creating temporary copy of " + ctx.getPath() + ", " + e.getMessage());
            return new ScimResponse(new InternalException("Unexpected parsing error: " + e.getMessage(), e));
        }

        temp.modifyResource(req, ctx);

        return processModifyScimResponse(ctx, origRes, temp);
    }

    private ScimResponse processModifyScimResponse(RequestCtx ctx, ScimResource origRes, ScimResource modRes) {
        deIndexResource(origRes);  // first remove old version.
        if (checkUniqueConflict(modRes)) {
            // As the transaction failed, restore the index on the original resource
            indexResource(origRes);
            return new ScimResponse(ScimResponse.ST_BAD_REQUEST, null, ScimResponse.ERR_TYPE_UNIQUENESS);
        }

        try {
            updateMeta(modRes, ctx);
        } catch (DuplicateTxnException | BackendException e) {
            DuplicateTxnException de;
            if (e instanceof BackendException)
                de = new DuplicateTxnException(e.getMessage());
            else
                de = (DuplicateTxnException) e;
            indexResource(origRes);
            return new ScimResponse(de);
        }
        storeResource(modRes);
        isModified = true;  // set memory as modified compared to disk
        return completeResponse(modRes, ctx);
    }

    private void updateMeta(ScimResource res, RequestCtx ctx) throws DuplicateTxnException, BackendException {
        String path = ctx.getResourceContainer();
        Meta meta = res.getMeta();
        Date modDate = new Date();
        meta.setLastModifiedDate(modDate);

        if (!path.equals(SystemSchemas.TRANS_CONTAINER))
            meta.addRevision(ctx, this, modDate);

        String etag = null;
        try {
            etag = res.calcVersionHash();
        } catch (ScimException ignored) {
        }
        meta.setVersion(etag);
    }

    @SuppressWarnings("DuplicatedCode")
    private ScimResponse completeResponse(ScimResource res, RequestCtx ctx) {

        // Nothing needs to be done as the original object modified directly.

        ctx.setEncodeExtensions(false);
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
        deIndexResource(res);
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
            String newName = parts[0] + "_" + formatter.format(new Date()) + ".json";
            File rollName = new File(storeDir, newName);
            logger.info("\tRolling database file to: " + rollName);
            dataFile.renameTo(rollName);

            File directory = new File(storeDir);

            File[] files = directory.listFiles((f, name) -> name.startsWith(parts[0]));

            if (files != null && files.length > maxBackups) {
                logger.info("\tPurging old data files");
                for (int i = maxBackups; i < files.length; i++) {
                    logger.debug("\t\tDeleteing data file " + files[i]);
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
            logger.info("\tMemory written to: " + this.storeFile);

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
    public PersistStateResource getConfigState() {

        return (PersistStateResource) this.mainMap.get(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
    }

    @Override
    public Collection<Schema> loadSchemas() {
        File schemaFile = new File(storeDir, SCHEMA_JSON);
        if (!schemaFile.exists()) {
            logger.warn("Scheam file not found: " + schemaFile);
            return null;
        }

        Collection<Schema> schemas = new ArrayList<>();

        JsonNode node;
        try {
            node = JsonUtil.getJsonTree(schemaFile);
        } catch (IOException e) {
            logger.error("Unexpected error parsing: " + schemaFile + ", error: " + e.getMessage());
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
                    logger.warn("SchemaException while parsing schema config: " + e.getMessage(), e);
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
        File typeFile = new File(storeDir, RESOURCE_TYPES_JSON);
        if (!typeFile.exists()) {
            logger.warn("Scheam file not found: " + typeFile);
            return null;
        }

        Collection<ResourceType> types = new ArrayList<>();

        JsonNode node;
        try {
            node = JsonUtil.getJsonTree(typeFile);
        } catch (IOException e) {
            logger.error("Unexpected error parsing: " + typeFile + ", error: " + e.getMessage());
            return null;
        }
        if (node.isObject())
            node = node.get(0);

        if (node.isArray()) {
            for (JsonNode typeNode : node) {
                ResourceType type;
                try {
                    type = new ResourceType(typeNode, schemaManager);
                    types.add(type);

                } catch (SchemaException e) {
                    logger.warn("SchemaException while parsing Resource type: " + e.getMessage(), e);
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

		/*
		if (!this.ready()) {
			logger.debug("*** Sync called before init!***");
			init();
		}

		 */
        boolean success = true;
        File schemaFile = new File(storeDir, SCHEMA_JSON);
        try {
            schemaFile.createNewFile();
            FileWriter writer = new FileWriter(schemaFile);

            JsonGenerator gen = JsonUtil.getGenerator(writer, false);
            gen.writeStartArray();
            for (Schema schema : schemaCol)
                schema.serialize(gen, null, false);

            gen.writeEndArray();
            gen.close();
            writer.close();

        } catch (IOException e) {
            logger.error("Error writing schema to disk at: " + schemaFile + " error: " + e.getMessage());
            success = false;
        }

        File resFile = new File(storeDir, RESOURCE_TYPES_JSON);
        try {
            resFile.createNewFile();
            FileWriter writer = new FileWriter(resFile);

            JsonGenerator gen = JsonUtil.getGenerator(writer, false);
            gen.writeStartArray();
            for (ResourceType type : resTypeCol)
                type.serialize(gen, null);
            gen.writeEndArray();
            gen.close();
            writer.close();

        } catch (IOException e) {
            logger.error("Error writing resourceType to disk at: " + resFile + " error: " + e.getMessage());
            success = false;
        }

        if (success) {
            PersistStateResource confState = new PersistStateResource(schemaManager, rcnt, scnt);

            storeResource(confState);

            writeDatabase();
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
            RequestCtx ctx = new RequestCtx(SystemSchemas.TRANS_CONTAINER, transid, null, schemaManager);

            return getResource(ctx);
        } catch (ScimException e) {
            // Would only expect a parsing error resulting from external data.
            logger.error("Unexpected scim error: " + e.getMessage(), e);
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

        storeResource(record);
    }

    public Set<String> evaluateFilter(Filter filter, String container) throws BadFilterException {
        Set<String> res = new HashSet<>();
        if (container == null || container.equals("/")) {
            for (String cont : containerIndexes.keySet())
                res.addAll(evaluateFilter(filter, cont));
        } else {
            IndexResourceType index = this.containerIndexes.get(container);
            res.addAll(index.getPotentialMatches(filter));
        }
        return res;
    }

    public int getCount() {
        return mainMap.size();
    }

}
