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

package com.independentid.scim.schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.serializer.JsonUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

/**
 * SchemaManager loads schema from configuration files or the back end when available. It us users by ScimResource and
 * other classes to provide resource and attribute type definitions.  Previously this was contained within ConfigMgr and
 * is migrated to solve bootstrap issues.
 */
@Startup
@Singleton
//@ApplicationScoped
@Named("SchemaMgr")
public class SchemaManager {
    private final static Logger logger = LoggerFactory.getLogger(SchemaManager.class);

    @Inject
    @Resource(name="BackendHandler")
    BackendHandler backendHandler;

    public final static String SCIM_CORE_SCHEMAID = "urn:ietf:params:scim:schemas:core:2.0:Common";

    public final static List<String> SCIM_CORE_ATTRS = Arrays.asList(
            "id", "externalid", "schemas", "meta");

    @ConfigProperty(name = "scim.schema.path", defaultValue = "classpath:/schema/scimSchema.json")
    String schemaPath;

    @ConfigProperty(name = "scim.resourcetype.path", defaultValue = "classpath:/schema/resourceTypes.json")
    String resourceTypePath;

    @ConfigProperty(name = "scim.coreSchema.path", defaultValue = "classpath:/schema/scimCommonSchema.json")
    String coreSchemaPath;

    @ConfigProperty(name = "scim.persist.schema", defaultValue = "true")
    boolean persistSchema;

    private final static LinkedHashMap<String, Schema> schIdMap = new LinkedHashMap<>();
    private final static LinkedHashMap<String, Schema> schNameMap = new LinkedHashMap<>();

    //private TreeMap<String,Attribute> vals = new TreeMap<String,Attribute>();

    private final static LinkedHashMap<String, ResourceType> rTypes = new LinkedHashMap<>();
    private final static HashMap<String, ResourceType> rTypePaths = new HashMap<>();

    //private ServletConfig scfg = null;

    private static boolean initialized = false;

    public SchemaManager() {

    }

    /**
     * During initial startup, the default Schema and ResourceTypes are loaded into the server. Once the rest of the
     * server is started, ConfigMgr may check the backend provider to see if there is persisted schema available.
     * @throws ScimException due to invalid data in schema config
     * @throws IOException due to problems accessing files
     */
    @PostConstruct
    public void init() throws ScimException, IOException {
        logger.info("SchemaManager initializing");
        if (initialized)
            return;

        loadDefaultSchema();
        loadDefaultResourceTypes();
        loadCoreSchema();
        initialized = true;
    }

    @PreDestroy
    public synchronized void shutdown() {
        // clean up so that GC works faster
        logger.debug("SchemaManager shutdown.");
        resetSchema();
    }

    /**
     * This method loads the SCIM Core Schema definitions. This schema comprises attributes that are in common across
     * all SCIM Resources  This method should be called *after* other schema is loaded.
     * @throws ScimException when a parsing error (e.g. JSON parsing) occurs locating default schema.
     * @throws IOException   when attempting to read files (e.g. not found).
     */
    protected void loadCoreSchema() throws ScimException, IOException {
        String filePath = this.coreSchemaPath;

        if (filePath == null)
            throw new ScimException("SCIM default schema file path is null.");

        try {
            File sfile = ConfigMgr.findClassLoaderResource(filePath);

            if (sfile == null)
                throw new IOException("Schema file must not be null");

            if (!sfile.exists())
                throw new IOException("Schema file does not exist: " + sfile.getPath());

            logger.debug("\t..Parsing Core Attribute Schema");

            JsonNode node = JsonUtil.getJsonTree(sfile);

            if (!node.isObject()) {
                logger.error("Was expecting a JSON Object for SCIM Core Attributes. Found: " + node.getNodeType().toString());
                return;
            }
            Schema schema;
            try {
                schema = new Schema(this,node);
                addSchema(schema);
                if (logger.isDebugEnabled())
                    logger.debug("\t....Attribute Schema loaded>" + schema.getId());
                //System.out.println("Debug: Schema loaded>"+schema.getId());
            } catch (SchemaException e) {
                logger.warn("SchemaException while parsing Schema Core Attribute config: " + filePath + ", " + e.getLocalizedMessage(), e);
            }

        } catch (JsonProcessingException e) {
            throw new ScimException(
                    "JSON parsing exception processing schema core configuration: " + filePath,
                    e);
        }
    }

    /*
     * Resets the default schema and resource types loaded by SchemaManager so that persisted schema can be loaded from
     * backend handler.
     */
    public void resetSchema() {
        initialized = false;
        schIdMap.clear();
        schNameMap.clear();

        rTypes.clear();
        rTypePaths.clear();
    }

    /**
     * Loads the default schema from the config properties into ConfigMgr "schIdMap" map.
     * @throws ScimException thrown configured filepath is undefined or invalid, or due to invalid JSON
     * @throws IOException   thrown due to file processing errors (missing file)
     */
    protected void loadDefaultSchema() throws ScimException, IOException {
        if (schemaPath == null)
            throw new ScimException("SCIM default schema file path is null.");

        try {
            File sfile = ConfigMgr.findClassLoaderResource(schemaPath);

            parseSchemaConfig(sfile);
        } catch (JsonProcessingException e) {
            throw new ScimException(
                    "JSON parsing exception processing schema configuration.",
                    e);
        }
    }

    protected void parseSchemaConfig(File schemaFile) throws IOException {
        if (schemaFile == null)
            throw new IOException("Schema file must not be null");

        if (!schemaFile.exists())
            throw new IOException("Schema file does not exist: " + schemaFile.getPath());

        logger.debug("\t..Parsing Schema Config.");
        JsonNode node = JsonUtil.getJsonTree(schemaFile);

        // In case this is a reload, reset the current Schemas
        schIdMap.clear();

        Iterator<JsonNode> iter;
        if (node.isObject()) {
            //The outer element is an object {} rather than []
            node = node.get(0);

        }
        if (node.isArray()) {
            iter = node.iterator();
            while (iter.hasNext()) {
                JsonNode schemaNode = iter.next();
                Schema schema;
                try {
                    schema = new Schema(this,schemaNode);
                    addSchema(schema);

                } catch (SchemaException e) {
                    logger.warn("SchemaException while parsing schema config.", e);
                }
            }
            return;

        } else {
            logger.warn("Dectected unexpected node while parsing Schema: " + node.getNodeType().toString());
            //System.out.println("Detected node endpoint "+node.getNodeType().toString());
        }
        // TODO Handle invalid node on schema parsing
        logger.error("Unexpected JsonNode while parsing schema: " + node.toString());
    }



    public void addSchema(Schema schemaDef) {
        String id = schemaDef.getId();
        String name = schemaDef.getName();
        logger.debug("  Loading Schema: " + id + ", Name: " + name);

        //Store schIdMap in the hash by Id and Name!
        schIdMap.put(schemaDef.getId(), schemaDef);

        schNameMap.put(schemaDef.getName(), schemaDef);
    }

    /**
     * Serializes a result using the provided JsonGenerator. Does not close or flush the generator.
     * @param ctx The SCIM RequestCtx - which specifies all the request parameters
     * @param gen The JsonGenerator with which to generate the result.
     * @throws IOException thrown due to error serializing schema identified in RequestCtx
     */
    public void serializeSchema(RequestCtx ctx, JsonGenerator gen) throws IOException {

        String id = ctx.getPathId();
        if (id != null && id.length() > 0) {
            Schema schema = getSchemaById(id);
            if (schema == null) {
                return;
            }
            schema.serialize(gen, ctx, false);

        } else {
            gen.writeStartArray();
            for (Schema schema : schIdMap.values()) {
                schema.serialize(gen, ctx, false);
            }
            gen.writeEndArray();

        }
    }

    /**
     * @param ctx    The SCIM RequestCtx - which specifies all the request parameters
     * @param writer If provided, output will be sent to the writer specified. Otherwise a String is returned.
     * @throws IOException thrown when error occurs serializing Schema identified in RequestCtx, or a writer error.
     */
    public String serializeSchema(RequestCtx ctx, Writer writer) throws IOException {

        Writer swriter;
        swriter = (writer != null)?writer : new StringWriter();

        JsonGenerator gen = JsonUtil.getGenerator(swriter, false);

        serializeSchema(ctx, gen);

        gen.close();

        //Only return the string value if no writer was specified.
        if (writer == null)
            return swriter.toString();
        else
            return null;
    }


    /**
     * Loads the default resource types defined by config properties into ConfigMgr "resourceTypes" map.
     * @throws ScimException thrown configured filepath is undefined or invalid, or due to invalid JSON
     * @throws IOException   thrown due to file processing errors (missing file)
     */
    protected void loadDefaultResourceTypes() throws ScimException, IOException {

        if (resourceTypePath == null)
            throw new ScimException("SCIM default resource type configuraiton file path is null.");

        try {
            File sfile = ConfigMgr.findClassLoaderResource(resourceTypePath);

            parseResourceTypes(sfile);
        } catch (JsonProcessingException e) {
            throw new ScimException(
                    "JSON parsing exception processing Resource Type configuration.",
                    e);
        } catch (SchemaException e) {
            throw new ScimException(
                    "Schema exception parsing resource endpoint configuration file.",
                    e);
        }
    }

    public void parseResourceTypes(File typeFile) throws SchemaException, IOException {

        if (typeFile == null)
            throw new IOException("Resource endpoint file must not be null");

        if (!typeFile.exists())
            throw new IOException("Resource endpoint file does not exist: " + typeFile.getPath());

        JsonNode node = JsonUtil.getJsonTree(typeFile);

        Iterator<JsonNode> iter;
        if (node.isObject()) {
            //The outer element is an object {} rather than []
            node = node.get(0);

        }
        if (node.isArray()) {
            iter = node.iterator();
            while (iter.hasNext()) {
                JsonNode typeNode = iter.next();
                ResourceType type;

                type = new ResourceType(typeNode, this);
                addResourceType(type);
            }
            return;
        } else {
            logger.error("While parsing resource types, detected node endpoint " + node.getNodeType().toString());
        }
        // TODO Handle invalid node on Resource Type parsing
        logger.error("Unexpected JsonNode while parsing ResourceTypes: " + node.toString());

    }

    public void addResourceType(ResourceType type) throws SchemaException {
        logger.debug("  Resource endpoint loading: " + type.getId() + ", URI: " + type.getEndpoint());
        rTypes.put(type.getId(), type);
        String path = type.getTypePath();
        if (path == null)
            throw new SchemaException("The resource endpoint path for " + type.getName() + " was null or not valid.");
        if (path.startsWith("/"))
            path = path.substring(1);
        rTypePaths.put(path.toLowerCase(), type);

    }

    /**
     * @param ctx The SCIM RequestCtx - which specifies all the request parameters
     * @param gen The JsonGenerator used to serialize the requested values. Caller must flush and close gen.
     * @throws IOException thrown when serializing ResourceType into JSON
     */
    public void serializeResourceTypes(RequestCtx ctx, JsonGenerator gen) throws IOException {

        String id = ctx.getPathId();
        if (id != null && id.length() > 0) {
            ResourceType type = getResourceTypeById(id);
            //		.getConfig(ctx.sctx).getSchema(ctx.id);
            if (type == null) {
                return;
            }
            type.serialize(gen, ctx, false);

        } else {
            gen.writeStartArray();
            for (ResourceType type : rTypes.values()) {
                type.serialize(gen, ctx, false);
            }
            gen.writeEndArray();

        }
    }

    /**
     * @param ctx    The SCIM RequestCtx - which specifies all the request parameters
     * @param writer If provided, output will be sent to the writer specified. Otherwise a String is returned.
     * @return A String containing the serialized JSON value. Returns <code>null</code> if a writer is specified.
     * @throws IOException thrown when attempting to write serialized ResourceType to a writer
     */
    public String serializeResourceTypes(RequestCtx ctx, Writer writer) throws IOException {

        Writer swriter = (writer != null)? writer : new StringWriter();

        JsonGenerator gen = JsonUtil.getGenerator(swriter, false);

        serializeResourceTypes(ctx, gen);
        gen.close();

        //Only return the string value if no writer was specified.
        if (writer == null)
            return swriter.toString();
        else
            return null;
    }

    /**
     * Thie method looks up an <code>Attribute</code> type based on one of 3 methods: 1. If schemaId is provided, the
     * attribute is matched from there. 2. If no attribute found, the RequestCtx is checked for a ResourceType where the
     * container schema and extensions are used 3. If still no match, all schemas are checked for a match. Note: this
     * method cannot be used to locate sub=attributes directly.
     * @param schemaId    Optional Schema URN or null
     * @param name        The base attribute name (required)
     * @param subAttrName The name of a sub-attribute if desired or null
     * @param ctx         Optional RequestCtx object (used to detect resourceType)
     * @return The matching Attribute or null if not matched
     */
    public Attribute findAttribute(String schemaId, String name, String subAttrName, RequestCtx ctx) {
        Schema schema;

        if (schemaId == null && SCIM_CORE_ATTRS.contains(name.toLowerCase())) {
            schemaId = "Common";
        }

        Attribute attr = null;
        // 1st, try to get the schema from the parameter schemaId
        if (schemaId != null) {
            // Try by full Schema ID
            schema = schIdMap.get(schemaId);
            if (schema != null)
                attr = schema.getAttribute(name);
                // Let's try name
            else {
                schema = schNameMap.get(schemaId);
                if (schema != null)
                    attr = schema.getAttribute(name);
            }
            if (attr == null) return null;

            return (subAttrName == null) ? attr : attr.getSubAttribute(subAttrName);
        }


        // If not found, try to get it from the container via RequestCtx...
        // Note that the attribute could come from the core schema or extension schema

        if (ctx != null && ctx.getResourceContainer() != null) {
            //check the resource type endpoint for core schema and extensions
            ResourceType type = getResourceTypeByPath(ctx.getResourceContainer());
            if (type != null) {
                Schema core = getSchemaById(type.getSchema());
                attr = core.getAttribute(name);
                if (attr != null) return attr;

                String[] exts = type.getSchemaExtension();
                for (String s : exts) {
                    Schema ext = getSchemaById(s);
                    if (ext != null) {
                        attr = ext.getAttribute(name);
                        if (attr != null) {
                            if (subAttrName != null) {
                                if (attr.getSubAttribute(subAttrName) != null)
                                    return attr.getSubAttribute(subAttrName);
                            } else
                                return attr;
                        }
                    }
                }
            }
        }

        // finally, search all the schIdMap for the attribute
        for (Schema sch : schIdMap.values()) {
            attr = sch.getAttribute(name);
            if (attr != null) {
                if (subAttrName != null) {
                    if (attr.getSubAttribute(subAttrName) != null)
                        return attr.getSubAttribute(subAttrName);
                } else
                    return attr;
            }
        }
        return null;

    }

    /**
     * This method takes a full attribute path and parses it to find the
     * <code>Attribute</code> type associated. If it is a sub-attribute,
     * the schema is walked until the sub-attribute is located
     * @param path The path (incl URN) or base path (e.g. attr.subattr) for the attribute
     * @param ctx  The request context (used to detect resource type to default schema urn)
     * @return The <code>Attribute<code> type for the attribute path requested.
     */
    public Attribute findAttribute(String path, RequestCtx ctx) {
        int aindex = path.lastIndexOf(':');
        String schema = null;
        String attr;
        String sattr = null;
        Attribute aType;

        if (aindex > -1) {
            schema = path.substring(0, aindex);
            attr = path.substring(aindex + 1);

        } else
            attr = path;
        int sind = attr.indexOf('.');
        if (sind > -1) {
            sattr = attr.substring(sind + 1);
            attr = attr.substring(0, sind);
        }

        // Check to see if the attribute is a core attribute (id, meta, externalID, schemas).
        if (schema == null &&
                SCIM_CORE_ATTRS.contains(attr.toLowerCase()))
            schema = ScimParams.SCHEMA_SCHEMA_Common;

        // If schema not specified, use the CTX, if provided to determine schema
        if (schema == null && ctx != null) {
            String container = ctx.getResourceContainer();
            // If CTX had a folder, use it to look up the resource type and schema
            if (container != null) {
                ResourceType rt = this.getResourceTypeByPath(container);
                if (rt != null) {
                    schema = rt.getSchema();
                    aType = this.findAttribute(schema, attr, sattr, ctx);
                    if (aType != null)
                        return aType;
                    // Try the extension schemas
                    String[] exts = rt.getSchemaExtension();
                    for (String ext : exts) {
                        aType = this.findAttribute(ext, attr, sattr, ctx);
                        if (aType != null)
                            return aType;
                    }
                }
            }
        }
        // last gasp, try with defaults
        aType = this.findAttribute(schema, attr, sattr, ctx);

        return aType;
    }

    public Collection<ResourceType> getResourceTypes() {
        return rTypes.values();
    }

    public ResourceType getResourceTypeById(String id) {
        return rTypes.get(id);
    }

    public int getResourceTypeCnt() {
        return rTypes.size();
    }

    public int getSchemaCnt() {
        return schIdMap.size();
    }


    /**
     * @param path The path of the endpoint without leading "/" (case-insensitive)
     * @return The ResourceType that defines the endpoint
     */
    public ResourceType getResourceTypeByPath(String path) {
        if (path == null)
            return null;
        return rTypePaths.get(path.toLowerCase());
    }

    public Schema getSchemaByName(String name) {
        return schNameMap.get(name);
    }

    /**
     * @param id May be the schema id value or the schema name to locate the corresponding <code>Schema<code> object
     * @return The <code>Schema</code> object that corresponds to the schema urn provided
     */
    public Schema getSchemaById(String id) {
        return schIdMap.get(id);
    }

    /**
     * @return The Collection of all Schema objects from the Schema Id hash map.
     */
    public Collection<Schema> getSchemas() {
        return schIdMap.values();
    }

    public boolean persistSchemaMode() {
        return this.persistSchema;
    }

    /**
     * This method checks the persistence provider to see if the data for Schema and ResourceTypes
     * has already been stored. In this case of a new database, the data has to first be loaded from
     * json config files via loadDefaultSchema/loadDefaultResoruceTypes.
     * @return true if schema exists and was loaded
     * @throws SchemaException when backend is unable to parse the stored schema.
     */
    public boolean checkAndLoadStoredSchema() throws ScimException {
        if (this.backendHandler == null) {
            logger.error("BackendHandler not initialized.");
            return false;
        }
        backendHandler.isReady();


        //TODO: Complete stored schema implementation
        Collection<Schema> dbschemas = backendHandler.loadSchemas();
        if (dbschemas == null) return false; // schema has not been stored.

        //resetSchema();  // resets the default schema so persisted schema can be loaded
        for (Schema dbschema : dbschemas)
            addSchema(dbschema);

        Collection<ResourceType> dbTypes = backendHandler.loadResourceTypes();
        for (ResourceType dbType : dbTypes)
            addResourceType(dbType);
        logger.info("\tSchema and Resource Types loaded from database.");
        return true;
    }

    public void persistSchema() throws IOException {
        logger.debug("Persisting schema to databse provider");

        this.backendHandler.syncConfig(this);
    }


}


