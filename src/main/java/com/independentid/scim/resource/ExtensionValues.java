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
package com.independentid.scim.resource;

import java.io.IOException;
import java.text.ParseException;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.ScimSerializer;
import com.independentid.scim.server.ConflictException;
import com.independentid.scim.server.ScimException;

public class ExtensionValues implements ScimSerializer, IBulkIdTarget {

	private Schema eSchema;
	//private ConfigMgr cfg;
	private String sname;
	private IBulkIdResolver resolver;
	
	private LinkedHashMap<String,Value> attrs = new LinkedHashMap<String, Value>();
	
	/**
	 * Creates a value container for Extension Schema Attributes
	 * @param extensionSchema A Schema object containing the definitions for the Extension Schema
	 * @param extNode A JsonNode object holding the extension attributes
	 * @param bulkIdResolver A resolver used to resolve temporary IDs in bulkoperations
	 * @throws ConflictException
	 * @throws SchemaException
	 * @throws ParseException
	 */
	public ExtensionValues(Schema extensionSchema, JsonNode extNode, IBulkIdResolver bulkIdResolver) throws ConflictException, SchemaException, ParseException {
		this.sname = extensionSchema.getId();
		this.eSchema = extensionSchema;
		this.resolver = bulkIdResolver;
		parseJson(extNode);
		
	}
	
	public ExtensionValues(Schema extensionSchema, Map<String,Value> valMap) throws ConflictException, SchemaException, ParseException {
		this.sname = extensionSchema.getId();
		this.eSchema = extensionSchema;
		this.resolver = null;
		this.attrs.putAll(valMap);
		
	}

	
	public void parseJson(JsonNode node) throws ConflictException, SchemaException, ParseException {
				
		//this.eSchema = this.cfg.getSchemaByName(this.sname);
		
		Attribute[] attrs = this.eSchema.getAttributes();
    	for (int i=0; i<attrs.length; i++) {
    		processAttribute(attrs[i],node);
    	}

	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
				serialize(gen, ctx, false);
			}


	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx,
			boolean forHash) throws IOException, ScimException {
		
		if (getSize() == 0)
			return;
		
		// SchemaAlias is used when persistence provider (e.g. Mongo) can't handle urns
		if (ctx != null && ctx.useEncodedExtensions())
			gen.writeFieldName(ScimResource.SCHEMA_EXT_PREFIX+Base64.getEncoder().encodeToString(eSchema.getId().getBytes()));
		else 
			gen.writeFieldName(eSchema.getId());
		gen.writeStartObject();
		
		for (String field : attrs.keySet()) {
			Attribute attr = eSchema.getAttribute(field);
			if (ValueUtil.isReturnable(attr, ctx)) {
				gen.writeFieldName(field);
				Value val = attrs.get(field);
				val.serialize(gen, ctx);
			}
		}
		gen.writeEndObject();

	}
	
	public Value getValue(String name) {
		return this.attrs.get(name);
	}
	
	public Map<String,Value> getValueMap() {
		return this.attrs;
	}
	
	public void removeValue(String name) {
		this.attrs.remove(name);
	}
	
	/**
	 * @return The number of attribute values in the extension
	 */
	public int getSize() {
		return this.attrs.size();
	}
	
	public void putValue(String name, Value val) {
		this.attrs.put(name, val);
	}
	
	public Set<String> attrNameSet() {
		return this.attrs.keySet();
	}
	
	private void processAttribute(Attribute attr, JsonNode node) throws SchemaException, ParseException, ConflictException {
		JsonNode attrNode = node.get(attr.getName());
		Value val = null;
		if (attrNode != null) {
			val = ValueUtil.parseJson(attr, attrNode, this.resolver);
			this.attrs.put(attr.getName(), val);
		}
	}
	
	public String getSchemaName() {
		return this.sname;
	}
	
	public Schema getSchema() {
		return this.eSchema;
	}


	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#hasBulkIds()
	 */
	@Override
	public boolean hasBulkIds() {
		Iterator<Value> iter = attrs.values().iterator();
		while (iter.hasNext()) {
			Value attribute = iter.next();
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				if (bAttr.hasBulkIds()) 
					return true;
			}
		}
		return false; 
	}


	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getBulkIdsRequired(java.util.List)
	 */
	@Override
	public void getBulkIdsRequired(List<String> bulkList) {
		Iterator<Value> iter = attrs.values().iterator();
		while (iter.hasNext()) {
			Value attribute = iter.next();
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getBulkIdsRequired(bulkList);
			}
		}
		
	}


	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getAttributesWithBulkIdValues(java.util.List)
	 */
	@Override
	public void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs) {
		Iterator<Value> iter = attrs.values().iterator();
		while (iter.hasNext()) {
			Value attribute = iter.next();
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getAttributesWithBulkIdValues(bulkIdAttrs);
			}
		}
		
	}

}
