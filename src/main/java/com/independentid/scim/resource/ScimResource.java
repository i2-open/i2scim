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
import com.independentid.scim.backend.IResourceModifier;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.*;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.*;
import com.independentid.scim.schema.*;
import com.independentid.scim.serializer.JsonUtil;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;

public class ScimResource implements IResourceModifier, IBulkIdTarget {

	public final static String SCHEMA_EXT_PREFIX = "Ext-";
	
	protected ConfigMgr cfg;

	protected String id;

	protected String externalId;

	protected List<String> schemas;
	public Schema commonSchema;
	protected Schema coreSchema;
	protected ResourceType type;

	// Holds the meta data about the resource
	
	protected Meta meta;

	protected LinkedHashMap<String, Value> coreAttrs;

	protected LinkedHashMap<String, ExtensionValues> extAttrs;
		
	protected boolean modified;
	
	protected IBulkIdResolver idResolver;

	/**
	 * Construct the resource based on JsonNode
	 * @param container TODO
	 * 
	 * @throws SchemaException
	 * @throws ParseException
	 * @throws ScimException  
	 */
	public ScimResource(ConfigMgr cfg, JsonNode resourceNode, String container)
			throws SchemaException, ParseException, ScimException {
				this(cfg, resourceNode, null, container);
		
	}

	protected ScimResource () {
		this.coreAttrs = new LinkedHashMap<>();
		this.extAttrs = new LinkedHashMap<>();
		this.idResolver = null;
		this.modified = false;
	}
	
	
	/**
	 * Creates a ScimResource based on a JsonNode structure
	 * @param cfg The server <ConfigMgr> instance container server schema
	 * @param resourceNode A <JsonNode> object containing a SCIM JSON parsed object
	 * @param bulkIdResolver An <IBulkIdResulver> used to resolve identifiers during bulk operations
	 * @param container A <String> identifying the resource container where the object is from or to be stored (e.g. Users, Groups). Used to lookup <ResourceType> and <Schema>
	 * @throws SchemaException
	 * @throws ParseException
	 * @throws ScimException
	 */
	public ScimResource(ConfigMgr cfg, JsonNode resourceNode, IBulkIdResolver bulkIdResolver, String container)
			throws SchemaException, ParseException, ScimException {
		
		this.cfg = cfg;
		this.coreAttrs = new LinkedHashMap<>();
		this.extAttrs = new LinkedHashMap<>();
		this.commonSchema = cfg.getSchemaById(ScimParams.SCHEMA_SCHEMA_Common);
		setResourceType(container);
		this.idResolver = bulkIdResolver;
		this.parseJson(cfg, resourceNode);
		this.modified = false;
		
	}
	
	public String getResourceType() {
		if (this.type != null)
			return this.type.getName();
		if (this.meta == null) {
			// Added because serviceproviderconfig does not necessarily have a meta object
			if (this.schemas.contains(ScimParams.SCHEMA_SCHEMA_ServiceProviderConfig))
				return ScimParams.TYPE_SERV_PROV_CFG;
			return null;
		}
		return this.meta.getResourceType();
	}
	
	/**
	 * @param type The String resource type of the resource (e.g. User, Group)
	 */
	public void setResourceType(String container) {
		this.type = cfg.getResourceTypeByPath(container);
		if (this.type != null)
			this.coreSchema = cfg.getSchemaById(this.type.getSchema());
	
	}
	
	public void setResourceType(RequestCtx ctx) {
		if (ctx != null) {
			String container = ctx.getResourceContainer();
			setResourceType(container);
		}
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
	
	public Map<String,Value> getCoreAttrs() {
		return this.coreAttrs;
	}
	
	public Map<String,ExtensionValues> getExtensions() {
		return this.extAttrs;
	}

	public void addValue(Attribute attr, Value addval) {
		//ResourceType type = cfg.getResourceType(getResourceType());
		//String core = type.getSchema();

		Attribute rootAttribute;
		if (attr.isChild())
			rootAttribute = attr.getParent();
		else
			rootAttribute = attr;

		if (attr.getSchema().equals(coreSchema.getId())) {
			if (attr.isChild()) {
				Value rval = this.getValue(rootAttribute);
				// If parent was undefined, add it.
				if (rval == null) {
					rval = new ComplexValue();
					this.coreAttrs.put(rootAttribute.getName(), rval);
					
				}
				if (rval instanceof ComplexValue) { 
					// Add the sub attribute value to the parent
					ComplexValue cval = (ComplexValue) rval;
					cval.addValue(attr.getName(), addval);
					return;
				}
			}
			this.coreAttrs.put(attr.getName(), addval);
			return;
			
		}
		
		// The attribute is an extension attribute
		ExtensionValues map = this.extAttrs.get(attr.getSchema());
		//refval1 = map.getAttribute(rootAttribute.getName());
		
		if (attr.isChild()) {
			Value rval = map.getValue(rootAttribute.getName());
			// If parent was undefined, add it.
			if (rval == null) {
				rval = new ComplexValue();
				map.putValue(rootAttribute.getName(), rval);
				
			}
			if (rval instanceof ComplexValue) { 
				// Add the sub attribute value to the parent
				ComplexValue cval = (ComplexValue) rval;
				cval.addValue(attr.getName(), addval);
				return;
			}
		}
		
		// Not a complex attribute, just add it.
		map.putValue(attr.getName(), addval);
		return;
		
	}
	
	public void removeValue(Attribute attr) {
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
					cval.vals.remove(attr.getName());
					// if the parent has no sub-attributes left, remove the parent
					if (cval.vals.size() == 0)
						this.coreAttrs.remove(rootAttribute.getName());
					return;
				}
			}
			// remove the simple core attribute
			this.coreAttrs.remove(attr.getName());
			return;
			
		}
		
		// The attribute is an extension attribute
		ExtensionValues map = this.extAttrs.get(attr.getSchema());
		//refval1 = map.getAttribute(rootAttribute.getName());
		
		if (attr.isChild()) {
			Value rval = map.getValue(rootAttribute.getName());
			// If parent was undefined, add it.
			if (rval == null) {
				return;
				
			}
			if (rval instanceof ComplexValue) { 
				// Add the sub attribute value to the parent
				ComplexValue cval = (ComplexValue) rval;
				cval.vals.remove(attr.getName());
				// if the parent has no sub-attributes left, remove the parent
				if (cval.vals.size() == 0)
					map.removeValue(rootAttribute.getName());
				return;
			}
		}
		
		// Not a complex attribute, just remove it.
		map.removeValue(attr.getName());
		
		
	}
	
	/**
	 * @return The <Schema> for the main content of the <ScimResource>.
	 */
	public Schema getBodySchema() {
		return this.coreSchema;
	}
	
	public void parseJson(ConfigMgr cfg, JsonNode node) throws SchemaException,
			ParseException, ScimException {

		JsonNode snode = node.get("schemas");
		if (snode == null) throw new SchemaException("Schemas attribute missing");
		else {
			Iterator<JsonNode> iter = snode.elements();
			this.schemas = new ArrayList<>();
			while (iter.hasNext()) {
				JsonNode anode = iter.next();
				this.schemas.add(anode.asText());
			}

		}

		JsonNode item = node.get("id");
		if (item != null)
			this.id = item.asText();

		JsonNode meta = node.get("meta");
		//Note: a SCIM schema or ResourceType might not have a meta
		if (meta != null) {
			this.meta = new Meta(meta);
			if (this.type == null) {
				this.type = cfg.getResourceType(this.meta.getResourceType());
				this.coreSchema = cfg.getSchemaById(this.type.getSchema());
			}
		} else
			this.meta = new Meta();
		
		// We will override meta resource type based on where this object is written (driven by type)
		this.meta.setResourceType(this.getResourceType());
		
		// TODO Write validate method to check schemas attr against resource
		// endpoint
		
		item = node.get("externalId");
		if (item != null)
			this.externalId = item.asText();

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
			
			processAttribute(this.coreAttrs, attr, node,isReplace);
	
		}

	}
	
	public String checkEncodedAttribute(String attrname) {
		if (attrname.startsWith(SCHEMA_EXT_PREFIX))
			return new String(java.util.Base64.getDecoder().decode(attrname.substring(SCHEMA_EXT_PREFIX.length())));
					
		return attrname;
	}
	
	protected void processExtension(ResourceType type, String extensionId,JsonNode parent) throws SchemaException, ParseException, ConflictException {
		
		Schema eSchema = cfg.getSchemaById(extensionId);
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

		ExtensionValues ext = this.extAttrs.get(extensionId);
		if (ext == null)
			ext = new ExtensionValues(eSchema, extNode,	this.idResolver);
		if (ext.getSize() > 0)
			this.extAttrs.put(extensionId, ext);
	}

	/**
	 * Generate a JSON representation of the resource
	 * 
	 * @return A String containing the JSON representation of the resource
	 * @throws ScimException 
	 */
	public String toJsonString() throws ScimException {
		StringWriter writer = new StringWriter();
		try {
			JsonGenerator gen = JsonUtil.getGenerator(writer, true);
			this.serialize(gen, null, false);
			gen.close();
			writer.close();
			return writer.toString();
		} catch (IOException e) {
			// Should not happen
			e.printStackTrace();
		}
		return super.toString();
	}
	
	public JsonNode toJsonNode() throws ScimException {
		StringWriter writer = new StringWriter();
		try {
			JsonGenerator gen = JsonUtil.getGenerator(writer, true);
			this.serialize(gen, null, false);
			gen.close();
			writer.close();
			return JsonUtil.getJsonTree(writer.toString());
		} catch (IOException e) {
			// Should not happen
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Calculate the MD5 digest hash of the resource based on its JSON
	 * representation.
	 * 
	 * @return An String usable as an ETag for versioning/matching purposes
	 * @throws ScimException 
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

	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash)
			throws IOException, ScimException {
		gen.writeStartObject();

		// Write out the schemas value
		gen.writeArrayFieldStart("schemas");
		Iterator<String> iter = this.schemas.iterator();
		while (iter.hasNext())
			gen.writeString(iter.next());
		gen.writeEndArray();

		// Write out the id and externalId
		if (this.id != null)
			gen.writeStringField("id", this.id);

		
		if (this.externalId != null && ValueUtil.isReturnable(commonSchema,"externalId", ctx))
			gen.writeStringField("externalId", this.externalId);

		// Write out the meta information
		// Meta will not be used for hash calculations.
		if (this.meta != null && !forHash && ValueUtil.isReturnable(commonSchema,"meta", ctx)) {
			gen.writeFieldName("meta");
			this.meta.serialize(gen, ctx, forHash);
		}
	
		// Write out the core attribute values
		iter = this.coreAttrs.keySet().iterator();  
		while (iter.hasNext()) {
			String field = iter.next();
			if(!ValueUtil.isReturnable(getBodySchema(),field, ctx))
				continue;
			Value val = this.coreAttrs.get(field);
			gen.writeFieldName(field);
			val.serialize(gen, ctx);
		}

		for (ExtensionValues ext : this.extAttrs.values()) {
			if (ValueUtil.isReturnable(ext, ctx))
				ext.serialize(gen, ctx, forHash);
		}

		// Write out the end of object for the resource
		gen.writeEndObject();

	}

	protected void processAttribute(LinkedHashMap<String, Value> map,
			Attribute attr, JsonNode node, boolean isReplace) throws ConflictException, SchemaException,
			ParseException {
		
		JsonNode attrNode = node.get(attr.getName());
		Value val;
		if (attrNode != null) {
			if (isReplace || !attr.isMultiValued()) {
				val = ValueUtil.parseJson(attr, attrNode, this.idResolver);
				map.put(attr.getName(), val);
			} else {
				val = ValueUtil.parseJson(attr, attrNode, this.idResolver);
				MultiValue mval = (MultiValue) map.get(attr.getName());
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
				map.put(attr.getName(), mval);
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
		return cfg.findAttribute(name, ctx);
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

		Value val;
		if (attr.getSchema().equals(core)) {
			val = this.coreAttrs.get(rootAttribute.getName());
		} else {
			ExtensionValues map = this.extAttrs.get(attr.getSchema());
			val = map.getValue(rootAttribute.getName());
		}

		// TODO Need to check for multiple levels of complex attributes.
		if (rootAttribute != attr) {
			if (val instanceof ComplexValue) {
				ComplexValue cvalue = (ComplexValue) val;
				val = cvalue.getValue(attr.getName());
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

	/**
	 * @param ctx
	 *            Based on the RequestCtx (which contains the requested and
	 *            excluded attributes) and attribute returnability, filterAttributes removes attributes not
	 *            requested.
	 */
	/*  This is now handled in the serialize code for objects.z
	public void filterAttributes(RequestCtx ctx) {
		List<String> attrsReq = ctx.getAttrNamesReq();
		List<String> exclAttrs = ctx.getExcludedAttrNames();
		if (attrsReq == null) {
			Iterator<String> iter = this.coreAttrs.keySet().iterator();
			while (iter.hasNext()) {
				String aname = iter.next();
				Attribute attr = this.cfg.findAttribute(aname, ctx);
				if (ctx.isAttrRequested(attr))
					continue;
				if (attr.getReturned().equals(Attribute.RETURNED_default))
					continue;
				
				// Attribute is not returned by default or is never returned. Remove it.
				iter.remove();
			}
		} else {
			HashSet<String> map = new HashSet<String>(attrsReq);
			Iterator<String> iter = this.coreAttrs.keySet().iterator();
			while (iter.hasNext()) {
				String aname = iter.next();
				Attribute attr = this.cfg.findAttribute(aname, ctx);
				if (attr.getReturned().equals(Attribute.RETURNED_always))
					continue;
				if (attr.getReturned().equals(Attribute.RETURNED_never)) {
					iter.remove();
					continue;
				}
				if (map.contains(aname) || map.contains(attr.getRelativePath()))
					continue;
				iter.remove();
				
			}
		}
		
		if (exclAttrs != null) {
			for (int i=0; i < exclAttrs.size(); i++) {
				String aname = exclAttrs.get(i);
				if (this.coreAttrs.containsKey(aname)) {
					Attribute attr = this.cfg.findAttribute(aname, ctx);
					if (attr != null && !attr.getReturned().equals(Attribute.RETURNED_always))
						this.coreAttrs.remove(aname);
				}
			}
		}
	}
	*/
	
	public Set<String> coreAttrNameSet() {
		return this.coreAttrs.keySet();
	}
	
	public Set<String> extSchemaUrns() {
		return this.extAttrs.keySet();
	}
	
	/**
	 * Returns the parsed ExtensionValues object based on the extension URI object requested.
	 * @param urnName The URI of the extension schema to be returned.
	 * @return An ExtensionValues object contained the parsed extension attributes for the URI provided.
	 */
	public ExtensionValues getExtensionValues(String urnName) {
		return this.extAttrs.get(urnName);
	}
	
	public void modifyResource(JsonPatchRequest req, RequestCtx ctx) throws ScimException {
		
		Iterator<JsonPatchOp> iter = req.iterator();
		while (iter.hasNext()) {
			JsonPatchOp op = iter.next();
			
			// Is this a resource level patch?
			if (op.path == null || op.path.equals("")) {
				performResourcePatch(op,ctx);
				return;
			}
			JsonPath jpath = new JsonPath(this,op,ctx);
			
			// Is this a multi-value patch?
			if (jpath.isMultiValue()) {
				performMultiValOp(op,jpath,ctx);
				return;
			}
			// Now we have simple attribute manipulation
			Attribute tattr = jpath.getTargetAttribute();
			switch (op.op){
			case JsonPatchOp.OP_ADD:
				try {
					Value val = ValueUtil.parseJson(tattr, op.value, null);
					this.addValue(tattr, val);
				} catch (SchemaException | ParseException e) {
					throw new InvalidValueException("JSON parsing error parsing value parameter.",e);
				}
				break;
			case JsonPatchOp.OP_REMOVE:
				// TODO should we test for a specific value to remove???
				this.removeValue(tattr);
				break;
			case JsonPatchOp.OP_REPLACE:
				Value val;
				try {
					val = ValueUtil.parseJson(tattr, op.value, null);
				} catch (SchemaException | ParseException e) {
					throw new InvalidValueException("JSON parsing error parsing value parameter.",e);
				}
				this.addValue(tattr, val);
				break;
			default:
				throw new InvalidValueException("The operation requested ("+op.op+") is not supported");
			}
		}
		return;	
		
	}
	
	private void performMultiValOp(JsonPatchOp op, JsonPath path, RequestCtx ctx) throws ScimException {
		ScimResource target = this;
		
		MultiValue mval = (MultiValue) target.getValue(path.getTargetAttribute());
		Value val = mval.getMatchValue(path.getTargetValueFilter());
		
		
		if (val == null)
			throw new NoTargetException("No match found for the path filter.");
			
				switch (op.op){
				case JsonPatchOp.OP_ADD:
					if (path.hasSubAttr() && !op.value.isObject()) {
						if (val instanceof ComplexValue) {
							ComplexValue cval = (ComplexValue) val;
							Attribute sattr = target.getAttribute(path.getTargetAttrName()+"."+path.getSubAttrName(), ctx);
							Value nval;
							try {
								nval = ValueUtil.parseJson(sattr, op.value, null);
							    if (nval instanceof BooleanValue) {
							    	BooleanValue bval = (BooleanValue) nval;
							    	if (bval.getValueArray() && sattr.getName().equals("primary"))
							    		mval.resetPrimary();
							    }
									
								cval.vals.put(path.getSubAttrName(), nval);
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
									cval.addValue(path.getTargetAttrName(),nval);
								}
							} else
								throw new ScimException("Unknown error. Expecting ComplexValue, got "+val.getClass().getCanonicalName());
							
							break;
							
						} catch (SchemaException | ParseException e) {
							throw new InvalidSyntaxException("Unable to parse value parameter",e);
						}
					}
					break;
				case JsonPatchOp.OP_REMOVE:
					if (path.hasSubAttr()) {
						if (val instanceof ComplexValue) {
							ComplexValue cval = (ComplexValue) val;
							//TODO do we care if the attribute didn't exist? Probably not
							cval.vals.remove(path.getSubAttrName());
							break;
						}
						// There was a sub attribute specified, but the parent does not support sub-attributes.
						// TODO what about simple "value" for mv attributes.
						throw new InvalidValueException("A sub-attribute was specified for a parent attribute that is not complex: "+op.path);	
					}
					// No sub-attribute specified, remove the entire value
					mval.removeValue(val);
					
					break;
					
				case JsonPatchOp.OP_REPLACE:
					if (path.hasSubAttr() && !op.value.isObject()) {
						if (val instanceof ComplexValue) {
							ComplexValue cval = (ComplexValue) val;
							Attribute sattr = target.getAttribute(path.getTargetAttrName()+"."+path.getSubAttrName(), ctx);
							Value nval;
							try {
								nval = ValueUtil.parseJson(sattr, op.value, null);
								if (nval instanceof ComplexValue) {
									cval.replaceValues((ComplexValue)nval);
								} else {
									// TODO may need to check if sub attribute is multi-valued
									cval.addValue(path.getTargetAttrName(),nval);
								}
								//cval.attrs.remove(path.getTargetAttrName());
								//cval.attrs.put(path.getSubAttrName(), nval);
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
		if (op.op.equalsIgnoreCase(JsonPatchOp.OP_ADD)
				|| op.op.equalsIgnoreCase(JsonPatchOp.OP_REPLACE)) {
			try {
				target.parseAttributes(op.value,
						(op.op.equalsIgnoreCase(JsonPatchOp.OP_REPLACE)), false);
				return;
			} catch (SchemaException | ParseException e) {
				throw new InvalidSyntaxException("Unable to parse value.", e);
			}
		}

		throw new InvalidValueException(
				"Invalid operation in combination with an empty path.");
	}
	
	@Override
	public boolean replaceResAttributes(ScimResource res, RequestCtx ctx)
			throws ScimException {
		
		//removeReadWriteAttributes(ctx);
		
		copyAttributesFrom(res,ctx);
		
		return true;
	}
	
	private void copyAttributesFrom(ScimResource res, RequestCtx ctx) {
		//ResourceType type = cfg.getResourceType(getResourceType());
		//String coreSchemaId = type.getSchema();
		
		//Attribute attr = this.cfg.findAttribute("externalId", ctx);
		if (res.getExternalId() != null)
			this.setExternalId(res.getExternalId());
		
		
		//Copy core read-write vals
		Iterator<String> iter = res.coreAttrNameSet().iterator();
		while (iter.hasNext()) {
			String aname = iter.next();
			
			//Attribute attr = cfg.findAttribute(coreSchemaId, aname, null, ctx);
			Attribute attr = coreSchema.getAttribute(aname);
			String mutability = attr.getMutability();
			
			if (mutability.equals(Attribute.MUTABILITY_readWrite)
					|| mutability.equals(Attribute.MUTABILITY_writeOnly)) {
				//this.coreAttrs.remove(aname);
				this.coreAttrs.put(aname, res.getValue(attr));
			}
		}
		
		//Copy extension vals
		iter = res.extSchemaUrns().iterator();
		while (iter.hasNext()) {
			String eschema = iter.next();
			ExtensionValues eattrs = res.getExtensionValues(eschema);
			copyExtensionAttributes(eschema, eattrs,ctx);
		}
	}
	
	private void copyExtensionAttributes(String schema, ExtensionValues eattrs, RequestCtx ctx) {
		
		ExtensionValues localExt = this.getExtensionValues(schema);
		for (String aname : eattrs.attrNameSet()) {
			Attribute attr = cfg.findAttribute(schema, aname, null, ctx);
			String mutability = attr.getMutability();
			if (mutability.equals(Attribute.MUTABILITY_readWrite)
					|| mutability.equals(Attribute.MUTABILITY_writeOnly))
				localExt.putValue(aname, eattrs.getValue(aname));
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
		Iterator<String> iter = this.coreAttrs.keySet().iterator();
		while (iter.hasNext()) {
			String aname = iter.next();
			
			// Meta should never be removed (regardless of server setting)
			if (aname.equals("meta")) 
				continue;
			
			Attribute attr = cfg.findAttribute(coreSchemaId, aname, null, ctx);
			if (attr.getMutability().equals(Attribute.MUTABILITY_readWrite))
				iter.remove();
		}
		
		//Remove extension read-write vals
		iter = this.extAttrs.keySet().iterator();
		while (iter.hasNext()) {
			String eschema = iter.next();
			ExtensionValues eattrs = this.extAttrs.get(eschema);
			Iterator<String> eiter = eattrs.attrNameSet().iterator();
			while (eiter.hasNext()) {
				String aname = eiter.next();
				Attribute attr = cfg.findAttribute(eschema, aname, null, ctx);
				if (attr.getMutability().equals(Attribute.MUTABILITY_readWrite))
					eiter.remove();
			}
			// If no more extension attributes, remove the extension schema
			if (eattrs.getSize() == 0)
				iter.remove();
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
		for (Value attribute : coreAttrs.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				if (bAttr.hasBulkIds())
					return true;
			}
		}

		for (ExtensionValues eattrs : extAttrs.values()) {
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
		for (Value attribute : coreAttrs.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getBulkIdsRequired(bulkList);
			}
		}

		for (ExtensionValues eattrs : extAttrs.values()) {
			eattrs.getBulkIdsRequired(bulkList);
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getAttributesWithBulkIdValues(java.util.List)
	 */
	@Override
	public void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs) {
		for (Value attribute : coreAttrs.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getAttributesWithBulkIdValues(bulkIdAttrs);
			}
		}

		for (ExtensionValues eattrs : extAttrs.values()) {
			eattrs.getAttributesWithBulkIdValues(bulkIdAttrs);
		}

		
	}


}
