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
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AccessControl;
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
import java.time.Instant;
import java.util.*;

/**
 * Meta defines the set of meta attributes for a SCIM resource.
 * @author pjdhunt
 *
 */
public class Meta extends ComplexValue implements ScimSerializer {
	private final static Logger logger = LoggerFactory.getLogger(Meta.class);
	public static final String META = "meta";
	public static final String META_LOCATION = "location";
	public static final String META_RESOURCE_TYPE = "resourceType";
	public static final String META_CREATED = "created";
	public static final String META_LAST_MODIFIED = "lastModified";
	public static final String META_VERSION = "version";
	public static final String META_ACIS = "acis";
	public static final String META_REVISIONS = "revisions";

	private String location = null;
	
    private String resourceType = null;

	private Date created;
    private Date lastModified;

    private MultiValue revisions;
    
    private String version = null;

    private Attribute attr = null;

    public final static DateFormat ScimDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public Meta() {}

	public Meta(SchemaManager schemaManager) {
		this.created = new Date();
		this.lastModified = this.created;
		this.attr = schemaManager.getSchemaById(SchemaManager.SCIM_CORE_SCHEMAID).getAttribute(META);

	}	
	
	public Meta(Attribute metaAttr, JsonNode node) throws SchemaException {
		this.attr = metaAttr;  // This is always the common attribute Meta
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
		if (created != null
			&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_CREATED),ctx))
			node.put(META_CREATED,getCreated());
		if (lastModified != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_LAST_MODIFIED),ctx))
			node.put(META_LAST_MODIFIED,getLastModified());

		if (revisions != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_REVISIONS),ctx))
			revisions.toJsonNode(node, META_REVISIONS);

		if (this.resourceType != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_RESOURCE_TYPE),ctx))
			node.put(META_RESOURCE_TYPE,resourceType);
		if (this.version != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_VERSION),ctx))
			node.put(META_VERSION,version);
		if (location != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_LOCATION),ctx))
			node.put(META_LOCATION,location);

		ConfigMgr cmgr = ConfigMgr.getConfig();
		AccessManager amgr = null;
		if (cmgr != null)
			amgr = ConfigMgr.getConfig().getAccessManager();
		if (amgr != null) {
			List<AccessControl> set = amgr.getResourceAcis(this.location);
			if (set.size()>0
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_ACIS),ctx)) {
				ArrayNode anode = node.putArray(META_ACIS);
				for (AccessControl aci : set) {
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
				case META_CREATED:
					return new DateValue(attr,getCreatedDate());
				case META_LAST_MODIFIED:
					return new DateValue(attr,getLastModifiedDate());
				case META_RESOURCE_TYPE:
					return new StringValue(attr,getResourceType());
				case META_VERSION:
					return new StringValue(attr,getVersion());
				case META_LOCATION:
					try {
						return new ReferenceValue(attr,getLocation());
					} catch (SchemaException e) {
						e.printStackTrace();
					}
				case META_REVISIONS:
					return this.revisions;
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

	private void checkAttr(RequestCtx ctx) {
    	if (this.attr == null) {
			this.attr = ctx.getSchemaMgr().getSchemaById(SchemaManager.SCIM_CORE_SCHEMAID).getAttribute(META);
			if (logger.isDebugEnabled())
				logger.debug("Lazy load of Meta Attribute definition *******");
		}
	}

	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
    	checkAttr(ctx);

		gen.writeStartObject();

		if (this.created != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_CREATED),ctx)) {
			gen.writeStringField(META_CREATED, getCreated());
		}
		
		if (this.lastModified != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_CREATED),ctx))
			gen.writeStringField(META_LAST_MODIFIED, getLastModified());

		if (this.revisions != null
				&& this.revisions.size() > 0
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_REVISIONS),ctx)) {
			try {
				gen.writeFieldName(META_REVISIONS);
				this.revisions.serialize(gen,ctx);
			} catch (ScimException e) {
				e.printStackTrace();
			}
		}

		if (this.resourceType != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_RESOURCE_TYPE),ctx))
			gen.writeStringField(META_RESOURCE_TYPE, this.resourceType);
		
		if (!forHash && this.version != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_VERSION),ctx)) {
			gen.writeStringField(META_VERSION, this.version);
		}

		// Write out the browser location if a servlet context is available
		if (this.location != null
				&& ValueUtil.isReturnable(this.attr.getSubAttribute(META_LOCATION),ctx)) {
			String url;
			ServletContext sctx = (ctx !=null)?ctx.getServletContext():null;
			if (sctx != null) {
				url = sctx.getContextPath() + this.location;
			} else
				url = this.location;
			gen.writeStringField(Meta.META_LOCATION, url);

			ConfigMgr cmgr = ConfigMgr.getConfig();
			AccessManager amgr = null;
			if (cmgr != null)
				amgr = ConfigMgr.getConfig().getAccessManager();
			if (amgr != null && !forHash) {
				List<AccessControl> set = amgr.getResourceAcis(this.location);
				if (set.size()>0) {
					if (ValueUtil.isReturnable(this.attr.getSubAttribute(META_ACIS), ctx)) {
						gen.writeFieldName(META_ACIS);
						gen.writeStartArray();
						for (AccessControl aci : set) {
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

		JsonNode item = node.get(META_LOCATION);
		if (item != null)
			this.location = item.asText();
		
		item = node.get(META_RESOURCE_TYPE);
		if (item != null)
			this.resourceType = item.asText();
		
		item = node.get(META_CREATED);
		
		if (item != null && !item.asText().equals("")) {
			try {
				// exammple valid time 2010-01-23T04:56:22Z
				this.created = ScimDateFormat.parse(item.asText());
			} catch (ParseException e) {
				System.out.println("Bad create date found: "+item.asText());
				e.printStackTrace();
			}
		}
		
		item = node.get(META_LAST_MODIFIED);

		if (item != null) {
			try {
				this.lastModified = ScimDateFormat.parse(item.asText());
			} catch (ParseException e) {
				System.out.println("Bad lastModified date found: "+item.asText());
			}
		}
		
		item = node.get(META_VERSION);
		if (item == null) 
			this.version = null;
		else
			this.version = item.asText();

		item = node.get(META_REVISIONS);
		try {
			if (item != null) {
				if (this.attr == null)
					logger.warn("**** Unable to load revisions as META Attr not loaded!");
				else
					this.revisions = (MultiValue) ValueUtil.parseJson(this.attr.getSubAttribute(META_REVISIONS), item, null);
			} else
				this.revisions = null;
		} catch (ConflictException | ParseException e) {
			e.printStackTrace();
		}
	}

	public void setRevisions(MultiValue revisions) {
		this.revisions = revisions;
	}

	public void addRevision(RequestCtx ctx) {
    	checkAttr(ctx);

    	Attribute revAttr = this.attr.getSubAttribute(META_REVISIONS);

		Map<Attribute,Value> map = new HashMap<>() ;
		StringValue tid = new StringValue(revAttr.getSubAttribute("value"), ctx.getTranId());
		this.lastModified = Date.from(Instant.now());
		DateValue tdate = new DateValue(revAttr.getSubAttribute("date"),this.lastModified);
		map.put(revAttr.getSubAttribute("date"),tdate);
		map.put(revAttr.getSubAttribute("value"),tid);
		try {
			ComplexValue revision = new ComplexValue(revAttr,map);
			if (this.revisions == null)
				this.revisions = new MultiValue(revAttr,new ArrayList<>());
			this.revisions.addValue(revision);
		} catch (SchemaException e) {
			e.printStackTrace();
		}
	}
    
}
