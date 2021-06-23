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
