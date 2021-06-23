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
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.InternalException;
import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * The SCIM Search Operation is invoked by an HTTP POST request. Instead of parsing the URL, Search processes the
 * HTTP Payload as per RFC7644 Sec 3.4.3
 * @author pjdhunt
 *
 */
public class SearchOp extends Operation {

	private static final long serialVersionUID = -3586153424932556487L;
	private final static Logger logger = LoggerFactory.getLogger(SearchOp.class);

	/**
	 * @param req HttpServletRequest object
	 * @param resp HttpServletResponse object
	 */
	public SearchOp(HttpServletRequest req,
					HttpServletResponse resp) {
		super(req, resp );

		if (!req.getRequestURI().endsWith(ScimParams.PATH_GLOBAL_SEARCH)) {
			InternalException ie = new InternalException(
					"Was expecting a search request, got: "
							+ req.getRequestURI());
			setCompletionError(ie);
		}
	}

	@Override
	protected void doPreOperation() {
		parseRequestUrl();
		if (opState == OpState.invalid)
			return;

		ServletInputStream bodyStream;
		try {
			bodyStream = getRequest().getInputStream();
			//Because the backendhalder logic just uses RequestCtx, the search body is handled by RequestCtx
			this.ctx.parseSearchBody(bodyStream);
		} catch (IOException | ScimException e) {
			setCompletionError(new InvalidSyntaxException(
					"Unable to parse request body (SCIM JSON Search Schema format expected)."));
			this.opState = OpState.invalid;
		}

	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#doOperation()
	 */
	@Override
	protected void doOperation() {
		try {
			this.scimresp = backendHandler.get(ctx);

		} catch (ScimException e) {
			setCompletionError(e);

		} catch (BackendException e) {
			ScimException se = new InternalException("Unknown backend exception during SCIM Search: "+e.getLocalizedMessage(),e);
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
		// not used. {@link RequestCtx#parseSearchBody} used instead.

	}

}
