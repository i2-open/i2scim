/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015 Phillip Hunt, All Rights Reserved                        *
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

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.protocol.ScimParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.InternalException;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */
public class SearchOp extends Operation {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3586153424932556487L;
	private final static Logger logger = LoggerFactory.getLogger(SearchOp.class);

	/**
	 * @param req HttpServletRequest object
	 * @param resp HttpServletResponse object
	 * @throws IOException
	 */
	public SearchOp(HttpServletRequest req, 
					HttpServletResponse resp) throws IOException {
		super(req, resp, true);
		
		if (!req.getRequestURI().endsWith(ScimParams.PATH_SEARCH)) {
			InternalException ie = new InternalException(
					"Was expecting a search request, got: "
							+ req.getRequestURI());
			setCompletionError(ie);
			return;
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#doOperation()
	 */
	@Override
	protected void doOperation() throws ScimException {
		try {
			this.scimresp = getHandler().get(ctx);
			return;
		} catch (ScimException e) {
			setCompletionError(e);
			return;
		} catch (BackendException e) {
			ScimException se = new InternalException("Unknown backend exception during SCIM Search: "+e.getLocalizedMessage(),e);
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
		// nothing to do.
		
	}

}
