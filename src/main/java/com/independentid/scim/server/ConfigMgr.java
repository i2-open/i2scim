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
package com.independentid.scim.server;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

/**
 * @author pjdhunt
 * ConfigMgr is a centralized SCIM server configuration service. It loads the schema from
 * JSON files or pulls them from a configured persistence provider. ConfigMgr can be
 * injected as a bean (name="ConfigMgr") or using the {@link #getInstance()} method.
 *
 */
@Configuration
@Component("ConfigMgr")
@ComponentScan(basePackages= {"com.independentid.scim.server","com.independentid.scim.backend"})
public class ConfigMgr {
	
	public final static List<String> SCIM_CORE_ATTRS = Arrays.asList(
			"id","externalid","schemas","meta");

	private final static Logger logger = LoggerFactory.getLogger(ConfigMgr.class);
		
	//public final static String PARAM_SCHEMA_PATH = "scim.schema.path";
	//public final static String DEFAULT_SCHEMA_PATH = "/META-INF/scimSchema.json";
	
	@Value("${scim.schema.path:classpath:/schema/scimSchema.json}")
	private String schemaPath;

	//public final static String PARAM_RTYPE_PATH = "scim.resourceType.path";
	//public final static String DEFAULT_RTYPE_PATH = "/META-INF/resourceType.json";
	
	@Value("${scim.resourcetype.path:classpath:/schema/resourceTypes.json}")
	private String resourceTypePath;
	
	@Value("${scim.coreSchema.path:classpath:/schema/scimCommonSchema.json}")
	private String coreSchemaPath;
	

	//public final static String PARAM_PRETTY_JSON = "scim.json.pretty";
	//public final static String DEFAULT_PRETTY_JSON = "false";
	
	@Value("${scim.json.pretty:false}")
	private boolean jsonPretty;
	
	@Value("${scim.persist.schema:true}")
	private boolean persistSchema;

	//public final static String PARAM_PROVIDER_CLASS = "scim.provider.class";
	//public final static String DEFAULT_PROVIDER = MongoProvider.class.getName();

	//public final static String PARAM_MAX_RESULTS = "scim.query.max.resultsize";
	//public final static String DEFAULT_MAX_RESULTS = "1000";
	
	@Value("${scim.query.max.resultsize:1000}")
	private int maxResults; 
	
	//public final static String PARAM_BULK_MAX_OPS = "scim.bulk.max.ops";
	//public final static String DEFAULT_BULK_MAX_OPS = "1000";
	
	@Value("${scim.bulk.max.ops:1000}")
	private int bulkMaxOps;
	
	//public final static String PARAM_BULK_MAX_ERRS = "scim.bulk.max.errors";
	//public final static String DEFAULT_BULK_MAX_ERRS = "50";

	@Value("${scim.bulk.max.errors:5}")
	private int bulkMaxErrors;
	
	@Value("${scim.thread.count:5}")
	private int threadCount;
	
	@Value("${scim.security.enable:true}")
	private boolean isSecure;

	@Value("${scim.security.authen.jwt:true}")
	private boolean authJwt;
	
	@Value("${scim.security.authen.opaque:false}")
	private boolean authOpaque;
	
	@Value("${scim.security.authen.basic:false}")
	private boolean authBasic;
	
	@Value("${scim.security.root.enable:false}")
	private boolean rootEnable;
	
	@Value("${scim.security.root.username:admin}")
	private String rootUser;
	
	@Value("${scim.security.root.password:admin}")
	private String rootPassword;
	
	@Autowired
	private BackendHandler persistMgr;
			
	private LinkedHashMap<String,Schema> schIdMap = new LinkedHashMap<String,Schema>();
	private LinkedHashMap<String,Schema> schNameMap = new LinkedHashMap<String,Schema>();
	
	//private TreeMap<String,Attribute> vals = new TreeMap<String,Attribute>();
	
	private LinkedHashMap<String,ResourceType> rTypes = new LinkedHashMap<String,ResourceType>();
	private HashMap<String,ResourceType> rTypePaths = new HashMap<String,ResourceType>();
	
	//private ServletConfig scfg = null;
		
	private boolean initialized = false;
	
	@Autowired
	ResourceLoader resourceloader;
	
	@Autowired
	ApplicationContext ctx;
	
	private static ConfigMgr self=null;
	
	public ConfigMgr() {
	
	}
	
	@PostConstruct
	public void initializeConfiguration() throws ScimException,IOException, SchemaException {
		if (initialized) {
			logger.debug("ERROR: Multiple initializations detected");
			return;
		}
		
		logger.info("======Initializing SCIM Config Mangaer=====");
		
		self = (ConfigMgr) this.ctx.getBean("ConfigMgr");
		
		//If the server will not persist its schema in the database, then load directly into config.
		if (!this.persistSchemaMode()) {
			loadDefaultSchema();
		
			loadDefaultResourceTypes();
			
			loadCoreSchema();
			
			initialized = true;
			return;
		}
		
		if(!checkAndLoadStoredSchema()) {
			loadDefaultSchema();
			
			loadDefaultResourceTypes();
			
			persistSchema();
		}
		loadCoreSchema();
		
		initialized = true;
		
		if (isRootEnabled() && getRootPassword().equals("admin"))
			logger.warn("Server is configured with root access and default password!");
	}
	
	public static ConfigMgr getInstance() {

		return self;
	}
	
	/*
	@Bean	
	public ServletRegistrationBean<HttpServlet> scimServlet() {
	   ServletRegistrationBean<HttpServlet> servRegBean = new ServletRegistrationBean<>();
	   servRegBean.setServlet(new ScimV2Servlet());
	   servRegBean.addUrlMappings("/v2/*","/*");
	   servRegBean.setLoadOnStartup(1);
	   return servRegBean;
	}*/
	
	/**
	 * @return the isSecure
	 */
	public boolean isSecure() {
		return isSecure;
	}

	/**
	 * @return the authJwt
	 */
	public boolean isAuthJwt() {
		return authJwt;
	}

	/**
	 * @return the authOpaque
	 */
	public boolean isAuthOpaque() {
		return authOpaque;
	}

	/**
	 * @return the authBasic
	 */
	public boolean isAuthBasic() {
		return authBasic;
	}
	
	public boolean isRootEnabled() {
		return rootEnable;
	}
	
	public String getRootUser() {
		return rootUser;
	}
	
	public String getRootPassword() {
		return rootPassword;
	}
	
	/**
	 * This method checks the persistence provider to see if the data for Schema and ResourceTypes
	 * has already been stored. In this case of a new database, the data has to first be loaded from
	 * json config files via loadDefaultSchema/loadDefaultResoruceTypes.
	 * @return true if schema exists and was loaded
	 * @throws SchemaException 
	 */
	public boolean checkAndLoadStoredSchema() throws ScimException, SchemaException {
		if (this.persistMgr == null) {
			logger.error("BackendHandler not initialized.");
			return false;
		}
		
		//TODO: Complete stored schema implementation
		Collection<Schema> dbschemas = persistMgr.loadSchemas();
		if (dbschemas == null) return false; // schema has not been stored.
		
		Iterator<Schema> siter = dbschemas.iterator();
		while (siter.hasNext())
			addSchema(siter.next());
		
		Collection<ResourceType> dbTypes = persistMgr.loadResourceTypes();
		Iterator<ResourceType> riter = dbTypes.iterator();
		while (riter.hasNext()) 
			addResourceType(riter.next());
		
		return true;
	}
	
	public void persistSchema() throws ScimException {
		logger.debug("Persisting schema to databse provider");
		
		try {
			this.persistMgr.syncConfig(this);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	/**
	 * Loads the default schema from the config properties into ConfigMgr "schIdMap" map.
	 * @throws ScimException thrown configured filepath is undefined or invalid, or due to invalid JSON
	 * @throws IOException thrown due to file processing errors (missing file)
	 */
	protected void loadDefaultSchema() throws ScimException, IOException {
		logger.debug("  loading SCIM Schema");
		
		
		String filePath = this.schemaPath;
	
		if (filePath == null)
			throw new ScimException("SCIM default schema file path is null.");
	
		try {
			File sfile = findClassLoaderResource(filePath);
			
			parseSchemaConfig(sfile);
		} catch (JsonProcessingException e) {
			throw new ScimException(
					"JSON parsing exception processing schema configuration.",
					e);
		} 
	}
	
	/**
	 * This method loads the SCIM Core Schema definitions. This schema comprises attributes
	 * that are in common across all SCIM Resources  This method should be called *after* other
	 * schema is loaded.
	 * @throws ScimException
	 * @throws IOException
	 */
	protected void loadCoreSchema() throws ScimException, IOException {
		String filePath = this.coreSchemaPath;
		
		if (filePath == null)
			throw new ScimException("SCIM default schema file path is null.");
	
		try {
			File sfile = findClassLoaderResource(filePath);
			
			if (sfile == null)
				throw new IOException("Schema file must not be null");
			
			if (!sfile.exists())
				throw new IOException("Schema file does not exist: "+sfile.getPath());

			logger.debug("\t..Parsing Core Attribute Schema");
			
			JsonNode node = JsonUtil.getJsonTree(sfile);

			if (!node.isObject()) {
				logger.error("Was expecting a JSON Object for SCIM Core Attributes. Found: "+node.getNodeType().toString());
				return;
			}
			Schema schema;
			try {
				schema = new Schema(node);
				addSchema(schema);
				if (logger.isDebugEnabled())
					logger.debug("\t....Attribute Schema loaded>"+schema.getId());
				//System.out.println("Debug: Schema loaded>"+schema.getId());
			} catch (SchemaException e) {
				logger.warn("SchemaException while parsing Schema Core Attribute config: "+filePath+", "+e.getLocalizedMessage(), e);
			}
			return;
			
			
		} catch (JsonProcessingException e) {
			throw new ScimException(
					"JSON parsing exception processing schema core configuration: "+filePath,
					e);
		} 
	}
	
	/**
	 * Loads the default resource types defined by config properties into ConfigMgr "resourceTypes" map.
	 * @throws ScimException thrown configured filepath is undefined or invalid, or due to invalid JSON
	 * @throws IOException thrown due to file processing errors (missing file)
	 */
	protected void loadDefaultResourceTypes() throws ScimException, IOException {
		logger.debug("  Loading Resource types.");
	    
		if (resourceTypePath == null)
			throw new ScimException("SCIM default resource type configuraiton file path is null.");
				
		try {
			File sfile = findClassLoaderResource(resourceTypePath);
			
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

	/**
	 * @return String representing the default path for Resource Type json configuration file
	 */
	public String getResourceTypePath() {
		return this.resourceTypePath;
	}
	
	/**
	 * @return String representing the default path for json Schema definitions file
	 */
	public String getSchemaPath() {
		return this.schemaPath;
	}
	
	/**
	 * Returns a File for a resource packaged with the SCIM server.
	 * @param file The resource to be located
	 * @return
	 * @throws IOException
	 */
	public File findClassLoaderResource(String file) throws IOException {
		Resource res = this.resourceloader.getResource(file);
		return res.getFile();
	}
	
	private void addResourceType(ResourceType type) throws SchemaException {
		logger.debug("  Resource endpoint loading: "+type.getId()+", URI: "+type.getEndpoint());	
		this.rTypes.put(type.getId(), type);
		String path = type.getTypePath();  
		if (path == null) 
			throw new SchemaException("The resource endpoint path for "+type.getName()+ " was null or not valid.");
		if (path.startsWith("/"))
			path = path.substring(1);
		this.rTypePaths.put(path.toLowerCase(), type);
		
	}
	
	public void parseResourceTypes(File typeFile) throws SchemaException, JsonProcessingException, IOException {

		if (typeFile == null)
			throw new IOException("Resource endpoint file must not be null");
		
		if (!typeFile.exists())
			throw new IOException("Resource endpoint file does not exist: "+typeFile.getPath());
		
		logger.debug("\t..Parsing ResourceType Config.");
		
		JsonNode node = JsonUtil.getJsonTree(typeFile);
		
		Iterator<JsonNode> iter = null;
		if (node.isObject()) {
			//The outer element is an object {} rather than []
			node = node.get(0);
			
		} 
		if (node.isArray()) {
			iter = node.iterator();
			while (iter.hasNext()) {
				JsonNode typeNode = iter.next();
				ResourceType type;
		
				type = new ResourceType(typeNode);
				addResourceType(type);
			}
			return;
		} else {
			logger.error("While parsing resource types, detected node endpoint "+node.getNodeType().toString());
		}
		// TODO Handle invalid node on Resource Type parsing
		logger.error("Unexpected JsonNode while parsing ResourceTypes: "+node.toString());
		return;
		
	}
	
	public void addSchema(Schema schemaDef) throws SchemaException {
		String id = schemaDef.getId();
		String name = schemaDef.getName();
		logger.debug("  Loading Schema: "+id+", Name: "+name);
		
		//Store schIdMap in the hash by Id and Name!
		schIdMap.put(schemaDef.getId(), schemaDef);
		
		schNameMap.put(schemaDef.getName(), schemaDef);
	}
	//public void parseSchemaConfig() {}

	protected void parseSchemaConfig(File schemaFile) throws JsonProcessingException, IOException {
		if (schemaFile == null)
			throw new IOException("Schema file must not be null");
		
		if (!schemaFile.exists())
			throw new IOException("Schema file does not exist: "+schemaFile.getPath());

		logger.debug("\t..Parsing Schema Config.");
		JsonNode node = JsonUtil.getJsonTree(schemaFile);
		
		// In case this is a reload, reset the current Schemas
		this.schIdMap.clear();
		
		Iterator<JsonNode> iter = null;
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
					schema = new Schema(schemaNode);
					addSchema(schema);
					//logger.debug("  Schema loaded>"+schema.getId());
					//System.out.println("Debug: Schema loaded>"+schema.getId());
				} catch (SchemaException e) {
					logger.warn("SchemaException while parsing schema config.", e);
				}
			}
			return;
			
		} else {
			logger.warn("Dectected unexpected node while parsing Schema: "+node.getNodeType().toString());
			//System.out.println("Detected node endpoint "+node.getNodeType().toString());
		}
		// TODO Handle invalid node on schema parsing
		logger.error("Unexpected JsonNode while parsing schema: "+node.toString());
		return;
	}

	/**
	 * Serializes a result using the provided JsonGenerator. Does not close or flush the generator.
	 * @param ctx The SCIM RequestCtx - which specifies all the request parameters
	 * @param gen The JsonGenerator with which to generate the result. 
	 * @throws IOException
	 */
	public void serializeSchema(RequestCtx ctx,JsonGenerator gen) throws IOException {
				
		String id = ctx.getPathId();
		if (id != null && id.length() > 0) {
			Schema schema = getSchemaById(id);
			if (schema == null) {
				return;
			}
			schema.serialize(gen, ctx, false);			
			
		} else {
			gen.writeStartArray();
			Iterator<Schema> iter = schIdMap.values().iterator();
			while (iter.hasNext()) {
				Schema schema = iter.next();
				schema.serialize(gen, ctx, false);
			}
			gen.writeEndArray();
		
		}
	}

	/**
	 * @param ctx The SCIM RequestCtx - which specifies all the request parameters
	 * @param writer If provided, output will be sent to the writer specified. Otherwise a String is returned.
	 * @return A String containing the serialized JSON value. Returns <code>null</code> if a writer is specified.
	 * @throws IOException
	 */
	public String serializeSchema(RequestCtx ctx, Writer writer) throws IOException {
		
		Writer swriter;
		if (writer == null)
			swriter = new StringWriter();
		else
			swriter = writer;
		
        JsonGenerator gen = JsonUtil.getGenerator(swriter, false);
		
		serializeSchema(ctx,gen);
		
		gen.close();
		
		//Only return the string value if no writer was specified.
		if (writer == null)
			return swriter.toString();
		else 
			return null;
	}

	/**
	 * @param ctx The SCIM RequestCtx - which specifies all the request parameters
	 * @param gen The JsonGenerator used to serialize the requested values. Caller must flush and close gen.
	 * @return A String containing the serialized JSON value. Returns <code>null</code> if a writer is specified.
	 * @throws IOException
	 */
	public void serializeResourceTypes(RequestCtx ctx,JsonGenerator gen) throws IOException {
		
		String id = ctx.getPathId();
		if (id != null && id.length() > 0) {
			ResourceType type = getResourceType(id);
			//		.getConfig(ctx.sctx).getSchema(ctx.id);
			if (type == null) {
				return;
			}
			type.serialize(gen, ctx, false);			
			
		} else {
			gen.writeStartArray();
			Iterator<ResourceType> iter = rTypes.values().iterator();
			while (iter.hasNext()) {
				ResourceType type = iter.next();
				type.serialize(gen, ctx, false);
			}
			gen.writeEndArray();
		
		}
	}
	
	/**
	 * @param ctx The SCIM RequestCtx - which specifies all the request parameters
	 * @param writer If provided, output will be sent to the writer specified. Otherwise a String is returned.
	 * @return A String containing the serialized JSON value. Returns <code>null</code> if a writer is specified.
	 * @throws IOException
	 */
	public String serializeResourceTypes(RequestCtx ctx, Writer writer) throws IOException {
		
		Writer swriter;
		if (writer == null)
			swriter = new StringWriter();
		else
			swriter = writer;
		
        JsonGenerator gen = JsonUtil.getGenerator(swriter, false);
		
		serializeResourceTypes(ctx,gen);
		gen.close();
		
		//Only return the string value if no writer was specified.
		if (writer == null)
			return swriter.toString();
		else 
			return null;
	}

	public void serializeServiceProviderConfig(RequestCtx ctx, JsonGenerator gen) throws IOException {
		
        gen.writeStartObject();
        
        gen.writeArrayFieldStart("schemas");
        gen.writeString(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig);
        gen.writeEndArray();
        
        // Identify the server
        gen.writeStringField("ProductName", "IndependentId SCIM Test Directory");
        //gen.writeStringField("ProductId", "BigDirectory");
        gen.writeStringField("ProductVersion", "V1.0");
        
        /* Not defined in standard schema.
        gen.writeArrayFieldStart("ScimVersionSupport");
        gen.writeString("2.0");
        gen.writeEndArray();
        */
        
        // Documentation
        // TODO set up web documentation URL
        
        gen.writeStringField("documentationUri", "https://independentid.com/scim");
        
        
        // Indicate Patch supported
        gen.writeFieldName("patch");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeEndObject();
        
        // Indicate Bulk support
        gen.writeFieldName("bulk");
        gen.writeStartObject();
        gen.writeBooleanField("supported", false);
        gen.writeNumberField("maxOperations", 0);
        gen.writeNumberField("maxPayloadSize", 0);
        gen.writeEndObject();
        
        // Indicate Filter support
        gen.writeFieldName("filter");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeNumberField("maxResults", 0);
        gen.writeEndObject();
        
        // Change Password support
        gen.writeFieldName("changePassword");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeEndObject();

        // Sorting
        gen.writeFieldName("sort");
        gen.writeStartObject();
        gen.writeBooleanField("supported", false);
        gen.writeEndObject();


        // ETag
        gen.writeFieldName("etag");
        gen.writeStartObject();
        gen.writeBooleanField("supported", true);
        gen.writeEndObject();
        
        // Authentication Schemes
        gen.writeArrayFieldStart("authenticationSchemes");
        gen.writeStartObject();
        gen.writeStringField("name", "httpbasic");
        gen.writeStringField("description", "HTTP Basic Authentication");
        gen.writeStringField("specUri", "https://www.ietf.org/rfc/rfc2617.txt");
        gen.writeEndObject();
        gen.writeEndArray();

	}

	public String serializeServiceProviderConfig(RequestCtx ctx, Writer writer) throws IOException {
		
		//TODO: Check for filter and apply to response
		
		Writer swriter;
		if (writer == null)
			swriter = new StringWriter();
		else
			swriter = writer;
		
        JsonGenerator gen = JsonUtil.getGenerator(swriter, false);
		
        serializeServiceProviderConfig(ctx,gen);
        
        gen.close();
        
        return swriter.toString();

	}

	public Schema getSchemaByName(String name) {
		return this.schNameMap.get(name);
	}
	
	/**
	 * @param id May be the schema id value or the schema name to locate the corresponding <code>Schema<code> object
	 * @return The <code>Schema</code> object that corresponds to the schema urn provided
	 */
	public Schema getSchemaById(String id) {
		return this.schIdMap.get(id);
	}
	
	/**
	 * @return The Collection of all Schema objects from the Schema Id hash map.
	 */
	public Collection<Schema> getSchemas() {
		return this.schIdMap.values();
	}
	
	/**
	 * Thie method looks up an <code>Attribute</code> type based on one of 3 methods:
	 * 1. If schemaId is provided, the attribute is matched from there.
	 * 2. If no attribute found, the RequestCtx is checked for a ResourceType where the container schema and extensions are used
	 * 3. If still no match, all schemas are checked for a match. 
	 * Note: this method cannot be used to locate sub=attributes directly. 
	 * @param schemaId Optional Schema URN or null
	 * @param name The base attribute name (required)
	 * @param subAttrName The name of a sub-attribute if desired or null
	 * @param ctx Optional RequestCtx object (used to detect resourceType)
	 * @return The matching Attribute or null if not matched
	 */
	public Attribute findAttribute(String schemaId, String name, String subAttrName, RequestCtx ctx) {
		Schema schema = null;
		
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
			
			return (subAttrName == null)?attr:attr.getSubAttribute(subAttrName);
		}
		
			
		
		// If not found, try to get it from the container via RequestCtx...
		// Note that the attribute could come from the core schema or extension schema
	
		if (ctx != null && ctx.getResourceContainer() != null) {
			//check the resource type endpoint for core schema and extensions
			ResourceType type = getResourceTypeByPath(ctx.getResourceContainer());
			if (type != null) {
				Schema core = this.getSchemaById(type.getSchema());
				attr = core.getAttribute(name);
				if (attr != null) return attr;
				
				String[] exts = type.getSchemaExtension();
				for (int i=0; i < exts.length; i++) {
					Schema ext = this.getSchemaById(exts[i]);
					if (ext != null) {
						attr = ext.getAttribute(name);
						if (attr != null) { 
							if (subAttrName != null) {
								if (attr.getSubAttribute(subAttrName) != null)
									return attr.getSubAttribute(subAttrName);
								continue;
							} else
								return attr;
						}
					}
				}
			}
		}
		
		// finally, search all the schIdMap for the attribute
		Iterator<Schema> iter = this.schIdMap.values().iterator();
		while (iter.hasNext()) {
			Schema sch = iter.next();
			attr = sch.getAttribute(name);
			if (attr != null) { 
				if (subAttrName != null) {
					if (attr.getSubAttribute(subAttrName) != null)
						return attr.getSubAttribute(subAttrName);
					continue;
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
	 * @param ctx The request context (used to detect resource type to default schema urn)
	 * @return The <code>Attribute<code> type for the attribute path requested.
	 */
	public Attribute findAttribute(String path, RequestCtx ctx) {
		int aindex = path.lastIndexOf(':');
		String schema = null;
		String attr = null;
		String sattr = null;
		Attribute aType = null;
		
		if (aindex > -1) {
			schema = path.substring(0,aindex);
			attr = path.substring(aindex+1);
			
		} else
			attr = path;
		int sind = attr.indexOf('.');
		if (sind > -1) {
			sattr = attr.substring(sind+1);
			attr = attr.substring(0,sind);
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
					for (int i=0; i < exts.length; i++) {
						aType =  this.findAttribute(exts[i], attr, sattr, ctx);
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
		
		return this.rTypes.values();
	}
	
	public ResourceType getResourceType(String id) {
		return this.rTypes.get(id);
	}
	
	public int getResourceTypeCnt() {
		return this.rTypes.size();
	}
	
	public int getSchemaCnt() {
		return this.schIdMap.size();
	}
	
	
	/**
	 * @param path The path of the endpoint without leading "/" (case-insensitive)
	 * @return The ResourceType that defines the endpoint
	 */
	public ResourceType getResourceTypeByPath(String path) {
		if (path == null)
			return  null;
		return this.rTypePaths.get(path.toLowerCase());
	}
	
	public boolean isPrettyJsonMode() {
		return this.jsonPretty;
	}
	
	/**
	 * @return Boolean contains true if ConfigMgr is fully initialized.
	 */
	public boolean isReady() {
		return this.initialized;
	}
	
	@PreDestroy
	public synchronized void shutdown() {
		// clean up so that GC works faster
		
		this.rTypePaths.clear();
		this.rTypePaths = null;
		this.rTypes.clear();
		this.rTypes = null;
		this.schIdMap.clear();
		this.schIdMap = null;
	}
	
	public void setMaxResults(int max) {
		this.maxResults = max;
	}
	
	public void setBulkMaxOps(int max) {
		this.bulkMaxOps = max;
	}
	
	public void setBulkMaxErrors(int max) {
		this.bulkMaxErrors = max;
	}
	
	public int getMaxResults() {
		return this.maxResults;
	}
	
	public boolean persistSchemaMode() {
		return this.persistSchema;
	}
	
	public int getBulkMaxOps() {
		return this.bulkMaxOps; 
	}
	
	public int getBulkMaxErrors() {
		return this.bulkMaxErrors; 
	}
	
	public int getPoolThreadCount() {
		return this.threadCount;
	}
	
	/*
	 * Added to solve @Value property mapping problems per: https://code-examples.net/en/q/19d9b07
	 */
	@Bean
	public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
	    return new PropertySourcesPlaceholderConfigurer();
	}
	
}
