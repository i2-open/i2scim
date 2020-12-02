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
	
}
