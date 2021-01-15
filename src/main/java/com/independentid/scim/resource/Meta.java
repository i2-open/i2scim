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
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.security.AccessManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Meta defines the set of meta attributes for a SCIM resource.
 * @author pjdhunt
 *
 */
public class Meta extends ComplexValue implements ScimSerializer {
	private final static Logger logger = LoggerFactory.getLogger(Meta.class);

    private String location = null;
	
    private String resourceType = null;

	private Date created;
    private Date lastModified;
    
    private String version = null;

    private Attribute attr = null;

    public final static DateFormat ScimDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	public Meta () {
		this.created = new Date();
		this.lastModified = this.created;
	}	
	
	public Meta(Attribute attribute, JsonNode node) throws SchemaException {
		this.attr = attribute;  // This is always the common attribute Meta
		this.parseJson(node);
		
	}
            
    public String getLocation() {
        return this.location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getResourceType() {
        return this.resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getCreated() {
    	return ScimDateFormat.format(this.created);
    }
    
	public Date getCreatedDate() {
		return created;
	}

	public void setCreatedDate(Date created) {
		this.created = created;
	}

	public Date getLastModifiedDate() {
		return lastModified;
	}
	
	public String getLastModified() {
		return ScimDateFormat.format(this.lastModified);
	}

	public void setLastModifiedDate(Date lastModified) {
		this.lastModified = lastModified;
	}
	
	public String getVersion() {
		return this.version;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		serialize(gen, ctx, false);
	}

	public JsonNode toJsonNode(ObjectNode parent, String aname) {
		if (parent != null) {
			parent.set(aname, toJsonNode());
			return parent;
		}
		return toJsonNode();
	}

	public JsonNode toJsonNode() {
		return toJsonNode(null);
	}

	public JsonNode toJsonNode(RequestCtx ctx) {
		ObjectNode node = JsonUtil.getMapper().createObjectNode();
		if (created != null)
			node.put("created",getCreated());
		if (lastModified != null)
			node.put("lastModified",getLastModified());
		if (this.resourceType != null)
			node.put("resourceType",resourceType);
		if (this.version != null)
			node.put("version",version);
		if (location != null)
			node.put("location",location);

		AccessManager amgr = ConfigMgr.getConfig().getAccessManager();
		if (amgr != null) {
			AccessManager.AciSet set = amgr.getAcisByPath(this.location);
			if (set != null && set.acis.size() > 0) {
				ArrayNode anode = node.putArray("acis");
				for (AccessControl aci : set.acis) {
						anode.add(aci.toJsonNode());
				}
			}
		}
		// acis will be added elsewhere
		return node;
	}

	public Value getValue(String subattrname) {
			Attribute sattr = attr.getSubAttribute(subattrname);

			return getValue(sattr);

	}

	public Value getValue(Attribute attr) {
		if (attr.isChild()) {
			switch (attr.getName()) {
				case "created":
					return new DateValue(attr,getCreatedDate());
				case "lastModified":
					return new DateValue(attr,getLastModifiedDate());
				case "resourceType":
					return new StringValue(attr,getResourceType());
				case "version":
					return new StringValue(attr,getVersion());
				case "location":
					try {
						return new ReferenceValue(attr,getLocation());
					} catch (SchemaException e) {
						e.printStackTrace();
					}
			}
			return null;
		}
		return toValue();
	}

	public Value toValue() {
		try {
			return new ComplexValue(attr,this.toJsonNode());
		} catch (ConflictException | ParseException | SchemaException e) {
			logger.error("Unexpected exception converting Meta to ComplexValue: "+e.getLocalizedMessage(),e);
		}
		return null;
	}

	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
				
		gen.writeStartObject();
		
		if (this.created != null) {
			gen.writeStringField("created", getCreated());
		}
		
		if (this.lastModified != null)
			gen.writeStringField("lastModified", getLastModified());

		if (this.resourceType != null)
			gen.writeStringField("resourceType", this.resourceType);
		
		if (!forHash && this.version != null) {
			gen.writeStringField("version", this.version);
		}

		// Write out the browser location if a servlet context is available
		if (this.location != null) {
			String url;
			ServletContext sctx = (ctx !=null)?ctx.getServletContext():null;
			if (sctx != null) {
				url = sctx.getContextPath() + this.location;
			} else
				url = this.location;
			gen.writeStringField("location", url);

			AccessManager amgr = ConfigMgr.getConfig().getAccessManager();
			if (amgr != null && !forHash) {
				AccessManager.AciSet set = amgr.getAcisByPath(this.location);
				if (set != null && set.acis.size() > 0) {
					gen.writeFieldName("acis");
					gen.writeStartArray();
					for (AccessControl aci : set.acis) {
						try {
							aci.serialize(gen, ctx);
						} catch (ScimException e) {
							e.printStackTrace();
						}
					}
					gen.writeEndArray();
				}
			}

		}


		gen.writeEndObject();
	}

	public void parseJson(Attribute attr, JsonNode node) throws SchemaException {
		this.attr = attr;
		parseJson(node);
	}

	public HashMap<Attribute, Value> getValueArray() {
		HashMap<Attribute,Value> map = new HashMap<>();
		Map<String,Attribute> amap = attr.getSubAttributesMap();
		for(Attribute sattr: amap.values()) {
			map.put(sattr,getValue(sattr));
		}
		return map;
	}

	@Override
	public void parseJson(JsonNode node) throws SchemaException {
		
		JsonNode item = node.get("location");
		if (item != null)
			this.location = item.asText();
		
		item = node.get("resourceType");
		if (item != null)
			this.resourceType = item.asText();
		
		item = node.get("created");
		/*
		if (logger.isDebugEnabled() && item != null) {
			logger.debug("node type:\t"+node.getNodeType()); 
			logger.debug("create date:\t"+item.asText());
		}
		*/
		
		if (item != null && !item.asText().equals("")) {
			try {
				// exammple valid time 2010-01-23T04:56:22Z
				this.created = ScimDateFormat.parse(item.asText());
			} catch (ParseException e) {
				System.out.println("Bad create date found: "+item.asText());
				e.printStackTrace();
			}
		}
		
		item = node.get("lastModified");
		
		/*
		if (logger.isDebugEnabled() && item != null) {
			logger.debug("node type:\t"+node.getNodeType()); 
			logger.debug("mod date:\t"+item.asText());
		}
		*/
		
		if (item != null) {
			try {
				this.lastModified = ScimDateFormat.parse(item.asText());
			} catch (ParseException e) {
				System.out.println("Bad lastModified date found: "+item.asText());
			}
		}
		
		item = node.get("version");
		if (item == null) 
			this.version = null;
		else
			this.version = item.asText();
		
	}
    
}
