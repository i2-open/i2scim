/*
 * Copyright 2021.  Independent Identity Incorporated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.protocol;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.security.AciSet;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

/**
 * ResourceResponse is used to generate a SCIM response per RFC7644. This response
 * format returns a ScimResource format directly. 
 * @author pjdhunt
 *
 */
public class ResourceResponse extends ScimResponse {
	private static final Logger logger = LoggerFactory.getLogger(ListResponse.class);
	final static SimpleDateFormat headDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

	protected Date lastMod;
	protected int totalRes;
	protected RequestCtx ctx;
	protected int smax;  // max server response size
	protected String id;

	protected ArrayList<ScimResource> entries = new ArrayList<>();
	
	
	public ResourceResponse(ScimResource val, RequestCtx ctx) {
		super();
		this.ctx = ctx;

		if (val.getMeta() == null) {
			// This typically happens in server config endpoints
			String cp = ctx.sctx.getContextPath();
			setLocation(cp + ctx.getPath());
		} else {
			setLocation(val.getMeta().getLocation());
		
			this.etag = val.getMeta().getVersion();
			this.lastMod = val.getMeta().getLastModifiedDate();
		}
		this.id = val.getId();
		this.entries.add(val);
		this.totalRes = 1;
		
	}
	
	public String getId() {
		return this.id;
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

        if (!ctx.isAsynchronous()) {
            // For single results, just return the object itself.
            ScimResource resource = getResultResource();
            try {
                resource.serialize(gen, ctx, false);
            } catch (ScimException e) {
                //TODO This should not happen
                logger.error("Unexpected exception serializing a response value: " + e.getMessage(), e);
            }
		}

		setHeaders(ctx);
	}

	public void setHeaders(RequestCtx ctx) {
		HttpServletResponse resp = ctx.getHttpServletResponse();
		if (resp != null) {
			resp.setStatus(getStatus());
            if (ctx.isAsync) {
                resp.setHeader("Preference-Applied", "respond-async");
                if (this.getLocation() != null)
                    resp.setHeader(ScimParams.HEADER_LOCATION, this.getLocation());
                if (ctx.getTranId() != null)
                    resp.setHeader("Set-txn", ctx.getTranId());
                return;
            }
			if (this.lastMod != null)
				resp.setHeader(ScimParams.HEADER_LASTMOD, headDate.format(this.lastMod));
			if (this.getLocation() != null)
				resp.setHeader(ScimParams.HEADER_LOCATION, this.getLocation());
			if (this.etag != null) {
				resp.setHeader(ScimParams.HEADER_ETAG, "\"" + this.etag + "\"");
			}
		}
	}

	@Override
	protected void processReadableResult(AciSet set) {
		for (ScimResource res : this.entries) {
			Set<Attribute> attrs = res.getAttributesPresent();
			for (Attribute attr: attrs) {
				if (set.isAttrNotReturnable(attr))
					res.blockAttribute(attr);
			}
		}
	}

	public ScimResource getResultResource() {
		if(this.entries.isEmpty()) return null;
		return this.entries.get(0);
	}
}
