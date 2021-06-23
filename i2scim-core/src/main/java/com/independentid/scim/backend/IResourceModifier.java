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

package com.independentid.scim.backend;

import com.independentid.scim.core.err.PreconditionFailException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaException;

/**
 * IResourceModifier defines an interface to a <code>ScimResource</code> which can 
 * be used to update or replace a resource.
 * @author pjdhunt
 *
 */
public interface IResourceModifier {

	/**
	 * Accepts a SCIM Patch request (similar to JSON Patch) and processes against
	 * the current ScimResource. If all operations succeed, the current scim object
	 * is modified (but not yet persisted) and true is returned. Processing is subject to
	 * attribute field mutability.
	 * @param req A <code>JsonPatchRequest</code> object containing the SCIM Patch request to be performed
	 * @param ctx The <code>RequestCtx</code> providing SCIM request parameters and headers
	 * @throws ScimException general processing error such as precondition etc.
	 * @throws SchemaException occurs when a violation of scim schema occurs
	 */
	void modifyResource(JsonPatchRequest req, RequestCtx ctx) throws ScimException;
	
	/**
	 * Attempts to replace the current resource (subject to attribute mutability) with the specified
	 * parsed resource object.  Non-mutable fields are left untouched.
	 * @param res A parsed ScimResource from the inbound SCIM request to potentially replace the current object
	 * @param ctx The RequestCtx (SCIM params and headers)
	 * @return True if the inbound ScimResource was used to replace the current resource.
	 * @throws ScimException when a processing error occurs
	 */
	boolean replaceResAttributes(ScimResource res, RequestCtx ctx) throws ScimException;
	
	/**
	 * @return true if the resource has been modified and not yet persisted.
	 */
	boolean isModified();
	
	/**
	 * Checks the <code>RequestCtx</code> for If-Match and If-Unmodified-Since preconditions
	 * @param ctx A RequestCtx containing the request headers
	 * @return true if preconditions fail
	 */
	boolean checkModPreConditionFail(RequestCtx ctx) throws PreconditionFailException;

	/**
	 * Checks the <code>RequestCtx</code> for If-Not-Match and If-Modified-Since preconditions
	 * with the current resource for a match. If not matched, false is returned.
	 * @param ctx A RequestCtx containing the request headers
	 * @return true if preconditions fail
	 */
	boolean checkGetPreConditionFail(RequestCtx ctx) throws PreconditionFailException;
}
