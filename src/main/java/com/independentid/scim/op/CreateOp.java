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
import java.text.ParseException;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.server.ConflictException;
import com.independentid.scim.server.InternalException;
import com.independentid.scim.server.InvalidSyntaxException;
import com.independentid.scim.server.NotFoundException;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */
public class CreateOp extends Operation implements IBulkOp {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5148093246436513326L;

	private final static Logger logger = LoggerFactory.getLogger(CreateOp.class);
	
	private BulkOps parent;

	/**
	 * Used in bulk requests for create requests. Provide the JsonNode of the
	 * data element of a bulk operation
	 * 
	 * @param data
	 *            The JsonNode of a data element of a SCIM Bulk operation
	 * @param ctx
	 *            The associated bulk operation RequestCtx
	 * @param handler
	 * @throws IOException
	 */
	public CreateOp(JsonNode data, RequestCtx ctx, BulkOps parent, int requestNum) {
		super(ctx, requestNum);
		this.parent = parent;
		try {
			parseJson(data, null);
		} catch (ScimException e) {
			this.setCompletionError(e);
		}
	}

	/**
	 * Creates a new SCIM resource. Note: it is assumed that the servlet has
	 * already determined that this is not a SCIM Search request. The body of
	 * the request must contain a valid SCIM resource.
	 * @param req
	 * @param resp
	 * 
	 * @throws IOException
	 */
	public CreateOp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		super(req, resp, false);
		this.parent = null;
		ResourceType type = getResourceType();
		if (type == null) {
			ScimException se = new NotFoundException(
					"Resource endpoint not found or implemented");
			logger.info(se.getMessage());
			setCompletionError(se);
			return;
		}

		ServletInputStream input = req.getInputStream();
		if (input == null) {
			logger.info("Missing body for SCIM Create request received");
			setCompletionError(new InvalidSyntaxException(
					"Request body missing or empty."));
			return;
		}

		
		JsonNode node = JsonUtil.getJsonTree(input);
		//input.close();
		try {
			parseJson(node, type);
		} catch (ScimException e) {
			setCompletionError(e);
		}
	}

	protected void parseJson(JsonNode node, ResourceType type) throws ScimException {
		if (node.isArray()) {
			setCompletionError(new InvalidSyntaxException(
					"Detected array, expecting JSON object for SCIM Create request."));
			return;
		}

		try {
			this.newResource = new ScimResource(this.sconfig, node, null, type.getTypePath());
		} catch (ConflictException | SchemaException | ParseException e) {
			ScimException se;
			if (e instanceof ParseException) {
				se = new InvalidSyntaxException(
						"JSON Parsing error found parsing SCIM request: "
								+ e.getLocalizedMessage(), e);
			} else
				se = (ScimException) e;
			logger.info(se.getMessage(), e);
			setCompletionError(se);
			return;
		}
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
	protected void doOperation() throws ScimException {

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
			return;
		}
	}

}
