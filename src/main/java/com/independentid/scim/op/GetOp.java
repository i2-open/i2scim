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
import com.independentid.scim.core.err.NotFoundException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ConfigResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author pjdhunt
 *
 */
public class GetOp extends Operation {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2919810859040965128L;
	private final static Logger logger = LoggerFactory.getLogger(GetOp.class);

	/**
	 * @param req The {@link HttpServletRequest} object received by the SCIM Servlet
	 * @param resp The {@link HttpServletResponse} to be returned by the SCIM Servlet
	 * @param configMgr A pointer to the system ConfigMgr bean for access to Schema etc
	 */
	public GetOp(HttpServletRequest req, HttpServletResponse resp, ConfigMgr configMgr) {
		super(req, resp);
		this.cfgMgr = configMgr;

	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#doOperation()
	 */
	@Override
	protected void doOperation() {
		if (this.state == OpState.invalid)
			return;
		String container = ctx.getResourceContainer();
		// If this is a query to the Schema or ResourceType end points use ConfigResponse
		if (ConfigResponse.isConfigEndpoint(container)) {
			this.scimresp = new ConfigResponse(ctx, cfgMgr);
			return;
		} 
		
		// Check if an undefined endpoint was requested.
		ResourceType type = cfgMgr.getResourceTypeByPath(container);
		if (type == null) {
			setCompletionError(new NotFoundException("Undefined resource endpoint."));
			return;
		}

		// Pass the request to the backend database handler
		try {
			this.scimresp = getHandler().get(ctx);
			
			//TODO: In theory ScimResponse should handle the error and this should not be caught
		} catch (ScimException e) {
			setCompletionError(e);

		} catch (BackendException e) {
			ScimException se = new InternalException("Unknown backend exception during SCIM Get: "+e.getLocalizedMessage(),e);
			setCompletionError(se);
			logger.error(
					"Received backend error while processing SCIM Search for: ["
							+ this.ctx.getPath() + "] " + e.getMessage(), e);
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#parseJson(com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	protected void parseJson(JsonNode node) {
		// Nothing to be done.
		
	}

}
