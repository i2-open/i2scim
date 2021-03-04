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
package com.independentid.scim.resource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.IResourceModifier;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.*;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.*;
import com.independentid.scim.schema.*;
import com.independentid.scim.serializer.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;

public class ScimResource implements IResourceModifier, IBulkIdTarget {
	private final Logger logger = LoggerFactory.getLogger(ScimResource.class);

	public SchemaManager smgr;

	public final static String SCHEMA_EXT_PREFIX = "Ext-";
	
	//protected ConfigMgr cfg;

	protected String id;

	protected String externalId;

	protected List<String> schemas;
	public static Schema commonSchema;
	protected Schema coreSchema;
	protected ResourceType type;
	protected String container;

	// Holds the meta data about the resource
	
	protected Meta meta;

	protected LinkedHashMap<Attribute, Value> coreAttrVals;

	protected LinkedHashMap<String, ExtensionValues> extAttrVals;

	protected HashSet<Attribute> attrsInUse = new HashSet<>();

	protected HashSet<Attribute> blockedAttrs = new HashSet<>();
		
	protected boolean modified;
	
	protected IBulkIdResolver idResolver;

	/**
	 * Construct the resource based on JsonNode
	 *
	 * @param schemaManager The {@link SchemaManager} object holding SCIM shema definitions
	 * @param resourceNode The JsonNode representation of a SCIM resource to be parsed
	 * @param container The top level path element (e.g. Users).
	 * @throws SchemaException Thrown when object parsed violates SCIM Schema
	 * @throws ParseException Thrown when a JSON parsing error occurs
	 * @throws ScimException Thrown due to internal SCIM error (TBD)
	 */
	public ScimResource(SchemaManager schemaManager, JsonNode resourceNode, String container)
			throws SchemaException, ParseException, ScimException {
				this(schemaManager, resourceNode, null, container);
		
	}

	protected ScimResource(SchemaManager smgr) {
		this.smgr = smgr;
		commonSchema = smgr.getSchemaById(ScimParams.SCHEMA_SCHEMA_Common);
		this.coreAttrVals = new LinkedHashMap<>();
		this.extAttrVals = new LinkedHashMap<>();
		this.idResolver = null;
		this.modified = false;
	}
	
	
	/**
	 * Creates a ScimResource based on a JsonNode structure
	 * @param schemaManager The server {@link SchemaManager} instance container server schema
	 * @param resourceNode A <JsonNode> object containing a SCIM JSON parsed object
	 * @param bulkIdResolver An <IBulkIdResulver> used to resolve identifiers during bulk operations
	 * @param container A <String> identifying the resource container where the object is from or to be stored (e.g. Users, Groups). Used to lookup <ResourceType> and <Schema>
	 * @throws SchemaException Thrown when object parsed violates SCIM Schema
	 * @throws ParseException Thrown when a JSON parsing error occurs
	 * @throws ScimException Thrown due to internal SCIM error (TBD)
	 */
	public ScimResource(SchemaManager schemaManager, JsonNode resourceNode, IBulkIdResolver bulkIdResolver, String container)
			throws SchemaException, ParseException, ScimException {
		
		this.smgr = schemaManager;

		this.coreAttrVals = new LinkedHashMap<>();
		this.extAttrVals = new LinkedHashMap<>();
		commonSchema = schemaManager.getSchemaById(ScimParams.SCHEMA_SCHEMA_Common);
		this.container = container;
		if (container != null)
			setResourceType(container);
		this.idResolver = bulkIdResolver;
		parseJson(resourceNode, smgr);
		this.modified = false;
		
	}
	
	public String getResourceType() {
		if (this.type != null)
			return this.type.getName();
		if (this.meta == null) {
			// Added because serviceproviderconfig does not necessarily have a meta object
			if (this.schemas.contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig))
				return ScimParams.TYPE_SERV_PROV_CFG;
			if (this.schemas.contains(ScimParams.SCHEMA_SCHEMA_SYNCREC))
				return SystemSchemas.TRANS_CONTAINER;
			return null;
		}
		return this.meta.getResourceType();
	}
	
	/**
	 * @param container The String resource type of the resource (e.g. User, Group).
	 */
	public void setResourceType(String container) {
		this.container = container;
		this.type = smgr.getResourceTypeByPath(container);
		if (this.type != null)
			this.coreSchema = smgr.getSchemaById(this.type.getSchema());
	
	}
	
	public void setResourceType(RequestCtx ctx) {
		if (ctx != null) {
			String container = ctx.getResourceContainer();
			setResourceType(container);
		}
	}

	public String getContainer() {
		return this.container;
	}

	public String getId() {
		return this.id;
	}

	public String getExternalId() {
		return this.externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public Meta getMeta() {
		return this.meta;
	}

	public void setMeta(Meta meta) {
		this.meta = meta;
	}
	
	public List<String> getSchemaURIs() {
		return this.schemas;
	}

	public void setId(String id) {
		this.id = id;
	}
	
	public Map<Attribute,Value> getCoreAttrVals() {
		return this.coreAttrVals;
	}
	
	public Map<String,ExtensionValues> getExtensions() {
		return this.extAttrVals;
	}

	public synchronized void addValue(@NotNull Value addval) throws SchemaException {
		Attribute attr = addval.getAttribute();
		Attribute rootAttribute;
		if (attr.isChild())
			rootAttribute = attr.getParent();
		else
			rootAttribute = attr;

		if (attr.getSchema().equals(coreSchema.getId())) {
			if (rootAttribute.isMultiValued()) {
				if (addval instanceof MultiValue) {
					MultiValue rval =(MultiValue) this.getValue(rootAttribute);
					if (rval == null) {
						coreAttrVals.put(rootAttribute, addval);
						attrsInUse.add(rootAttribute);
						return;
					}
					rval.addValue(addval);
					return;
				}
				MultiValue rval =(MultiValue) this.getValue(rootAttribute);
				if (rval == null) {
					rval = new MultiValue(rootAttribute, new LinkedList<>());
					coreAttrVals.put(rootAttribute,rval);
					attrsInUse.add(rootAttribute);
				}
				rval.addValue(addval);
				return;
			}
			if (attr.isChild()) {
				Value rval = this.getValue(rootAttribute);
				// If parent was undefined, add it.
				if (rval == null) {
					rval = new ComplexValue();
					this.coreAttrVals.put(rootAttribute, rval);
					attrsInUse.add(rootAttribute);
				}
				if (rval instanceof ComplexValue) { 
					// Add the sub attribute value to the parent
					ComplexValue cval = (ComplexValue) rval;
					cval.addValue(attr, addval);
					attrsInUse.add(attr);
					return;
				}
			}
			this.coreAttrVals.put(attr, addval);
			attrsInUse.add(attr);

			return;
			
		}
		
		// The attribute is an extension attribute
		Schema eschema = smgr.getSchemaById(attr.getSchema());
		if (!eschema.getId().equalsIgnoreCase(this.coreSchema.getId())) {
			ExtensionValues map = this.extAttrVals.get(eschema.getId());
			if (map == null) {
				// This occurs if the existing resource did not have the extension previously
				map = new ExtensionValues(eschema, new LinkedHashMap<>());
				this.extAttrVals.put(eschema.getId(), map);
			}
			map.addValue(attr,addval);
			attrsInUse.add(attr);

			return;
		}

		logger.error("Attribute ("+attr.getName()+") could not be mapped to current resource (type: "+type.getName()+").");
		
	}
	
	public synchronized void  removeValue(Attribute attr) {
		//ResourceType type = cfg.getResourceType(getResourceType());
		String core = type.getSchema();

		Attribute rootAttribute;
		if (attr.isChild())
			rootAttribute = attr.getParent();
		else
			rootAttribute = attr;

		if (attr.getSchema().equals(core)) {
			if (attr.isChild()) {
				Value rval = this.getValue(rootAttribute);
				// If parent was undefined, add it.
				if (rval == null) {
					return;
					
				}
				if (rval instanceof ComplexValue) {
					// Add the sub attribute value to the parent
					ComplexValue cval = (ComplexValue) rval;
					cval.removeValue(attr);
					attrsInUse.remove(attr);
					// if the parent has no sub-attributes left, remove the parent
					if (cval.valueSize() == 0) {
						this.coreAttrVals.remove(rootAttribute);
						attrsInUse.remove(rootAttribute);
					}
					return;
				}
			}
			// remove the simple core attribute
			this.coreAttrVals.remove(attr);
			attrsInUse.remove(attr);
			return;
			
		}
		
		// The attribute is an extension attribute
		Schema eSchema = smgr.getSchemaById(attr.getSchema());
		ExtensionValues map = this.extAttrVals.get(eSchema.getId());
		//refval1 = map.getAttribute(rootAttribute.getName());
		if (map == null)
			return;  // Nothing to remove

		if (attr.isChild()) {
			Value rval = map.getValue(rootAttribute.getName());
			// If parent was undefined, add it.
			if (rval == null) {
				return;
				
			}
			if (rval instanceof ComplexValue) { 
				// Add the sub attribute value to the parent
				ComplexValue cval = (ComplexValue) rval;
				cval.removeValue(attr);
				attrsInUse.remove(attr);
				// if the parent has no sub-attributes left, remove the parent
				if (cval.valueSize() == 0) {
					map.removeValue(rootAttribute.getName());
					attrsInUse.remove(rootAttribute);
				}
				return;
			}
		}
		
		// Not a complex attribute, just remove it.
		map.removeValue(attr);
		attrsInUse.remove(attr);
		
	}
	
	/**
	 * @return The <Schema> for the main content of the <ScimResource>.
	 */
	public Schema getBodySchema() {
		return this.coreSchema;
	}
	
	public void parseJson(JsonNode node, SchemaManager schemaManager) throws ParseException, ScimException {

		JsonNode snode = node.get(ScimParams.ATTR_SCHEMAS);
		if (snode == null) throw new SchemaException("Schemas attribute missing");
		else {
			Iterator<JsonNode> iter = snode.elements();
			this.schemas = new ArrayList<>();
			while (iter.hasNext()) {
				JsonNode anode = iter.next();
				this.schemas.add(anode.asText());
			}

		}

		JsonNode item = node.get(ScimParams.ATTR_ID);
		if (item != null)
			this.id = item.asText();

		JsonNode metaNode = node.get(ScimParams.ATTR_META);
		//Note: a SCIM schema or ResourceType might not have a meta
		Attribute mattr = commonSchema.getAttribute(ScimParams.ATTR_META);
		attrsInUse.add(mattr);
		if (metaNode != null) {
			this.meta = new Meta(metaNode);

			if (this.type == null) {
				if (this.meta.getResourceType() != null)
					this.type = smgr.getResourceTypeByName(this.meta.getResourceType());
				else { // infer type by schema
					for(String aschema : this.schemas) {
						this.type = smgr.getResourceTypeById(aschema);
						if (this.type != null)
							break;
					}
				}
				if (this.type == null)
					throw new SchemaException("Unable to determine resource type: "+this.id);
				this.coreSchema = smgr.getSchemaById(this.type.getSchema());
				this.container = this.type.getTypePath();
			}
		} else
			this.meta = new Meta();
		
		// We will override meta resource type based on where this object is written (driven by type)
		this.meta.setResourceType(this.getResourceType());
		
		// TODO Write validate method to check schemas attr against resource
		// endpoint
		
		item = node.get(ScimParams.ATTR_EXTID);
		if (item != null) {
			Attribute attr = commonSchema.getAttribute(ScimParams.ATTR_EXTID);
			attrsInUse.add(attr);
			this.externalId = item.asText();
		}

		parseAttributes(node,true,false);
		
		// Calculate the hash if the underlying provider didn't already do it.
		if (this.meta != null && this.meta.getVersion() == null)
			this.meta.setVersion(this.calcVersionHash());
	}
	
	public void parseAttributes(JsonNode node, boolean isReplace, boolean ignoreMutability) throws ConflictException, SchemaException, ParseException {
		
		// Look for all the core schema vals
		
		Set<String> exts = type.getSchemaExtensions().keySet();
		
		//Attribute[] vals = core.getAttributes();
		
		Iterator<String> iter = node.fieldNames();
		while (iter.hasNext()) {
			String field = iter.next();
			String lfield = field.toLowerCase();
			// if is is a common attribute, skip
			
			//if (ConfigMgr.SCIM_CORE_ATTRS.stream().anyMatch(lfield::equals))
			if (ConfigMgr.SCIM_CORE_ATTRS.contains(lfield))
				continue;
			//if it is an extension object, process it
			if (field.startsWith(ScimResource.SCHEMA_EXT_PREFIX) || exts.contains(field)) {
				processExtension(type,field,node);
				continue;
			}
			//else process as attribute
			Attribute attr = coreSchema.getAttribute(field);
			
			//TODO: When flex-schema is enabled, use ValueUtil.getType to
			//create a virtual attribute to allow undefined attributes.
			//If an attribute is undefined, it is skipped.
			if (attr == null) continue;	
			
			processAttribute(this.coreAttrVals, attr, node,isReplace);
	
		}

	}
	
	public String checkEncodedAttribute(String attrname) {
		if (attrname.startsWith(SCHEMA_EXT_PREFIX))
			return new String(java.util.Base64.getDecoder().decode(attrname.substring(SCHEMA_EXT_PREFIX.length())));
					
		return attrname;
	}
	
	protected void processExtension(ResourceType type, String extensionId,JsonNode parent) throws SchemaException, ParseException, ConflictException {
		
		Schema eSchema = smgr.getSchemaById(extensionId);
		if (eSchema == null)
			return;  //ignore, unsupported core attribute or schema
		String sname = eSchema.getName();
		JsonNode extNode = parent.get(extensionId);
		if (extNode == null)
			extNode = parent.get(sname);
		if (extNode == null
				&& type.getSchemaExtensions().get(extensionId).required)
			throw new SchemaException("Missing required schema extension: "
					+ extensionId);
		if (extNode == null)
			return; // skip optional missing schema

		ExtensionValues ext = this.extAttrVals.get(extensionId);
		if (ext == null)
			ext = new ExtensionValues(eSchema, extNode,	this.idResolver);
		if (ext.getSize() > 0) {
			this.extAttrVals.put(extensionId, ext);
			this.attrsInUse.addAll(ext.getAttributeSet());
		}

	}

	/**
	 * Generate a JSON representation of the resource. No contextual security filtering is provided!
	 * 
	 * @return A String containing the JSON representation of the resource
	 */
	public String toJsonString()  {
		return toJsonNode(null).toString();
	}

	/**
	 * Returns a JSON node representation of the resource. Can be used in copying and other transformations.
	 * @param requestCtx IF provided, will cause request filtering to be provided based on the request context.
	 * @return A {@link JsonNode} representation of the SCIM resource.
	 */
	public JsonNode toJsonNode(RequestCtx requestCtx)  {
		ObjectNode node = JsonUtil.getMapper().createObjectNode();
		ArrayNode sarray = node.putArray("schemas");
		for (String scheme : schemas)
			sarray.add(scheme);

		// Write out the id and externalId
		if (this.id != null)
			node.put("id",id);

		if (this.externalId != null && ValueUtil.isReturnable(commonSchema,"externalId", requestCtx))
			node.put("externalId",externalId);

		// Write out the meta information
		// Meta will not be used for hash calculations.
		if (this.meta != null && ValueUtil.isReturnable(commonSchema,"meta", requestCtx)) {
			node.set("meta",meta.toJsonNode(requestCtx));
		}

		// Write out the core attribute values

		for (Attribute field : coreAttrVals.keySet()) {
			if (ValueUtil.isReturnable(field,requestCtx)) {
				Value val = this.coreAttrVals.get(field);
				val.toJsonNode(node, field.getName());
			}
		}

		// Write out the extensions...
		for (ExtensionValues ext : extAttrVals.values()) {
			if (ValueUtil.isReturnable(ext,requestCtx)) {
				String eName = ext.getSchemaName();
				node.set(eName,ext.toJsonNode());
			}
		}

		return node;

	}

	/**
	 * Calculate the MD5 digest hash of the resource based on its JSON
	 * representation.
	 * 
	 * @return An String usable as an ETag for versioning/matching purposes
	 * @throws ScimException Thrown when error occurs when serializing resource for hash generation.
	 */
	public String calcVersionHash() throws ScimException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baos));
		try {
			// We don't want to use pretty print formatting for the hash
			JsonGenerator gen = JsonUtil.getGenerator(writer, true);
			this.serialize(gen, null, true);
			gen.close();
			writer.close();

			// Now, generate the hash
			MessageDigest md = MessageDigest.getInstance("MD5");

			md.update(baos.toByteArray());
			byte[] hashBytes = md.digest();
			// convert byte array to hex
			StringBuilder sb = new StringBuilder();
			for (byte hashByte : hashBytes) {
				sb.append(Integer.toString((hashByte & 0xff) + 0x100, 16)
						.substring(1));
			}
			return sb.toString();

		} catch (IOException | NoSuchAlgorithmException e) {
			// SHOULD NEVER HAPPEN
			e.printStackTrace();
		}

		return null;
	}

	public boolean isNotBlocked(Attribute attr) {
		return !blockedAttrs.contains(attr);
	}

	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash)
			throws IOException, ScimException {

		gen.writeStartObject();

		// Write out the schemas value
		gen.writeArrayFieldStart("schemas");
		for (String schema : this.schemas)
			gen.writeString(schema);
		gen.writeEndArray();

		// Write out the id and externalId
		if (this.id != null)
			gen.writeStringField("id", this.id);

		
		if (this.externalId != null &&
				ValueUtil.isReturnable(commonSchema,"externalId", ctx)
				&& isNotBlocked(commonSchema.getAttribute("externalId")))

			gen.writeStringField("externalId", this.externalId);

		// Write out the meta information
		// Meta will not be used for hash calculations.
		if (this.meta != null && !forHash &&
				ValueUtil.isReturnable(commonSchema,"meta", ctx) &&
				isNotBlocked(commonSchema.getAttribute("meta"))) {
			gen.writeFieldName("meta");
			this.meta.serialize(gen, ctx, false);
		}
	
		// Write out the core attribute values

		for (Attribute attr: coreAttrVals.keySet()) {
			if(!ValueUtil.isReturnable(attr, ctx))
				continue;
			if(isNotBlocked(attr)) {
				Value val = this.coreAttrVals.get(attr);
				gen.writeFieldName(attr.getName());
				val.serialize(gen, ctx);
			}
		}

		for (ExtensionValues ext : this.extAttrVals.values()) {
			if (ValueUtil.isReturnable(ext, ctx)) {
				ext.setBlockedAttrs(blockedAttrs);
				ext.serialize(gen, ctx, forHash);
			}
		}

		// Write out the end of object for the resource
		gen.writeEndObject();

	}

	protected void processAttribute(LinkedHashMap<Attribute, Value> map,
			Attribute attr, JsonNode node, boolean isReplace) throws ConflictException, SchemaException,
			ParseException {
		
		JsonNode attrNode = node.get(attr.getName());
		Value val;

		if (attrNode != null) {
			attrsInUse.add(attr);
			if (isReplace || !attr.isMultiValued()) {
				val = ValueUtil.parseJson(attr, attrNode, this.idResolver);
				map.put(attr, val);
			} else {
				val = ValueUtil.parseJson(attr, attrNode, this.idResolver);
				MultiValue mval = (MultiValue) map.get(attr);
				// Initialize if this is a new value
				if (mval == null)
					mval = new MultiValue();
				
				if (val instanceof MultiValue) {
					// node was an array of values
					mval.addAll(((MultiValue)val).values());
					//mval.values.addAll(((MultiValue)refval1).values);
				} else
					// node was a single json object
					mval.addValue(val);
				map.put(attr, mval);
			}
		}

	}
	
	/**
	 * Returns the attribute definition that corresponds to an attribute within the 
	 * resource.
	 * @param name The name or full name path of the attribute
	 * @param ctx The request ctx (used to default the resoruce type)
	 * 
	 * @return The corresponding <code>Attribute</code> definition
	 */
	public Attribute getAttribute(String name,RequestCtx ctx) {
		return smgr.findAttribute(name, ctx);
	}

	private Value getCommonValue(Attribute attr) throws SchemaException {
		Attribute rootAttribute;
		if (attr.isChild())
			rootAttribute = attr.getParent();
		else
			rootAttribute = attr;

		if (rootAttribute.getName().equalsIgnoreCase("id"))
			return new StringValue(attr,getId());
		if (rootAttribute.getName().equalsIgnoreCase("externalId"))
			return new StringValue(attr,getExternalId());

		if (rootAttribute.getName().equalsIgnoreCase("schemas")) {
			List<Value> vals = new ArrayList<>();
			for (String uri : getSchemaURIs())
				vals.add(new StringValue(attr,uri));
			return new MultiValue(attr,vals);
		}

		if (rootAttribute.getName().equalsIgnoreCase("meta")) {
			if (attr.isChild())
				return this.getMeta().getValue(attr);
			return this.getMeta();

		}
		return null;
	}
	
	/**
	 * Return a value based on the attribute definition (which includes the
	 * attributes name, path, and schema). This procedure automatically checks
	 * core and extended attributes to locate the correct value.
	 * 
	 * @param attr
	 *            An attribute (e.g. from Schemas) definiton
	 * @return A <code>Value</code> representing the corresponding attribute
	 *         value if defined (or null).
	 */
	public Value getValue(Attribute attr) {
		//ResourceType type = cfg.getResourceType(getResourceType());
		String core = type.getSchema();

		Attribute rootAttribute;
		if (attr.isChild())
			rootAttribute = attr.getParent();
		else
			rootAttribute = attr;

		Value val = null;
		if (attr.getSchema() == null) {
			// This attribue is undefined. See if it is in core attrs
			return this.coreAttrVals.get(attr);
		}
		if (attr.getSchema().equals(core)) {
			val = this.coreAttrVals.get(rootAttribute);
		} else if (attr.getSchema().equals(ScimParams.SCHEMA_SCHEMA_Common)) {
			try {
				val = getCommonValue(attr);
				return val;
			} catch (SchemaException e) {
				logger.warn("Unexpected schema exception getting common schema value: " + attr.getName() + ": " + e.getLocalizedMessage(), e);
			}
		} else {
			ExtensionValues map = this.extAttrVals.get(attr.getSchema());
			if (map != null)
				val = map.getValue(rootAttribute.getName());
		}


		// If the value requested is a sub-attribute return the sub attribute. If multi-value, return the sub-attribute
		// as a Multi-Value of the simple attribute.
		if (attr.isChild()) {
			if (val instanceof ComplexValue) {
				ComplexValue cvalue = (ComplexValue) val;
				val = cvalue.getValue(attr.getName());
			}
			if (val instanceof MultiValue) {
				MultiValue mval = (MultiValue) val;
				List<Value> vals = new ArrayList<>();
				for (Value mvitem: mval.getRawValue()) {
					if (mvitem instanceof ComplexValue) {
						ComplexValue cvalue = (ComplexValue) mvitem;
						val = cvalue.getValue(attr.getName());
						vals.add(val);
					} else
						vals.add(mvitem);
				}
				try {
					return new MultiValue(attr,vals);
				} catch (SchemaException e) {
					logger.warn("Unexpected SchemaException converting array of values to MultiValue object: "+e.getLocalizedMessage(),e);
				}
			}
		}

		return val;

	}

	public String toString() {
		StringWriter writer = new StringWriter();
		try {
			JsonGenerator gen = JsonUtil.getGenerator(writer, false);
			this.serialize(gen, null, false);
			gen.close();
			writer.close();
			return writer.toString();
		} catch (IOException | ScimException e) {
			// Should not happen
			e.printStackTrace();
		}
		return super.toString();
	}
	
	public Set<Attribute> coreAttrSet() {
		return this.coreAttrVals.keySet();
	}
	
	public Set<String> extSchemaUrns() {
		return this.extAttrVals.keySet();
	}
	
	/**
	 * Returns the parsed ExtensionValues object based on the extension URI object requested.
	 * @param urnName The URI of the extension schema to be returned.
	 * @return An ExtensionValues object contained the parsed extension attributes for the URI provided.
	 */
	public ExtensionValues getExtensionValues(String urnName) {
		return this.extAttrVals.get(urnName);
	}
	
	public void modifyResource(JsonPatchRequest req, RequestCtx ctx) throws ScimException {
		
		Iterator<JsonPatchOp> iter = req.iterator();
		while (iter.hasNext()) {
			JsonPatchOp op = iter.next();
			
			// Is this a resource level patch?
			if (op.path == null || op.path.equals("")) {
				performResourcePatch(op,ctx);
				continue;
			}
			JsonPath jpath = new JsonPath(this,op,ctx);
			
			// Is this a multi-value patch?
			if (jpath.isMultiValue()) {
				performMultiValOp(op,jpath,ctx);
				continue;
			}
			// Now we have simple attribute manipulation
			Attribute tattr = jpath.getTargetAttribute();
			switch (op.op){
			case JsonPatchOp.OP_ACTION_ADD:
				try {
					Value val = ValueUtil.parseJson(tattr, op.value, null);
					this.addValue(val);
				} catch (SchemaException | ParseException e) {
					throw new InvalidValueException("JSON parsing error parsing value parameter.",e);
				}
				break;
			case JsonPatchOp.OP_ACTION_REMOVE:
				// TODO should we test for a specific value to remove???
				this.removeValue(tattr);
				break;
			case JsonPatchOp.OP_ACTION_REPLACE:
				Value val;
				try {
					val = ValueUtil.parseJson(tattr, op.value, null);
				} catch (SchemaException | ParseException e) {
					throw new InvalidValueException("JSON parsing error parsing value parameter.",e);
				}
				this.addValue(val);
				break;
			default:
				throw new InvalidValueException("The operation requested ("+op.op+") is not supported");
			}
		}
		
	}
	
	private void performMultiValOp(JsonPatchOp op, JsonPath path, RequestCtx ctx) throws ScimException {
		ScimResource target = this;
		
		MultiValue mval = (MultiValue) target.getValue(path.getTargetAttribute());
		Value val = mval.getMatchValue(path.getTargetValueFilter());
		
		
		if (val == null)
			throw new NoTargetException("No match found for the path filter.");
			
				switch (op.op){
				case JsonPatchOp.OP_ACTION_ADD:
					if (path.hasSubAttr() && !op.value.isObject()) {
						if (val instanceof ComplexValue) {
							ComplexValue cval = (ComplexValue) val;
							Attribute sattr = target.getAttribute(path.getTargetAttrName()+"."+path.getSubAttrName(), ctx);
							Value nval;
							try {
								nval = ValueUtil.parseJson(sattr, op.value, null);
							    if (nval instanceof BooleanValue) {
							    	BooleanValue bval = (BooleanValue) nval;
							    	if (bval.getRawValue() && sattr.getName().equals("primary"))
							    		mval.resetPrimary();
							    }

								//cval.vals.put(path.getSubAttrName(), nval);
								cval.addValue(sattr,nval);
							} catch (SchemaException | ParseException e) {
								throw new InvalidValueException("JSON parsing error parsing value parameter.",e);
							}	
							break;
						}
						
						// There was a sub attribute specified, but the parent does not support sub-attributes.
						// TODO what about simple "value" for mv attributes.
						throw new InvalidValueException("A sub-attribute was specified, but the value was a JSON object: "+op.path);
						
					} else if (op.value.isObject() && 
							path.getTargetAttribute().getType()
							.equalsIgnoreCase(Attribute.TYPE_Complex)) {
						try {
							Value nval = ValueUtil.parseJson(path.getTargetAttribute(), op.value, null);
							if (val instanceof ComplexValue) {
								ComplexValue cval = (ComplexValue) val;
						
								if (nval instanceof ComplexValue) {
									if (((ComplexValue) nval).isPrimary()) {
										mval.resetPrimary();
									}
									cval.mergeValues((ComplexValue)nval);
								} else {
									cval.addValue(path.getTargetAttribute(),nval);
								}
							} else
								throw new ScimException("Unknown error. Expecting ComplexValue, got "+val.getClass().getCanonicalName());
							
							break;
							
						} catch (SchemaException | ParseException e) {
							throw new InvalidSyntaxException("Unable to parse value parameter",e);
						}
					}
					break;
				case JsonPatchOp.OP_ACTION_REMOVE:
					if (path.hasSubAttr()) {
						if (val instanceof ComplexValue) {
							ComplexValue cval = (ComplexValue) val;
							//TODO do we care if the attribute didn't exist? Probably not
							cval.removeValue(path.getSubAttribute());
							break;
						}
						// There was a sub attribute specified, but the parent does not support sub-attributes.
						// TODO what about simple "value" for mv attributes.
						throw new InvalidValueException("A sub-attribute was specified for a parent attribute that is not complex: "+op.path);	
					}
					// No sub-attribute specified, remove the entire value
					mval.removeValue(val);
					
					break;
					
				case JsonPatchOp.OP_ACTION_REPLACE:
					if (path.hasSubAttr() && !op.value.isObject()) {
						if (val instanceof ComplexValue) {
							ComplexValue cval = (ComplexValue) val;
							//Attribute sattr = target.getAttribute(path.getTargetAttrName()+"."+path.getSubAttrName(), ctx);
							Attribute sattr = path.getSubAttribute();
							Value nval;
							try {
								nval = ValueUtil.parseJson(sattr, op.value, null);
								if (nval instanceof ComplexValue) {
									cval.replaceValues((ComplexValue)nval);
								} else {
									// TODO may need to check if sub attribute is multi-valued
									cval.addValue(sattr,nval);
								}

							} catch (SchemaException | ParseException e) {
								throw new InvalidValueException("JSON parsing error parsing value parameter.",e);
							}	
							break;
						}
						
						// There was a sub attribute specified, but the parent does not support sub-attributes.
						// TODO what about simple "value" for mv attributes.
						throw new InvalidValueException("A sub-attribute was specified, but the value was a JSON object: "+op.path);
						
					} else if (op.value.isObject() && 
							path.getTargetAttribute().getType()
							.equalsIgnoreCase(Attribute.TYPE_Complex)) {
						try {
							Value cval = ValueUtil.parseJson(path.getTargetAttribute(), op.value, null);
							mval.removeValue(val);// remove the current value
							mval.addValue(cval);
							break;
						} catch (SchemaException | ParseException e) {
							throw new InvalidSyntaxException("Unable to parse value parameter",e);
						}
					}
					break;					
				}

		
			
	}
	
	private void performResourcePatch(JsonPatchOp op, RequestCtx ctx) throws ScimException {
		ScimResource target = this;
		if (op.op.equalsIgnoreCase(JsonPatchOp.OP_ACTION_ADD)
				|| op.op.equalsIgnoreCase(JsonPatchOp.OP_ACTION_REPLACE)) {
			try {
				target.parseAttributes(op.value,
						(op.op.equalsIgnoreCase(JsonPatchOp.OP_ACTION_REPLACE)), false);
				return;
			} catch (SchemaException | ParseException e) {
				throw new InvalidSyntaxException("Unable to parse value.", e);
			}
		}

		throw new InvalidValueException(
				"Invalid operation in combination with an empty path.");
	}
	
	@Override
	public boolean replaceResAttributes(ScimResource res, RequestCtx ctx) {
		
		//removeReadWriteAttributes(ctx);
		
		copyAttributesFrom(res,ctx);
		
		return true;
	}
	
	private void copyAttributesFrom(ScimResource res, RequestCtx ctx)  {
		if (res.getExternalId() != null)
			this.setExternalId(res.getExternalId());
		
		
		//Copy core read-write vals
		for (Attribute attr : res.coreAttrSet()) {

			String mutability = attr.getMutability();
			
			if (mutability.equals(Attribute.MUTABILITY_readWrite)
					|| mutability.equals(Attribute.MUTABILITY_writeOnly)) {
				//this.coreAttrs.remove(aname);
				this.coreAttrVals.put(attr, res.getValue(attr));
			}
		}
		
		//Copy extension vals
		for (String eschema : res.extSchemaUrns()) {
			ExtensionValues eattrs = res.getExtensionValues(eschema);
			copyExtensionAttributes(eschema, eattrs,ctx);
		}
	}
	
	private void copyExtensionAttributes(String schema, ExtensionValues eattrs, RequestCtx ctx)  {
		
		ExtensionValues localExt = this.getExtensionValues(schema);
		for (Attribute attr : eattrs.getAttributeSet()) {
			String mutability = attr.getMutability();
			if (mutability.equals(Attribute.MUTABILITY_readWrite)
					|| mutability.equals(Attribute.MUTABILITY_writeOnly)) {
				try {
					localExt.putValue(attr, eattrs.getValue(attr));
				} catch (SchemaException e) {
					//Since we are copying from an existing resource. This should not happen.
					logger.error("Unexpected error occurred copying extension attribute: "+e.getLocalizedMessage(),e);
				}
			}
		}
	}
	
	/**
	 * In preparation for a PUT, remove all the attributes that will be 
	 * replaced (which are readWrite attributes).
	 */
	@SuppressWarnings("unused")
	private void removeReadWriteAttributes(RequestCtx ctx) {
		//ResourceType type = cfg.getResourceType(getResourceType());
		String coreSchemaId = type.getSchema();
		
		//Remove core read-write vals
		Iterator<Attribute> iter = this.coreAttrVals.keySet().iterator();
		while (iter.hasNext()) {
			Attribute attr = iter.next();
			String aname = attr.getName();
			
			// Meta should never be removed (regardless of server setting)
			if (aname.equals("meta")) 
				continue;
			
			//Attribute attr = smgr.findAttribute(coreSchemaId, aname, null, ctx);
			if (attr.getMutability().equals(Attribute.MUTABILITY_readWrite))
				iter.remove();
		}
		
		//Remove extension read-write vals
		Iterator<String> siter = this.extAttrVals.keySet().iterator();
		while (siter.hasNext()) {
			String eschemaId = siter.next();
			ExtensionValues eattrs = this.extAttrVals.get(eschemaId);
			Schema  eSchema = eattrs.getSchema();
			//
			eattrs.getAttributeSet()
					.removeIf(attr ->
							attr.getMutability().equals(Attribute.MUTABILITY_readWrite));
			// If no more extension attributes, remove the extension schema
			if (eattrs.getSize() == 0)
				siter.remove();
		}
	}

	@Override
	public boolean isModified() {
		return this.modified;
	}

	@Override
	public boolean checkPreCondition(RequestCtx ctx) throws PreconditionFailException {
		
		//If inbound request context has no etag header, then no pre-condition.
		if (ctx == null || ctx.getVersion() == null)
			return true;
		// TODO Implement match pre-condition processing
		@SuppressWarnings("unused")
		String imatch = ctx.getIfMatch();
		@SuppressWarnings("unused")
		String nmatch = ctx.getIfNoneMatch();
		
		String curVersion;
		try {
			curVersion = this.calcVersionHash();
		} catch (ScimException e) {
			throw new PreconditionFailException("Failed to calculate current version: "+e.getMessage(),e);
		}
		String etag = ctx.getVersion();
		return etag.equals(curVersion);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#hasBulkIds()
	 */
	@Override
	public boolean hasBulkIds() {
		for (Value attribute : coreAttrVals.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				if (bAttr.hasBulkIds())
					return true;
			}
		}

		for (ExtensionValues eattrs : extAttrVals.values()) {
			if (eattrs.hasBulkIds())
				return true;
		}
		
		
		return false;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getBulkIdsRequired(java.util.List)
	 */
	@Override
	public void getBulkIdsRequired(List<String> bulkList) {
		for (Value attribute : coreAttrVals.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getBulkIdsRequired(bulkList);
			}
		}

		for (ExtensionValues eattrs : extAttrVals.values()) {
			eattrs.getBulkIdsRequired(bulkList);
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getAttributesWithBulkIdValues(java.util.List)
	 */
	@Override
	public void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs) {
		for (Value attribute : coreAttrVals.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getAttributesWithBulkIdValues(bulkIdAttrs);
			}
		}

		for (ExtensionValues eattrs : extAttrVals.values()) {
			eattrs.getAttributesWithBulkIdValues(bulkIdAttrs);
		}

		
	}

	/**
	 * Copies the current ScimResource and returns a new ScimResource object. Include a requestctx to do attribute filtering.
	 * @param requestCtx A RequestCtx object which may be used to filter included/excluded attributes and security filtering.
	 * @return a New ScimResource based on a JsonNode copied result.
	 */
	public ScimResource copy(RequestCtx requestCtx) throws ScimException, ParseException {
		JsonNode node = toJsonNode(requestCtx);
		return new ScimResource(smgr,node,this.getResourceType());

	}

	/**
	 * This method often used by ACIs to evaluate whether the current resource attributes are authorized.
	 * @return A set of Attribute definitions that are present in the current resource. Includes core and extension attributes.
	 */
	public Set<Attribute> getAttributesPresent() {
		return attrsInUse;
	}

	/**
	 * @param attr The Attribute to be tested
	 * @return true if the resource contains the specified attribute.
	 */
	public boolean isAttributePresent(Attribute attr) {
		return attrsInUse.contains(attr);
	}

	public void blockAttrSet(Set<Attribute> attrs) {
		this.blockedAttrs.addAll(attrs);
	}

	public void blockAttribute(Attribute attr) {
		this.blockedAttrs.add(attr);
	}
}
