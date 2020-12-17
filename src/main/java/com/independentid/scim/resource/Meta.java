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
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.security.AccessManager;
import com.independentid.scim.serializer.ScimSerializer;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Meta defines the set of meta attributes for a SCIM resource.
 * @author pjdhunt
 *
 */
public class Meta implements ScimSerializer {
	//private final static Logger logger = LoggerFactory
	//		.getLogger(Meta.class);

    private String location = null;
	
    private String resourceType = null;

	private Date created;
    private Date lastModified;
    
    private String version = null;

    public final static DateFormat ScimDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	public Meta () {
		this.created = new Date();
		this.lastModified = this.created;
	}	
	
	public Meta (JsonNode node) throws SchemaException {
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
