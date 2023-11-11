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
import com.independentid.scim.core.err.TooManyException;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AciSet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ListResponse is used to generate a SCIM response per RFC7644. Note: For error responses use {@link ScimResponse}. For
 * single resource responses use {@link ResourceResponse}.
 * @author pjdhunt
 */
public class ListResponse extends ScimResponse {
    private static final Logger logger = LoggerFactory.getLogger(ListResponse.class);

    public static final String ATTR_RESOURCES = "Resources";
    public static final String ATTR_TOTRES = "totalResults";
    public static final String ATTR_ITEMPERPAGE = "itemsPerPage";
    public static final String ATTR_STARTINDEX = "startIndex";

    public final static SimpleDateFormat headDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

    protected Date lastMod;
    protected int totalRes;

    protected RequestCtx ctx;
    protected int smax;  // max server response size
    protected String id;
    public List<Attribute> sortAttrs;
    public boolean isDescend = false;

    protected ArrayList<ScimResource> entries = new ArrayList<>();

    /**
     * Constructor used to create an empty SCIM List Response.
     * @param ctx        A {@link RequestCtx} object containing the original request information.
     * @param maxResults The maximum results that can be returned to the client
     */
    public ListResponse(RequestCtx ctx, int maxResults) {
        super();
        this.ctx = ctx;
        this.smax = maxResults;
        if (this.ctx.count == 0 || this.ctx.count > maxResults)
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
     * Creates a single resource response. Used in connection with search queries where RFC7644 requires a ListResponse
     * format. For resource retrievals see {@link ResourceResponse}.
     * @param val The {@link ScimResource} object to be returned.
     * @param ctx The {@link RequestCtx} containing the original request/search.
     */
    public ListResponse(final ScimResource val, RequestCtx ctx) {
        super();
        this.ctx = ctx;
        initSort(ctx); // just in case this class is extended, otherwise not needed.
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

    public void initSort(RequestCtx ctx) {
        if (ctx.sortBy == null) {
            this.sortAttrs = null;
            return;
        }
        if (ctx.sortOrder != null)
            this.isDescend = ctx.sortOrder.toLowerCase(Locale.ROOT).startsWith("d");

        this.sortAttrs = new ArrayList<>();
        String[] sortAttrsReq = ctx.sortBy.split(",");
        SchemaManager smgr = ctx.getSchemaMgr();
        for (String attrReq : sortAttrsReq) {
            Attribute attr = smgr.findAttribute(attrReq, ctx);
            if (attr != null)
                this.sortAttrs.add(attr);
        }
    }

    public ListResponse(final List<ScimResource> vals, RequestCtx ctx, int maxResults) throws ScimException {
        super();
        this.ctx = ctx;
        this.smax = maxResults;
        this.id = null;
        initSort(ctx);
        if (this.ctx.count == 0 || this.ctx.count > maxResults)
            this.ctx.count = this.smax;

        this.totalRes = vals.size();
        if (this.totalRes > this.smax) {
            setError(new TooManyException());

        } else {
            List<ScimResource> sorted = doSort(vals);

            int start = ctx.startIndex - 1;

            int stop = start + ctx.count;

            if (stop > this.totalRes) {
                stop = this.totalRes;
            }

            for (int i = start; (i < stop && i < this.totalRes); i++) {
                ScimResource resource = sorted.get(i);
                Meta meta = resource.getMeta();
                if (meta != null) {
                    Date mdate = meta.getLastModifiedDate();
                    if (mdate != null && (lastMod == null || mdate.after(lastMod)))
                        lastMod = mdate;
                }
                this.entries.add(resource);
            }

            sorted = null;


            if (this.entries.size() == 1 && ctx.getPathId() != null) {
                this.etag = this.entries.get(0).getMeta().getVersion();
                setLocation(this.entries.get(0).getMeta().getLocation());
            } else setLocation(ctx.getPath());
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

    @Override
    public void setHeaders(RequestCtx ctx) {
        HttpServletResponse resp = ctx.getHttpServletResponse();
        if (resp != null) {
            resp.setStatus(getStatus());
            if (this.lastMod != null)
                resp.setHeader(ScimParams.HEADER_LASTMOD, headDate.format(this.lastMod));
            if (this.getLocation() != null)
                resp.setHeader(ScimParams.HEADER_LOCATION, this.getLocation());
            if (this.etag != null) {
                resp.setHeader(ScimParams.HEADER_ETAG, "\"" + this.etag + "\"");
            }
        }
    }

    public List<ScimResource> doSort(List<ScimResource> vals) {
        if (this.sortAttrs == null)
            return vals; // sort not requested.

        ScimResource[] resources = vals.toArray(new ScimResource[0]);
        Arrays.sort(resources, (o1, o2) -> {
            List<Attribute> sortAttrs = ListResponse.this.sortAttrs;
            for (Attribute attr : sortAttrs) {
                Value val1 = o1.getValue(attr);
                Value val2 = o2.getValue(attr);
                if (val1 == null && val2 == null)
                    continue;
                int res;
                if (val1 == null || val2 == null) {
                    if (val1 == null)
                        res = 1;  // This algorithm will sort missing values after non-null values
                    else
                        res = -1;
                } else
                    res = val1.compareTo(val2);
                if (res == 0)
                    continue; // try the next sort in the list
                if (ListResponse.this.isDescend)
                    return -1 * res;
                return res;
            }
            return 0;
        });

        ArrayList<ScimResource> res = new ArrayList<>();
        res.addAll(Arrays.asList(resources));
        return res;
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

        //doSort();

        // For multiple or filtered results, return the result in a ListResponse
        gen.writeStartObject();
        gen.writeArrayFieldStart(ScimParams.ATTR_SCHEMAS);
        gen.writeString(ScimResponse.SCHEMA_LISTRESP);
        gen.writeEndArray();
        gen.writeNumberField(ATTR_TOTRES, this.totalRes);
        gen.writeNumberField(ATTR_ITEMPERPAGE, this.entries.size());
        gen.writeNumberField(ATTR_STARTINDEX, this.ctx.startIndex);
        gen.writeArrayFieldStart(ATTR_RESOURCES);

        Iterator<ScimResource> iter = this.entries();


        while (iter.hasNext()) {
            ScimResource resource = iter.next();
            try {
                resource.serialize(gen, ctx, false);
            } catch (ScimException e) {
                //TODO This should not happen
                logger.error("Unexpected exception serializing a response value: " + e.getMessage(), e);
            }
        }
        gen.writeEndArray();
        gen.writeEndObject();
        // Set Last Modified based on the most recently modified result in the set.
        setHeaders(ctx);
    }

    @Override
    protected void processReadableResult(AciSet set) {
        for (ScimResource res : this.entries) {
            Set<Attribute> attrs = res.getAttributesPresent();
            for (Attribute attr : attrs) {
                if (set.isAttrNotReturnable(attr))
                    res.removeValue(attr);
            }
        }
    }
}
