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
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.InternalException;
import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.NotFoundException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
        this.node = data;
        this.cfgMgr = ctx.getConfigMgr();
    }

    /**
     * Creates a new SCIM resource. Note: it is assumed that the servlet has already determined that this is not a SCIM
     * Search request. The body of the request must contain a valid SCIM resource.
     * @param req  The {@link HttpServletRequest} object received by the SCIM Servlet
     * @param resp The {@link HttpServletResponse} to be returned by the SCIM Servlet
     * @param mgr Pointer to the system ConfigMgr instance for Schema definitions
     */
    public CreateOp(HttpServletRequest req, HttpServletResponse resp, ConfigMgr mgr ) {
        super(req, resp);
        this.parent = null;
        this.cfgMgr = mgr;
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
            this.newResource = new ScimResource(this.cfgMgr, node, null, getResourceType().getTypePath());
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
        if (state == OpState.invalid)
            return;
        parseRequestBody();
        if (state == OpState.invalid)
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
            this.scimresp = getHandler().create(ctx, this.newResource);

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

}
