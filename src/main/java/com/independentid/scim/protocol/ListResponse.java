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

package com.independentid.scim.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.core.err.TooManyException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.security.AciSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ListResponse is used to generate a SCIM response per RFC7644. Note:
 * For error responses use <ScimResponse>. For single resource responses use <ResourceResponse>.
 * @author pjdhunt
 *
 */
public class ListResponse extends ScimResponse {
	private static final Logger logger = LoggerFactory.getLogger(ListResponse.class);

	public final static SimpleDateFormat headDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
	
	protected Date lastMod;
	protected int totalRes;
	protected RequestCtx ctx;
	protected int smax;  // max server response size
	protected String id;

	protected ArrayList<ScimResource> entries = new ArrayList<>();
	
	/**
	 * Constructor used to create an empty SCIM List Response.
	 * @param ctx A <RequestCtx> object containing the original request information.
	 * @param configMgr System configuration to obtain max results.
	 */
	public ListResponse(RequestCtx ctx, ConfigMgr configMgr) {
		super();
		this.ctx = ctx; 
		
		this.smax = configMgr.getMaxResults();
		if (this.ctx.count == 0 || this.ctx.count > this.smax)  
			this.ctx.count = this.smax;
		this.totalRes = 0;
		this.id = null;
		this.lastMod = null;
		
		ServletContext sctx = ctx.getServletContext();
		if (sctx == null)
			setLocation(ctx.path);  
		else
			setLocation(sctx.getRealPath(ctx.path));
	}
	
	/**
	 * Creates a single resource response. Used in connection with search queries where RFC7644 requires a ListResponse format.
	 * For resource retrievals see <ResourceResponse>.
	 * @param val The <ScimResource> object to be returned.
	 * @param ctx The <RequestCtx> containing the original request/search.
	 * @param configMgr System config to obtain max results
	 */
	public ListResponse(final ScimResource val, RequestCtx ctx, ConfigMgr configMgr) {
		super();
		this.ctx = ctx;
		
		this.smax = configMgr.getMaxResults();
		if (this.ctx.count == 0 || this.ctx.count > this.smax)  
			this.ctx.count = this.smax;
		
		setLocation(val.getMeta().getLocation());
		
		this.etag = val.getMeta().getVersion();
		this.lastMod = val.getMeta().getLastModifiedDate();
		this.id = val.getId();
		this.entries.add(val);
		this.totalRes = 1;
		
	}
	
	public String getId() {
		return this.id;
	}
	
	
	public ListResponse(final List<ScimResource> vals, RequestCtx ctx, ConfigMgr configMgr) throws ScimException {
		super();
		this.ctx = ctx;
		
		this.id = null;
		
		this.smax = configMgr.getMaxResults();
		if (this.ctx.count == 0 || this.ctx.count > this.smax)  
			this.ctx.count = this.smax;
		
		this.totalRes = vals.size();
		if (this.totalRes > this.smax) {
			setError(new TooManyException());
			
		} else {
		
			int start = ctx.startIndex-1;
			int count = ctx.count;
			int stop = start + count;
			/*
			if (stop > this.totalRes) {
				stop = this.totalRes;
				count = stop - start;
			}
			 */

			
			for (int i = start; (i < this.ctx.count && i < vals.size()); i++) {
				ScimResource resource = vals.get(i);
				Date mdate = resource.getMeta().getLastModifiedDate();
				if (mdate != null &&(lastMod == null || mdate.after(lastMod)))
					lastMod = mdate;
				this.entries.add(resource);
			}
			
			
			if (this.entries.size() == 1 && ctx.getPathId() != null) {
				this.etag = this.entries.get(0).getMeta().getVersion();
				setLocation(this.entries.get(0).getMeta().getLocation());
			}
			else setLocation(ctx.getPath());
			//setLocation(this.ctx.sctx.getRealPath(ctx.path));
		}
		
	}
	
	public List<ScimResource> getResults() {
		return this.entries;
	}
	
	public Iterator<ScimResource> entries() {
		return this.entries.iterator();
	}
	
	public int getSize() {
		return this.entries.size();
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.protocol.ScimResponse#serialize(com.fasterxml.jackson.core.JsonGenerator, com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public void serialize(JsonGenerator gen, RequestCtx sctx) throws IOException {
		serialize(gen, sctx, false);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.protocol.ScimResponse#serialize(com.fasterxml.jackson.core.JsonGenerator, com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
		
		//TODO: What happens if getStatus = HttpServletResponse.SC_OK
		//TODO: What if entries.size == 0?
		
		/* Note: Normally this.ctx and sctx are the same. However server may modify
		 * sctx after result set creation (or have chosen to insert an override). 
		 */
		
		HttpServletResponse resp = ctx.getHttpServletResponse(); 

		// For multiple or filtered results, return the result in a ListResponse
		gen.writeStartObject();
		gen.writeArrayFieldStart("schemas");
		gen.writeString(ScimResponse.SCHEMA_LISTRESP);
		gen.writeEndArray();
		gen.writeNumberField("totalResults",this.totalRes);
		gen.writeNumberField("itemsPerPage",this.entries.size());
		gen.writeNumberField("startIndex", this.ctx.startIndex);
		gen.writeArrayFieldStart("Resources");
		
		Iterator<ScimResource> iter = this.entries();
		
		
		while(iter.hasNext()) {
			ScimResource resource = iter.next();
			try {
				resource.serialize(gen, ctx, false);
			} catch (ScimException e) {
				//TODO This should not happen
				logger.error("Unexpected exception serializing a response value: "+e.getMessage(),e);
			}
		}
		gen.writeEndArray();
		gen.writeEndObject();
		// Set Last Modified based on the most recently modified result in the set.
		if (resp != null) {
			if (this.lastMod != null)
				resp.setHeader(ScimParams.HEADER_LASTMOD, headDate.format(this.lastMod));

			resp.setStatus(this.getStatus());
			String loc = getLocation();

			if (loc != null)
				resp.setHeader("Location", loc);
			// TODO should we check for response size of 0 for not found?
		}
	}

	@Override
	public JsonNode toJsonNode() {
		return super.toJsonNode();
	}

	@Override
	protected void processReadableResult(AciSet set) {
		for (ScimResource res : this.entries) {
			Set<Attribute> attrs = res.getAttributesPresent();
			for (Attribute attr: attrs) {
				if (set.isAttrNotReturnable(attr))
					res.removeValue(attr);
			}
		}
	}
}
