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
package com.independentid.scim.op;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.InternalException;
import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * PatchOp implements handling of SCIM Patch Operation. In particular it parses the request for a JSON
 * Patch input document and then passes the {@link JsonPatchRequest} to the backend for processing.
 * @author pjdhunt
 */
public class PatchOp extends Operation implements IBulkOp {

    private static final long serialVersionUID = 2989171950215028209L;

    private final static Logger logger = LoggerFactory.getLogger(PatchOp.class);

    private final BulkOps parent;
    private JsonPatchRequest preq;


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
        try {
            if (node.isArray()) {
                setCompletionError(new InvalidSyntaxException(
                        "Detected array, expecting JSON object for SCIM PATCH request."));
                return;
            }
            this.preq = new JsonPatchRequest(configMgr, node, ctx);

        } catch (SchemaException e) {
            ScimException se;
            se = new InvalidSyntaxException(e.getLocalizedMessage(), e);
            if (logger.isDebugEnabled())
                logger.debug("Error parsing PATCH request: " + se.getMessage(), e);
            setCompletionError(se);
        }
    }

    public BulkOps getParentBulkRequest() {
        return this.parent;
    }

    @Override
    protected void doOperation() {
        try {
            this.scimresp = backendHandler.patch(this.ctx, this.preq);

        } catch (ScimException e) {
            // Catch the scim error and serialize it
            logger.info("SCIM error while processing SCIM PATCH for: ["
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
        if (isCompletedNormally()) {
            ObjectNode node = JsonUtil.getMapper().createObjectNode();
            node.put(BulkOps.PARAM_METHOD,Bulk_Method_PATCH);
            node.put(BulkOps.PARAM_PATH,ctx.getPath());
            node.set(BulkOps.PARAM_DATA,preq.toJsonNode());

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
