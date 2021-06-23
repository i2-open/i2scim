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
import com.independentid.scim.core.err.*;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.serializer.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.ParseException;

/**
 * @author pjdhunt
 */
public class PutOp extends Operation implements IBulkOp {

    private static final long serialVersionUID = -1801010484460287650L;

    private final static Logger logger = LoggerFactory.getLogger(PutOp.class);

    private final BulkOps parent;

    /**
     * Used in bulk requests for put requests. Provide the JsonNode of the data element of a bulk operation
     * @param data       The JsonNode of a data element of a SCIM Bulk operation
     * @param ctx        The associated bulk operation RequestCtx
     * @param requestNum If part of a series of bulk operations, the request number
     */
    public PutOp(JsonNode data, RequestCtx ctx, BulkOps parent, int requestNum) {
        super(ctx, requestNum);
        this.parent = parent;
        this.node = data;

    }

    /**
     * @param req  The {@link HttpServletRequest} object received by the SCIM Servlet
     * @param resp The {@link HttpServletResponse} to be returned by the SCIM Servlet
     */
    public PutOp(HttpServletRequest req, HttpServletResponse resp) {
        super(req, resp);
        this.parent = null;

    }

    protected void parseJson(JsonNode node) {
        if (this.ctx.getPathId() == null) {
            ScimException se = new MethodNotAllowedException("HTTP PUT not permitted against resource container.");
            setCompletionError(se);
            return;
        }

        if (node.isArray()) {
            setCompletionError(new InvalidSyntaxException(
                    "Detected array, expecting JSON object for SCIM Put request."));
            return;
        }

        ResourceType type = getResourceType();
        if (type == null) {
            ScimException se = new NotFoundException("An invalid path was specified for resource creation");
            setCompletionError(se);
            return;
        }

        try {
            this.newResource = new ScimResource(schemaManager, node, null, type.getTypePath());
        } catch (ScimException | ParseException e) {
            if (e instanceof ScimException)
                setCompletionError(e);
            else //convert ParseException to SCIM invalidsyntaxexception
                setCompletionError(new InvalidSyntaxException(
                        "JSON Parsing error found parsing SCIM request: "
                                + e.getLocalizedMessage(), e));
        }
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

    public BulkOps getParentBulkRequest() {
        return this.parent;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.independentid.scim.op.Operation#doOperation()
     */
    @Override
    protected void doOperation() {

        try {
            this.scimresp = backendHandler.replace(ctx, this.newResource);

        } catch (ScimException e) {
            logger.info("SCIM error while processing SCIM PUT for: ["
                    + this.ctx.getPath() + "] " + e.getMessage(), e);
            setCompletionError(e);

        } catch (BackendException e) {
            ScimException se = new InternalException(
                    "Unknown backend exception during SCIM PUT: "
                            + e.getLocalizedMessage(), e);

            logger.error(
                    "Received backend error while processing SCIM PUT for: ["
                            + this.ctx.getPath() + "] " + e.getMessage(), e);
            setCompletionError(se);
        }
    }

    @Override
    public JsonNode getJsonReplicaOp() {
        if (!super.getStats().completionError) {
            ObjectNode node = JsonUtil.getMapper().createObjectNode();
            node.put(BulkOps.PARAM_METHOD,Bulk_Method_PUT);
            node.put(BulkOps.PARAM_PATH,ctx.getPath());
            // CTX should be null to ensure entire structure emitted
            node.set(BulkOps.PARAM_DATA,newResource.toJsonNode(null));

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
