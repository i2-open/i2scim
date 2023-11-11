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

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.InternalException;
import com.independentid.scim.core.err.NotFoundException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ConfigResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.ResourceType;
import jakarta.enterprise.context.RequestScoped;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pjdhunt
 *
 */
@RequestScoped
public class GetOp extends Operation {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2919810859040965128L;
	private final static Logger logger = LoggerFactory.getLogger(GetOp.class);

	private final static int CONFIG_MAX_RESULTS = 1000;
	/**
	 * @param req The {@link HttpServletRequest} object received by the SCIM Servlet
	 * @param resp The {@link HttpServletResponse} to be returned by the SCIM Servlet
	 */
	public GetOp(HttpServletRequest req, HttpServletResponse resp) {
		super(req, resp);

	}

	public GetOp(RequestCtx ctx, int requestNum) {
		super(ctx,requestNum);
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.Operation#doOperation()
	 */
	@Override
	protected void doOperation() {
		if (this.opState == OpState.invalid)
			return;
		String container = ctx.getResourceContainer();
		// If this is a query to the Schema or ResourceType end points use ConfigResponse
		if (ConfigResponse.isConfigEndpoint(container)) {
			this.scimresp = new ConfigResponse(ctx, configMgr,CONFIG_MAX_RESULTS);
			return;
		} 
		
		// Check if an undefined endpoint was requested.
		ResourceType type = schemaManager.getResourceTypeByPath(container);
		if (type == null) {
			setCompletionError(new NotFoundException("Undefined resource endpoint."));
			return;
		}

		// Pass the request to the backend database handler
		try {
			this.scimresp = backendHandler.get(ctx);
			
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


}
