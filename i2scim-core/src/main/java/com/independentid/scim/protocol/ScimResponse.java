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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.security.AciSet;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * @author pjdhunt ScimResponse is used to generate simple responses that do not involve data such as accepted or OK.
 * Error responses can be specifically set or assigned using constructor with {@link ScimException}.
 */
public class ScimResponse implements ScimSerializer {

    public final static String SCHEMA_ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";
    public final static String SCHEMA_LISTRESP = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    public final static String SCHEMA_BULKRESP = "urn:ietf:params:scim:api:messages:2.0:BulkResponse";

    public static final int ST_OK = HttpServletResponse.SC_OK;
    public static final int ST_ACCEPTED = HttpServletResponse.SC_ACCEPTED;
    public static final int ST_CREATED = HttpServletResponse.SC_CREATED;
    public static final int ST_NOCONTENT = HttpServletResponse.SC_NO_CONTENT;

    public static final int ST_TEMP_REDIR = 307;
    public static final int ST_PERM_REDIR = 308;
    public static final int ST_BAD_REQUEST = HttpServletResponse.SC_BAD_REQUEST;
    public static final int ST_UNAUTHORIZED = HttpServletResponse.SC_UNAUTHORIZED;
    public static final int ST_FORBIDDEN = HttpServletResponse.SC_FORBIDDEN;
    public static final int ST_NOTFOUND = HttpServletResponse.SC_NOT_FOUND;
    public static final int ST_CONFLICT = HttpServletResponse.SC_CONFLICT;
    public static final int ST_PRECONDITION = HttpServletResponse.SC_PRECONDITION_FAILED;
    public static final int ST_TOOLARGE = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
    public static final int ST_INTERNAL = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    public static final int ST_NOSUPPORT = HttpServletResponse.SC_NOT_IMPLEMENTED;
    public static final int ST_NOTMODIFIED = HttpServletResponse.SC_NOT_MODIFIED;
    public static final int ST_METHODNOTALLOWED = HttpServletResponse.SC_METHOD_NOT_ALLOWED;

    public static final String ERR_TYPE_FILTER = "invalidFilter";
    public static final String ERR_TYPE_TOOMANY = "tooMany";
    public static final String ERR_TYPE_UNIQUENESS = "uniqueness";
    public static final String ERR_TYPE_MUTABILITY = "mutability";
    public static final String ERR_TYPE_SYNTAX = "invalidSyntax";
    public static final String ERR_TYPE_PATH = "invalidPath";
    public static final String ERR_TYPE_TARGET = "noTarget";
    public static final String ERR_TYPE_BADVAL = "invalidValue";
    public static final String ERR_TYPE_BADVERS = "invalidVersion";

    public static final String DESCR_TYPE_FILTER = "Specified filter syntax or search endpoint is invalid.";
    public static final String DESCR_TYPE_TOOMANY = "The specified filter yields too many results.";
    public static final String DESCR_TYPE_UNIQUENESS = "One or more attribute values is already in use or reserved.";
    public static final String DESCR_TYPE_MUTABILITY = "The attempted modification is not compability with the attribute's mutability.";
    public static final String DESCR_TYPE_SYNTAX = "The request body message was invalid or did not conform to request schema.";
    public static final String DESCR_TYPE_PATH = "The path attribute is invalid or malformed.";
    public static final String DESCR_TYPE_TARGET = "The specified path did not yield an attribute or value that could be operated on.";
    public static final String DESCR_TYPE_BADVAL = "A required value is missing, or the supplied value is not compatible.";
    public static final String DESCR_TYPE_BADVERS = "The specified SCIM protocol version is not supported.";

    private int status;
    private String stype;
    private String location;
    private String detail;
    protected String etag;
    protected ScimException exception;

    public ScimResponse() {
        this.status = ST_OK;
        this.stype = null;
        this.location = null;
        this.detail = null;
        this.etag = null;
        this.exception = null;
    }

    public ScimResponse(int status, String detail, String scimType) {
        this.status = status;
        this.detail = detail;
        this.stype = scimType;
        this.etag = null;
        this.exception = null;
    }

    public ScimResponse(ScimException e) {
        setStatus(e.getStatus());
        setDetail(e.getDetail());
        setScimErrorType(e.getScimType());
        setDetail(e.getDetail());
        this.exception = e;
    }

    public ScimException getException() {
        return this.exception;
    }

    @Override
    public void parseJson(JsonNode node) throws SchemaException {
        JsonNode item = node.get("status");
        if (item != null)
            this.status = item.asInt();

        item = node.get("scimType");
        if (item != null)
            this.stype = item.asText();

        item = node.get("detail");
        if (item != null)
            this.detail = item.asText();

    }

    /**
     * Serialize the response by setting the appropriate HTTP response headers (from RequestCtx) and generating a JSON
     * body response if required (e.g. for SCIM Errors HTTP Status 400).
     * @param gen A JsonGenerator object usually bound to an HttpResponse object writer.
     * @param ctx A RequestCtx object containing the original request/response.
     * @throws IOException may be thrown when attempting to write to generator
     */
    public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
        if (ctx.getHttpServletResponse() != null)
            ctx.getHttpServletResponse().setStatus(getStatus());

        if (this.status >= 400) {
            gen.writeStartObject();
            gen.writeArrayFieldStart("schemas");
            gen.writeString(SCHEMA_ERROR);
            gen.writeEndArray();
            if (this.stype != null)
                gen.writeStringField("scimType", this.stype);
            if (this.detail != null)
                gen.writeStringField("detail", this.detail);
            gen.writeNumberField("status", this.status);
            gen.writeEndObject();

        }
    }

    @Override
    public JsonNode toJsonNode() {
        ObjectNode node = JsonUtil.getMapper().createObjectNode();
        if (this.status >= 500) {
            node.putArray("schemas").add(SCHEMA_ERROR);
            node.put("scimType", stype);
            node.put("detail", detail);
            node.put("status", status);
        }
        return node;
    }

	/*
	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		serialize(gen, ctx, false);
	}*/

    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {

        if (this.status >= 400) {
            serialize(gen, ctx);
        }
    }

    public void setHeaders(RequestCtx ctx) {
        if (ctx.getHttpServletResponse() != null)
            ctx.getHttpServletResponse().setStatus(getStatus());
    }


    /**
     * @return the status
     */
    public int getStatus() {
        return status;
    }


    /**
     * @param status the status to set
     */
    public void setStatus(int status) {
        this.status = status;
    }


    /**
     * @return the stype
     */
    public String getScimErrorType() {
        return stype;
    }


    /**
     * @param stype the stype to set
     */
    public void setScimErrorType(String stype) {
        this.stype = stype;
    }


    /**
     * @return the location
     */
    public String getLocation() {
        return location;
    }


    /**
     * @param location the location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public String getDetail() {
        return this.detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public void setETag(String etag) {
        this.etag = etag;
    }

    public String getETag() {
        return this.etag;
    }

    public void setError(ScimException e) {
        setStatus(e.getStatus());
        setScimErrorType(e.getScimType());
        setDetail(e.getDetail());
        this.exception = e;
    }

    /**
     * Applies a given ACI to the results. It looks at the rights and targetAttr specifications and removes attributes
     * which are not returnable.
     * @param set The {@link AciSet} to be applied.
     */
    public void applyAciSet(AciSet set) {
        processReadableResult(set);
    }

    /**
     * Process the targetAttrs list if provided
     * @param set The access control set specifying the targetAttr to return if any.
     */
    protected void processReadableResult(AciSet set) {
        // for ScimResponse there is nothing to process!
    }

}
