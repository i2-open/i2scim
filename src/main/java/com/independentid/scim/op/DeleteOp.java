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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.server.InternalException;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */
public class DeleteOp extends Operation implements IBulkOp {

	/**
	 * 
	 */
	private static final long serialVersionUID = 64834204508689433L;
	private final static Logger logger = LoggerFactory.getLogger(DeleteOp.class);
	
	private BulkOps parent;

	/**
	 * @param req HttpServletRequest containing the path of the object to be deleted
	 * @param resp HttpServlet response where the response may be serialized
	 * @throws ScimException
	 * @throws IOException
	 */
	public DeleteOp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		super(req, resp, false);
		this.parent = null;
	}

	public DeleteOp(RequestCtx ctx, BulkOps parent, int requestNum) {
		super(ctx, requestNum);
		this.parent = parent;
	}
	
	public BulkOps getParentBulkRequest() {
		return this.parent;
	}

	public void doOperation() {

		ResourceType type = getResourceType();
		if (logger.isDebugEnabled())
			logger.debug("Initiating delete of " + ctx.getPath());
		if (type != null) {
			try {
				this.scimresp = getHandler().delete(ctx);
				if (logger.isDebugEnabled())
					logger.debug("Successfull delete of " + ctx.getPath());
				
				// doSuccess(this.scimresp);
			} catch (ScimException e) {
				logger.info("SCIM error while processing delete for: ["
						+ this.ctx.getPath() + "] " + e.getMessage(), e);
				setCompletionError(e);
				// doErrorResp(e, true);
			} catch (BackendException e) {
				ScimException se = new InternalException(
						"Unknown backend exception during SCIM Delete: "
								+ e.getLocalizedMessage(), e);
				setCompletionError(se);
				logger.error(
						"Received backend error while processing delete for: ["
								+ this.ctx.getPath() + "] " + e.getMessage(), e);
				// doErrorResp(e, 500, "Unknown backend error");
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#parseJson(com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	protected void parseJson(JsonNode node, ResourceType type) {
		// nothing needed to be done.
		
	}

}
