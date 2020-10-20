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
import java.text.ParseException;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.server.InternalException;
import com.independentid.scim.server.InvalidSyntaxException;
import com.independentid.scim.server.NotFoundException;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */
public class PatchOp extends Operation implements IBulkOp {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2989171950215028209L;

	private final static Logger logger = LoggerFactory.getLogger(PatchOp.class);

	private BulkOps parent;
	private JsonPatchRequest preq;
	

	/**
	 * Used in bulk requests for Patch requests. Provide the JsonNode of the
	 * data element of a bulk operation
	 * 
	 * @param data
	 *            The JsonNode of a data element of a SCIM Bulk operation
	 * @param ctx
	 *            The associated bulk operation RequestCtx
	 * @param handler
	 * @throws IOException
	 */
	public PatchOp(JsonNode data, RequestCtx ctx, BulkOps parent, int requestNum) {
		super(ctx, requestNum);
		this.parent = parent;
		parseJson(data, null);
	}

	/**
	 * A SCIM Patch Operation. The constructor accepts the HttpServletRequest
	 * and parses the patch operation in the constructor. If successfully
	 * parsed, the runnable portion executes the operation.
	 * 
	 * @param req
	 * @param resp
	 * @throws IOException
	 */
	public PatchOp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		super(req, resp, false);

		this.parent = null;
		
		ResourceType type = getResourceType();
		if (type != null) {
			ServletInputStream input = req.getInputStream();
			if (input == null) {
				logger.warn("Missing body for PATCH request received");
				setCompletionError(new InvalidSyntaxException(
						"Request body missing."));
				return;
			}

			JsonNode node;
			try {
				node = JsonUtil.getJsonTree(input);
			} catch (Exception e) {
				if (logger.isDebugEnabled())
					logger.debug(
						"JSON Parsing error parsing patch request: "
								+ e.getMessage(), e);
				setCompletionError(new InvalidSyntaxException(
						"JSON parsing error found parsing SCIM PATCH request: "
								+ e.getLocalizedMessage(), e));
				return;
			}

			parseJson(node, type);
		} else {
			// The endpoint is not supported. Return not found error.
			// ScimResponse e = new
			// ScimResponse(ScimResponse.ST_NOTFOUND,"Resource endpoint not supported",null);
			// TODO Is there a way to tell the difference between not
			// implemented and not found???

			ScimException se = new NotFoundException(
					"Resource endpoint not found or implemented");
			logger.info(se.getMessage());
			setCompletionError(se);
			return;
		}
	}

	protected void parseJson(JsonNode node, ResourceType type) {
		try {

			if (node.isArray()) {
				setCompletionError(new InvalidSyntaxException(
						"Detected array, expecting JSON object for SCIM PATCH request."));
				return;
			}
			this.preq = new JsonPatchRequest(this.sconfig, node, ctx);

		} catch (SchemaException | ParseException e) {
			ScimException se;
			if (e instanceof ParseException) {
				se = new InvalidSyntaxException(
						"JSON Parsing error found parsing PATCH request: "
								+ e.getLocalizedMessage(), e);
			} else
				se = new InvalidSyntaxException(e.getLocalizedMessage(), e);
			if (logger.isDebugEnabled())
				logger.debug("Error parsing PATCH request: "+se.getMessage(), e);
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
			this.scimresp = getHandler().patch(this.ctx, this.preq);

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
			return;
		}
		return;
	}

}
