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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ResourceType implements ScimSerializer {

	public final static String SCHEMA_ID = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";

	private String id; //The resource endpoint's unique id (OPTIONAL)
	
	private String name; //Name of the resourceType e.g. User
	
	private String description;
	
	private URI endpoint; //The relative endpoint address to server base
	
	private String lastPathSegment; //Usually the plural version of resourceType name
	
	private String schema;
		
	private LinkedHashMap<String,SchemaExtension> schemaExtensions;

	private Meta meta;

	public ResourceType() {
		
	}
	
	public ResourceType(JsonNode node) throws SchemaException {
		this.schemaExtensions = new LinkedHashMap<>();
		this.parseJson(node);
	}
	
	/**
	 * @return A String containing the identifier for the resource type. Typically
	 * this is just a simple name like "User".
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * @param id A String containing the identifier for the resource type. Typically
	 * this is just a simple name like "User".
	 */
	public void setId(String id) {
		this.id = id;
	}
	
	/**
	 * @return A String containing the name of the resource type (e.g. User).
	 * Often corresponds to the "id" of the resource type.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * @param name A String containing the name of the resource type (e.g. User).
	 * Often corresponds to the "id" of the resource type.
	 */
	public void setName(String name) {
		this.name = name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	/**
	 * @return A URI containing the server relative endpoint for the resource 
	 * type. E.g. "/Users"
	 */
	public URI getEndpoint() {
		return endpoint;
	}
	
	/**
	 * @return String containing the main folder container name (minus slashes). 
	 * E.g. Users is the folder for type User
	 */
	public String getTypePath() {
		return this.lastPathSegment;
	}
	
	/**
	 * Sets the server relative endpoint of the resource type expressed as a URI (e.g. /Users).
	 * @param endpoint
	 */
	public void setEndpoint(URI endpoint) {
		this.endpoint = endpoint;
		this.lastPathSegment = endpoint.toString().replaceFirst(".*/([^/?]+).*", "$1");
	}
	/**
	 * @return A String containing the SCIM main schema URI for the resource type.
	 */
	public String getSchema() {
		return this.schema;
	}
	
	/**
	 * @param schema A String containing the SCIM main schema URI for the resource type.
	 */
	public void setSchema(String schema) {
		this.schema = schema;
	}
	
	/**
	 * @return A Map containing a set of SchemaExtension objects for the 
	 * Resource Type. The key is the URI for the schema extension.
	 */
	public Map<String,SchemaExtension> getSchemaExtensions() {
		return schemaExtensions;
	}
	
	/**
	 * @return An array of Strings containing the URI values of the schema extensions
	 * supported by the ResourceType.
	 */
	public String[] getSchemaExtension() {
		return schemaExtensions.keySet().toArray(new String[0]);
	}
	
	
	/**
	 * @param schemaExtensions A Map containing a set of SchemaExtension objects for the 
	 * Resource Type. The key is the URI for the schema extension.
	 */
	public void setSchemaExtensions(Map<String,SchemaExtension> schemaExtensions) {
		this.schemaExtensions = new LinkedHashMap<>();
		this.schemaExtensions.putAll(schemaExtensions);
		
		//this.schemaExtensions = schemaExtensions;
	}
	
	/**
	 * @return Returns a SCIM Meta object containing SCIM meta information 
	 * entry (e.g. location, resourceType, created, modified, version).
	 */
	public Meta getMeta() {
		return this.meta;
	}
	
	public void setMeta(Meta metaObj) {
		this.meta = metaObj;
	}
	
	public void setMeta(JsonNode metaNode) throws SchemaException  {
		if (metaNode != null) {
			this.meta = new Meta(metaNode);
		} else {
			this.meta = new Meta();
			this.meta.setResourceType(ScimParams.TYPE_RESOURCETYPE);
		}
	}
	
	public String toJsonString() throws IOException {
    	StringWriter writer = new StringWriter();
    	JsonGenerator gen = JsonUtil.getGenerator(writer, true);
    	serialize(gen,null,false);
    	gen.close();
    	writer.close();
    	
    	return writer.toString();
	}
	
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
	
	public String toString() {
    	StringWriter writer = new StringWriter();
    	JsonGenerator gen;
		try {
			gen = JsonUtil.getGenerator(writer, false);
			serialize(gen,null,false);
	    	gen.close();
	    	writer.close();
		} catch (IOException e) {
			return null;
		}

    	return writer.toString();
	}
	
	public JsonNode toJsonNode() {
		ObjectNode node = JsonUtil.getMapper().createObjectNode();
    	node.putArray("schemas").add(SCHEMA_ID);
    	if (id != null)
    		node.put("id",id);
    	if (name != null)
    		node.put("name",name);
    	if (endpoint != null)
    		node.put("endpoint",endpoint.toString());
    	if (description != null)
    		node.put("description",description);
    	if (schema != null)
    		node.put("schema",getSchema());
    	if (schemaExtensions != null && schemaExtensions.size()>0) {
			ArrayNode anode = node.putArray("schemaExtensions");
			for (SchemaExtension ext : schemaExtensions.values())
				anode.add(ext.toJsonNode());
		}
    	if (meta != null)
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
		
		gen.writeArrayFieldStart("schemas");
		gen.writeString(SCHEMA_ID);
		gen.writeEndArray();
		
		if (id != null)
			gen.writeStringField("id", id);
	
		if (name != null)
			gen.writeStringField("name", name);
		
		if (endpoint != null) 
			gen.writeStringField("endpoint", this.endpoint.toString());
		
		if (description != null)
			gen.writeStringField("description", description);
		
		if (schema != null)
			gen.writeStringField("schema", this.getSchema());
		
		if (schemaExtensions != null && schemaExtensions.size() > 0) {
			gen.writeArrayFieldStart("schemaExtensions");
			for (SchemaExtension ext : schemaExtensions.values()) {
				ext.serialize(gen, ctx, false);
			}
			gen.writeEndArray();
		}
		
		if (meta != null) {
			gen.writeFieldName("meta");
			meta.serialize(gen, ctx, false);			
		}
		
		gen.writeEndObject();
	}

	@Override
	public void parseJson(JsonNode node) throws SchemaException {
		JsonNode item = node.get("schemas");
		boolean valid = false;
		if (item != null) {
			Iterator<JsonNode> elements = item.elements();
			while (elements.hasNext() && !valid) {
				JsonNode enode = elements.next();
				if (enode.asText().equals(ScimParams.SCHEMA_SCHEMA_ResourceType))
					valid = true;
			}
		}
		if (!valid)
			throw new SchemaException("Resource endpoint did not have valid 'schemas' attribute.\nFields: "+node.fieldNames().toString());
			
		
		item = node.get("name");
		if (item != null) 
			this.name = item.asText();
		else
			throw new SchemaException("ResourceType has no name\nFields: "+node.fieldNames().toString());
		
		item = node.get("id");
		if (item != null)
			this.id = item.asText();
		
		item = node.get("endpoint");
		if (item != null) {
			String ep = item.asText();
			try {
				this.endpoint = new URI(ep);
			} catch (URISyntaxException e) {
				// TODO What should happen with an invalid resourcetype endpoint?
				e.printStackTrace();
			}

			this.lastPathSegment = ep.replaceFirst(".*/([^/?]+).*", "$1");
		}

		item = node.get("description");
		if (item != null)
			this.description = item.asText();
		
		item = node.get("schema");
		if (item != null) {
			this.schema = item.asText();
		} else
			throw new SchemaException("Resource endpoint has no schema\n"+node.toString());
		
		item = node.get("schemaExtensions");
		if (item != null) {
			//wipe out any existing extensions
			this.schemaExtensions.clear();
			
			Iterator<JsonNode> iter = item.elements();
			while (iter.hasNext()) {
				JsonNode extNode = iter.next();
				SchemaExtension ext = new SchemaExtension(extNode);
				String eid = ext.id;
				this.schemaExtensions.put(eid, ext);
			}
		}
	
		item = node.get("meta");
		setMeta(item);
	}
	
}
