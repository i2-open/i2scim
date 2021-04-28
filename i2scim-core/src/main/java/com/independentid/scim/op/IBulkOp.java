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
import com.independentid.scim.core.err.ScimException;

import java.util.List;

/**
 * @author pjdhunt
 *
 */
public interface IBulkOp {
	
	boolean hasBulkIdValues();

	List<String> getBulkIdsRequired();
	
	/**
	 * Tests whether the current operation depends on the operation supplied (is a predicate).
	 * @param op An Operation value that should execute before the current one.
	 * @return True if the operation is defined as a predicate operation.
	 * @throws ScimException may be thrown when a SCIM error occurs (not a child?)
	 */
	boolean isChildOperation(Operation op) throws ScimException;

	BulkOps getParentBulkRequest();

	/**
	 * Generates a bulk request "operation" for the operation performed. Used to propagate replication ops. Note
	 * that for request, the post-add result is sent to capture generated "id".
	 * @return A JsonNode representation of operation in Bulk "Operation" format as described in RFC7644 Sec 3.7. If the
	 * operation did not complete normally, the request returns NULL (because the operation was not processed).
	 */
	JsonNode getJsonReplicaOp();
	
}
