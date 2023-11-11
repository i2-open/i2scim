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
package com.independentid.scim.op;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.InternalException;
import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.NotFoundException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ResourceResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.serializer.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

/**
 * @author pjdhunt
 * The SCIM Create Operation takes either a JsonNode or HttpServletRequest object, parses the data and calls the backend
 * handler to create a new SCIM resource.
 */
public class CreateOp extends Operation implements IBulkOp {

    private static final long serialVersionUID = 5148093246436513326L;

    private final static Logger logger = LoggerFactory.getLogger(CreateOp.class);

    private final BulkOps parent;

    /**
     * Used in bulk requests for create requests. Provide the JsonNode of the data element of a bulk operation
     * @param data       The JsonNode of a data element of a SCIM Bulk operation
     * @param ctx        The associated bulk operation RequestCtx
     * @param parent     When part of a series of bulk operations, the parent {@link BulkOps} operation.
     * @param requestNum An identifier that identifies a request number in a series of bulk operations.
     */
    public CreateOp(JsonNode data, RequestCtx ctx, BulkOps parent, int requestNum) {
        super(ctx, requestNum);
        this.parent = parent;
        node = data;

    }

    /**
     * Creates a new SCIM resource. Note: it is assumed that the servlet has already determined that this is not a SCIM
     * Search request. The body of the request must contain a valid SCIM resource.
     * @param req  The {@link HttpServletRequest} object received by the SCIM Servlet
     * @param resp The {@link HttpServletResponse} to be returned by the SCIM Servlet
     */
    public CreateOp(HttpServletRequest req, HttpServletResponse resp) {
        super(req, resp);
        this.parent = null;
    }

    /**
     * Parses the JSON data and creates a new {@link ScimResource} object that can be added to backend
     * @param node A JsonNode representation of the payload
     *
     */
    protected void parseJson(JsonNode node) {
        if (node.isArray()) {
            setCompletionError(new InvalidSyntaxException(
                    "Detected array, expecting JSON object for SCIM Create request."));
            return;
        }

        ResourceType type = getResourceType();
        if (type == null) {
            ScimException se = new NotFoundException("An invalid path was specified for resource creation");
            setCompletionError(se);
            return;
        }

        try {
            newResource = new ScimResource(schemaManager, node, null, getResourceType().getTypePath());
            if (!ctx.isReplicaOp())
                newResource.setId(null); // ignore inbound identifiers (unless via replication).
        } catch (ScimException | ParseException e) {
            ScimException se;
            if (e instanceof ParseException) {
                se = new InvalidSyntaxException(
                        "JSON Parsing error found parsing SCIM request: "
                                + e.getLocalizedMessage(), e);
            } else {
                se = (ScimException) e;
            }
            logger.info(se.getMessage(), e);
            setCompletionError(se);
        }
    }

    public BulkOps getParentBulkRequest() {
        return this.parent;
    }

    @Override
    protected void doPreOperation() {
        parseRequestUrl();
        if (opState == OpState.invalid)
            return;
        parseRequestBody();
        if (opState == OpState.invalid)
            return;
        parseJson(node);
    }


    /*
     * (non-Javadoc)
     *
     * @see com.independentid.scim.op.Operation#doOperation()
     */
    @Override
    protected void doOperation() {

        try {
            this.scimresp = backendHandler.create(ctx, this.newResource);

        } catch (ScimException e) {
            logger.info("SCIM error while processing SCIM Create for: ["
                    + this.ctx.getPath() + "] " + e.getMessage(), e);
            setCompletionError(e);

        } catch (BackendException e) {
            ScimException se = new InternalException(
                    "Unknown backend exception during SCIM Create: "
                            + e.getLocalizedMessage(), e);

            logger.error(
                    "Received backend error while processing SCIM Create for: ["
                            + this.ctx.getPath() + "] " + e.getMessage(), e);
            setCompletionError(se);

        }
    }

    @Override
    public JsonNode getJsonReplicaOp() {
        // Note: when serializing for replication use null RequestCtx to ensure all attributes passed.
        if (!super.getStats().completionError) {
            JsonNode rnode = null;
            if (this.scimresp != null && this.scimresp instanceof ResourceResponse) {
                ResourceResponse rr = (ResourceResponse) this.scimresp;
                rnode = rr.getResultResource().toJsonNode(null);
            } else if (newResource != null)
                rnode = newResource.toJsonNode(null); // this should only happen in testing
            else if (node != null)
                rnode = node;
            if (rnode == null)
                return null;
            ObjectNode node = JsonUtil.getMapper().createObjectNode();
            node.put(BulkOps.PARAM_METHOD,Bulk_Method_POST);
            node.put(BulkOps.PARAM_PATH,ctx.getPath());
            node.set(BulkOps.PARAM_DATA,rnode);

            OpStat stats = getStats();
            node.put(BulkOps.PARAM_SEQNUM,stats.executionNum);
            node.put(BulkOps.PARAM_ACCEPTDATE,stats.getFinishDateStr());
            if (ctx != null)
                node.put(BulkOps.PARAM_TRANID, ctx.getTranId());
            return node;
        }
        return null;
    }
}
