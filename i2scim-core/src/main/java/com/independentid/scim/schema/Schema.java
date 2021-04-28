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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.TreeMap;

/*
 * Schema defines a SCIM schema, its attributes and associated meta data. 
 */
public class Schema implements ScimSerializer  {
	
	public final static String SCHEMA_ID = "urn:ietf:params:scim:schemas:core:2.0:Schema";
	
    private String id;
	
    private Meta meta;
	
    private String name;
	
    private String description;
    
    private final TreeMap<String,Attribute> attributes;

    private final SchemaManager smgr;
    
    
	public Schema (SchemaManager schemaManager) {
		this.attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		this.smgr = schemaManager;
	}	
        
	/**
	 * Create a new <code>Schema</code> object based on Json Parser
	 * @param node A <JsonMode> parser handle containing a SCIM Schema object
	 * @throws SchemaException May be thrown when parsing an invalid JSON representation of SCIM Schema
	 */
	public Schema (SchemaManager schemaManager,JsonNode node) throws SchemaException {
		
		this.attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		this.smgr = schemaManager;
		this.parseJson(node);
	}
	    
	/**
	 * @return A String containing the identifier for the schema. Typically
	 * this is a urn. For example urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig: 
	 */
	public String getId() {
        return this.id;
    }

	/**
	 * @param id A String containing the schema identifier for the schema being
	 * defined. For example urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig: 
	 */
	public void setId(String id) {
        this.id = id;
    }

	/**
	 * @return Returns a SCIM Meta object containing SCIM meta information 
	 * entry (e.g. location, resourceType, created, modified, version).
	 */
	public Meta getMeta() {
        return this.meta;
    }

    /**
     * @param meta Sets the SCIM Meta information using a Meta object containing SCIM meta information 
	 * entry (e.g. location, resourceType, created, modified, version).
     */
    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    /**
	 * @return A String containing the name of the schema type (e.g. User).
	 * Often corresponds to the "id" of the resource type.
	 */
    public String getName() {
        return this.name;
    }

	/**
	 * @param name A String containing the name of the schema type (e.g. User).
	 * Often corresponds to the "id" of the resource type.
	 */
    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Attribute[] getAttributes() {
        return this.attributes.values()
        		.toArray(new Attribute[0]);
    }
    
    
    public Attribute getAttribute(String name) {
    	
    	return this.attributes.get(name);
    }

    public void putAttribute(Attribute attr) {
        this.attributes.put(attr.getName(), attr);
    }
    
    public String toJsonString() throws IOException {
    	StringWriter writer = new StringWriter();
    	JsonGenerator gen = JsonUtil.getGenerator(writer, true);
    	serialize(gen,null,false);
    	gen.close();
    	writer.close();
    	
    	return writer.toString();
    }
    
    public String toString() {
    	StringWriter writer = new StringWriter();
    	
    	try {
    		JsonGenerator gen = JsonUtil.getGenerator(writer, false);
    		serialize(gen,null,false);
        	gen.close();
        	writer.close();
        	
		} catch (IOException e) {
			return null;
		}
    	return writer.toString();
    }

    @Override
	public boolean equals(Object obj) {
    	if (obj instanceof Schema) {
    		Schema sobj = (Schema) obj;

    		//Do a JSON compare rather than a string compare
    		JsonNode jobj, jnode;
			try {
				jobj = JsonUtil.getJsonTree(sobj.toJsonString());
				jnode = JsonUtil.getJsonTree(toJsonString());
				return jnode.equals(jobj);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		
    	}
		return false;
	}

	public JsonNode toJsonNode() {
		ObjectNode node = JsonUtil.getMapper().createObjectNode();
		node.putArray("schemas").add(SCHEMA_ID);
		node.put("id",id);

		if (this.name != null)
			node.put("name",name);
		if (this.description != null)
			node.put("description",description);
		if (!attributes.values().isEmpty()){
			ArrayNode anode = node.putArray("attributes");
			for (Attribute attr : this.attributes.values()) {
				anode.add(attr.toJsonNode());
			}
		}
		node.set("meta",meta.toJsonNode());
		return node;

    }
    
	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		
		serialize(gen, ctx, false);
	}


	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
		gen.writeStartObject();
		
		// Write the schemas attribute as this isn't in normal scim schema def'n
		
		gen.writeArrayFieldStart("schemas");
		gen.writeString(SCHEMA_ID);
		gen.writeEndArray();
		
		
		gen.writeStringField("id", id);
		if (this.name != null)
			gen.writeStringField("name", this.name);
		if (this.description != null)
			gen.writeStringField("description", this.description);
		
		gen.writeArrayFieldStart("attributes");
		for (Attribute attr : this.attributes.values()) {
			attr.serialize(gen, ctx, false);
		}
		gen.writeEndArray();
		
		if(meta != null) {
			gen.writeFieldName("meta");
			meta.serialize(gen, ctx, false);
		}
		gen.writeEndObject();
	} 

	@Override
	public void parseJson(JsonNode node) throws SchemaException {
		JsonNode n_id = node.get("id");
		if (n_id == null)
			throw new SchemaException("Invalid Schema element: missing id\n"+ node);
		
		this.id = n_id.asText();
		
		JsonNode item = node.get("name");
		if (item != null)
			this.name = item.asText();
		
		item = node.get("description");
		if (item != null)
			this.description = item.asText();
		
		item = node.get("meta");
		if (item != null)
			this.meta = new Meta(item);
		else {
			this.meta = new Meta();
			// Set default location and type
			this.meta.setResourceType(ScimParams.TYPE_SCHEMA);
			String loc = "/"+ScimParams.PATH_TYPE_SCHEMAS+"/"+getId();
			this.meta.setLocation(loc);
		}
		JsonNode attrs = node.get("attributes");
		if (attrs != null) {
			Iterator<JsonNode> iter = attrs.elements();
			while (iter.hasNext()) {
				JsonNode anode = iter.next();
				Attribute attr = new Attribute(anode, null);
				
				//Generate the full schema path for the attr
				
				attr.setPath(this.id, attr.getName());
				
				this.attributes.put(attr.getName(), attr);
			}
		}
		
	}

}
