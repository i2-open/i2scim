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

package com.independentid.scim.backend;

import com.independentid.scim.core.err.PreconditionFailException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaException;

import java.text.ParseException;

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
	 * @throws ScimException
	 * @throws ParseException 
	 * @throws SchemaException 
	 */
	void modifyResource(JsonPatchRequest req, RequestCtx ctx) throws ScimException;
	
	/**
	 * Attempts to replace the current resource (subject to attribute mutability) with the specified
	 * parsed resource object.  Non-mutable fields are left untouched.
	 * @param res A parsed ScimResource from the inbound SCIM request to potentially replace the current object
	 * @param ctx The RequestCtx (SCIM params and headers)
	 * @return True if the inbound ScimResource was used to replace the current resource.
	 * @throws ScimException
	 */
	boolean replaceResAttributes(ScimResource res, RequestCtx ctx) throws ScimException;
	
	/**
	 * @return true if the resource has been modified and not yet persisted.
	 */
	boolean isModified();
	
	/**
	 * Checks the <code>RequestCtx</code> for the "ETag" header. If specified compares
	 * with the current resource for a match. If not matched, false is returned.
	 * @param ctx A RequestCtx containing an etag hash (or null)
	 * @return true if RequestCtx etag is null OR RequestCtx etag matches current resource hash
	 */
	boolean checkPreCondition(RequestCtx ctx) throws PreconditionFailException;
}
