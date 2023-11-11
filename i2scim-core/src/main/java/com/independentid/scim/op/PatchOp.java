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
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PatchOp implements handling of SCIM Patch Operation. In particular it parses the request for a JSON
 * Patch input document and then passes the {@link JsonPatchRequest} to the backend for processing.
 * @author pjdhunt
 */
public class PatchOp extends Operation implements IBulkOp {

    private static final long serialVersionUID = 2989171950215028209L;

    private final static Logger logger = LoggerFactory.getLogger(PatchOp.class);

    private final BulkOps parent;



    private JsonPatchRequest patchRequest;


    /**
     * Used in bulk requests for Patch requests. Provide the JsonNode of the data element of a bulk operation
     * @param data       The JsonNode of a data element of a SCIM Bulk operation
     * @param ctx        The associated bulk operation RequestCtx
     * @param parent     When part of a bulk operation, the parent Operation.
     * @param requestNum The request sequence number of this operation in a parent set of bulk requests
     */
    public PatchOp(JsonNode data, RequestCtx ctx, BulkOps parent, int requestNum) {
        super(ctx, requestNum);
        this.parent = parent;
        this.node = data;

    }

    /**
     * A SCIM Patch Operation. The constructor accepts the HttpServletRequest and parses the patch operation in the
     * constructor. If successfully parsed, the runnable portion executes the operation.
     * @param req  The {@link HttpServletRequest} passed from the Scim Servlet
     * @param resp The {@link HttpServletResponse} passed from the Scim Servlet
     */
    public PatchOp(HttpServletRequest req, HttpServletResponse resp) {
        super(req, resp);
        this.parent = null;

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

    protected void parseJson(JsonNode node) {
        if (this.ctx.getPathId() == null) {
            ScimException se = new MethodNotAllowedException("HTTP PATCH not permitted against resource container.");
            setCompletionError(se);
            return;
        }
        try {
            if (node.isArray()) {
                setCompletionError(new InvalidSyntaxException(
                        "Detected array, expecting JSON object for SCIM PATCH request."));
                return;
            }
            this.patchRequest = new JsonPatchRequest(node, ctx);

        } catch (SchemaException e) {
            ScimException se;
            se = new InvalidSyntaxException(e.getLocalizedMessage(), e);
            if (logger.isDebugEnabled())
                logger.debug("Error parsing PATCH request: " + se.getMessage(), e);
            setCompletionError(se);
        } catch (InvalidValueException e) {

            if (logger.isDebugEnabled())
                logger.debug("Invalid PATCH request: " + e.getMessage(), e);
            setCompletionError(e);
        } catch (BadFilterException e) {
            if (logger.isDebugEnabled())
                logger.debug("Error parsing PATCH filter: "+e.getMessage(),e);
            setCompletionError(e);
        }
    }

    public JsonPatchRequest getPatchRequest() {
        return patchRequest;
    }

    public BulkOps getParentBulkRequest() {
        return this.parent;
    }

    @Override
    protected void doOperation() {
        try {
            this.scimresp = backendHandler.patch(this.ctx, this.patchRequest);
        } catch (ScimException e) {
            // Catch the scim error and serialize it
            if (logger.isDebugEnabled())
                logger.debug("SCIM error while processing SCIM PATCH for: ["
                    + this.ctx.getPath() + "] " + e.getMessage(), e);
            setCompletionError(e);

        } catch (BackendException e) {
            ScimException se = new InternalException(
                    "Unknown backend exception during SCIM Patch: "
                            + e.getLocalizedMessage(), e);
            setCompletionError(se);
            logger.error(
                    "Received backend error while processing SCIM PATCH for: ["
                            + this.ctx.getPath() + "] " + e.getMessage(), e);

        }

    }

    @Override
    public JsonNode getJsonReplicaOp() {
        if (!super.getStats().completionError) {
            ObjectNode node = JsonUtil.getMapper().createObjectNode();
            node.put(BulkOps.PARAM_METHOD,Bulk_Method_PATCH);
            node.put(BulkOps.PARAM_PATH,ctx.getPath());
            node.set(BulkOps.PARAM_DATA, patchRequest.toJsonNode());

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
