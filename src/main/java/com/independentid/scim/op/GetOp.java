/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015,2020 Phillip Hunt, All Rights Reserved                   *
 *                                                                    *
 *  Confidential and Proprietary                                      *
 *                                                                    *
 *  This unpublished source code may not be distributed outside       *
 *  “Independent Identity Org”. without express written permission of *
 *  Phillip Hunt.                                                     *
 *                                                                    *
 *  People at companies that have signed necessary non-disclosure     *
 *  agreements may only distribute to others in the company that are  *
 *  bound by the same confidentiality agreement and distribution is   *
 *  subject to the terms of such agreement.                           *
 **********************************************************************/
package com.independentid.scim.op;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.protocol.ConfigResponse;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.InternalException;
import com.independentid.scim.server.NotFoundException;
import com.independentid.scim.server.ScimException;

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
	 * @param req
	 * @param resp
	 * @throws IOException
	 */
	public GetOp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		super(req, resp, false);
		
		if (this.state != OpState.invalid &&
				ctx.getResourceContainer() == null)
			ctx.setResourceContainer("/");
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#doOperation()
	 */
	@Override
	protected void doOperation() throws ScimException {
		if (this.state == OpState.invalid)
			return;
		String container = ctx.getResourceContainer();
		// If this is a query to the Schema or ResourceType end points use ConfigResponse
		if (ConfigResponse.isConfigEndpoint(container)) {
			this.scimresp = new ConfigResponse(ctx);
			
			return;
		} 
		
		// Check if an undefined endpoint was requested.
		if (container != null) {
			ResourceType type = sconfig.getResourceTypeByPath(container);
			if (type == null) {
				setCompletionError(new NotFoundException("Undefined resource endpoint."));
				return;
			}
		}
		
		// Pass the request to the backend database handler
		try {
			this.scimresp = getHandler().get(ctx);
			
			//TODO: In theory ScimResponse should handle the error and this should not be caught
		} catch (ScimException e) {
			setCompletionError(e);
			return;
		} catch (BackendException e) {
			ScimException se = new InternalException("Unknown backend exception during SCIM Get: "+e.getLocalizedMessage(),e);
			setCompletionError(se);
			logger.error(
					"Received backend error while processing SCIM Search for: ["
							+ this.ctx.getPath() + "] " + e.getMessage(), e);
			return;
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#parseJson(com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	protected void parseJson(JsonNode node, ResourceType type) throws ScimException,
			SchemaException {
		// Nothing to be done.
		
	}

}
