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
import com.independentid.scim.core.err.NotFoundException;
import com.independentid.scim.core.err.ScimException;
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
