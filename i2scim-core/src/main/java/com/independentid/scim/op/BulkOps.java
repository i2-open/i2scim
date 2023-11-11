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
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.core.err.TooLargeException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.SchemaException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * @author pjdhunt
 *
 */
public class BulkOps extends Operation implements IBulkIdResolver {

	private final static Logger logger = LoggerFactory.getLogger(BulkOps.class);

	private static final long serialVersionUID = 794465867214870343L;
	public static final String FAIL_ON_ERRORS = "failOnErrors";
	public static final String PREFIX_BULKID = "bulkid:";
	public static final String PARAM_BULKID = "bulkid";
	public static final String PARAM_METHOD = "method";
	public static final String PARAM_PATH = "path";
	public static final String PARAM_DATA = "data";
	public static final String PARAM_VERSION = "version";
	public static final String PARAM_SEQNUM = "seq";
	public static final String PARAM_ACCEPTDATE = "accptd";
	public static final String PARAM_TRANID = "tid";

	protected RequestCtx ctx;
	protected final ArrayList<Operation> ops;
	protected final HashMap<String, Operation> bulkMap;
	protected final HashMap<Operation, List<String>> bulkValMap;
	
	protected int opCompleted = 0, opFailed = 0, opRequested = 0;
	protected int failOnErrors = 0;


	public BulkOps(HttpServletRequest req, HttpServletResponse resp) {
		super (req,resp);
		this.ops = new ArrayList<>();
		this.bulkMap = new HashMap<>();
		this.bulkValMap = new HashMap<>();

	}

	public BulkOps(JsonNode node, RequestCtx ctx) {
		super(ctx, 0);

		this.node = node;
		this.ops = new ArrayList<>();
		this.bulkMap = new HashMap<>();
		this.bulkValMap = new HashMap<>();

	}

	@Override
	protected void doPreOperation() {
		parseRequestUrl();
		if (opState == OpState.invalid)
			return;
		parseRequestBody();
		if (opState == OpState.invalid)
			return;
		parseJson(node);

	}

	public void parseJson(JsonNode node) {
		JsonNode snode = node.get(ScimParams.ATTR_SCHEMAS);
		if (snode == null) {
			setCompletionError(new SchemaException("JSON is missing 'schemas' attribute."));
			return;
		}

		boolean invalidSchema = true;
		if (snode.isArray()) {
			Iterator<JsonNode> jiter = snode.elements();
			while (jiter.hasNext() && invalidSchema) {
				JsonNode anode = jiter.next();
				if (anode.asText().equalsIgnoreCase(
						ScimParams.SCHEMA_API_BulkRequest))
					invalidSchema = false;
			}
		} else if (snode.asText().equalsIgnoreCase(
				ScimParams.SCHEMA_API_BulkRequest))
			invalidSchema = false;
	
		if (invalidSchema) {
			setCompletionError(new SchemaException(
					"Expecting JSON with schemas attribute of: "
							+ ScimParams.SCHEMA_API_BulkRequest));
			return;
		}

		this.failOnErrors = configMgr.getBulkMaxErrors();
		JsonNode fnode = node.get(FAIL_ON_ERRORS);
		if (fnode != null) {
			this.failOnErrors = fnode.asInt();
		}
			
		JsonNode opsnode = node.get(ScimParams.ATTR_PATCH_OPS);
		if (opsnode == null) {
			setCompletionError(new SchemaException("Missing 'Operations' attribute array."));
			return;
		}
		if (!opsnode.isArray()) {
			setCompletionError(new SchemaException("Expecting 'Operations' to be an array."));
			return;
		}
	
		int requestNum = 0;
		int maxOps = configMgr.getBulkMaxOps();
		Iterator<JsonNode> oiter = opsnode.elements();
		while (oiter.hasNext()) {
			JsonNode oper = oiter.next();
			requestNum++;
			if (requestNum > maxOps) {
				setCompletionError(new TooLargeException(
						"Bulk request exceeds server maximum operations of "
								+ maxOps));
				return;
			}

			Operation op;
			try {
				op = parseOperation(oper,this,requestNum, false);
			} catch (ScimException e) {
				setCompletionError(e);
				return;
			}

			this.ops.add(op); // add to the list of operations

			String bulkId = op.getBulkId();
			if (bulkId != null) {
				// Check if this is a repeat bulkId
				if (this.bulkMap.containsKey(bulkId)) {
					setCompletionError(new ConflictException("Detected repeated bulkId "
							+ bulkId + " at operation number " + requestNum));
					return;
				}
				this.bulkMap.put(bulkId, op);
			}
	
			List<String> bulkVals = op.getBulkIdsRequired();
			if (bulkVals != null && bulkVals.size() > 0) {
				this.bulkValMap.put(op, bulkVals);
			}
		}
	
		this.opRequested = this.ops.size();

		try {
			validateBulkIds();
		} catch (ScimException e) {
			setCompletionError(e);
			return;
		}


		JsonNode feNode = node.get(FAIL_ON_ERRORS);
		if (feNode != null) {
			if (!feNode.isInt()) {
				setCompletionError(new SchemaException(
						"Expecting 'failOnErrors' to be an integer value."));
				return;
			}
			this.failOnErrors = feNode.asInt();
		}
	}

	/**
	 * This routine checks if b depends on a. The assumption is that a already
	 * depends on b
	 * 
	 * @param a Scim operation A.
	 * @param b Scim operation B.
	 * @throws ScimException Exception thrown if a circular/conflict reference is detected.
	 */
	protected void checkCircular(Operation a, Operation b) throws ScimException {
		// We know a references b. Does b reference a?

		List<String> vals = this.bulkValMap.get(b);
		if (vals == null)
			return;
		for (String bvalue : vals) {
			// Check that there is an operation that defines the value
			if (this.bulkMap.containsKey(bvalue)) {
				Operation op = this.bulkMap.get(bvalue);
				if (op == a)
					throw new ConflictException(
							"Operation references non-existend bulkId: "
									+ bvalue);
			}

		}
	}

	protected void validateBulkIds() throws ScimException {

		// First, check that every value specified has a corresponding bulkdId
		// transtraction

		for (Operation op : this.bulkValMap.keySet()) {
			List<String> vals = this.bulkValMap.get(op);
			if (vals == null)
				continue;
			for (String bvalue : vals) {
				// Check that there is an operation that defines the value
				if (!this.bulkMap.containsKey(bvalue))
					throw new ConflictException(
							"Operation references non-existend bulkId: "
									+ bvalue);

				// Check for a circular reference
				Operation bop = this.bulkMap.get(bvalue);
				checkCircular(op, bop);
				// Add the prerequisite operation to the current operation
				op.addPrerequisiteOperation(bop);

			}
		}
		
	}

	public static Operation parseOperation(
			JsonNode bulkOpNode, BulkOps parent, int requestNum, boolean isReplicOp) throws ScimException {
		if (schemaManager == null) {
			logger.info("Schema manager was NULL attempting to fix...");
			schemaManager = configMgr.getSchemaManager();
			if (schemaManager == null)
				logger.error("SchemaManager is still NULL");
		}
		RequestCtx octx = new RequestCtx(bulkOpNode, schemaManager, isReplicOp);

		JsonNode item = bulkOpNode.get(BulkOps.PARAM_TRANID);
		if (item == null) {
			throw new SchemaException(
					"Bulk request for POST/PUT/PATCH missing required attribute 'tid'.");
		}
		octx.setTranId(item.asText());

		item = bulkOpNode.get(PARAM_METHOD);
		if (item == null)
			throw new SchemaException(
					"Bulk request missing "+PARAM_METHOD+ "parameter.");
		String method = item.asText();

		item = bulkOpNode.get(PARAM_DATA);
		if (item == null) {
			if (!octx.getBulkMethod().equals(Operation.Bulk_Method_DELETE))
				throw new SchemaException(
						"Bulk request for POST/PUT/PATCH missing required attribute 'data'.");
		}

		Operation op = null;
		switch (method) {
		case Operation.Bulk_Method_POST:
			op = new CreateOp(item, octx, parent, requestNum);
			break;
		case Operation.Bulk_Method_PUT:
			op = new PutOp(item, octx, parent, requestNum);
			break;
		case Operation.Bulk_Method_PATCH:
			op = new PatchOp(item, octx, parent, requestNum);
			break;
		case Operation.Bulk_Method_DELETE:
			op = new DeleteOp(octx, parent, requestNum);
		}

		return op;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.independentid.scim.op.IBulkIdResolver#translateId(java.lang.String)
	 */
	@Override
	public String translateId(String bulkId) {
		if (bulkId == null)
			return null;

		String id;
		if (bulkId.toLowerCase().startsWith(PREFIX_BULKID))
			id = bulkId.substring(7);
		else
			return bulkId;
		Operation op = this.bulkMap.get(id);
		return (op == null) ? null : op.getResourceId();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.independentid.scim.op.IBulkIdResolver#translateRef(java.lang.String)
	 */
	@Override
	public String translateRef(String bulkId) {
		if (bulkId == null)
			return null;

		String id;
		if (bulkId.toLowerCase().startsWith(PREFIX_BULKID))
			id = bulkId.substring(7);
		else
			return bulkId;
		Operation op = this.bulkMap.get(id);
		return (op == null) ? null : op.getLocation();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.independentid.scim.op.Operation#doOperation()
	 */
	@Override
	protected void doOperation() {
		int batchExecNum = 0;
		if (logger.isDebugEnabled()) {
			logger.debug("Processing BATCH request with "+this.ops.size()+" operations.");
		}
		for (Operation op : this.ops) {
			if (!op.isDone()) {
				OpStat stat = op.getStats();
				stat.setBulkExecNumber(batchExecNum++);
				op.compute();
			}

			if (op.isError())
				this.opFailed++;
			else
				this.opCompleted++;
			if (this.opFailed >= this.failOnErrors)
				break; // stop processing as we have had too many errors
		}
		
		if (logger.isDebugEnabled()) {
			String buf = "Bulk Ops Requesed: " + this.opRequested +
					", Completed: " + this.opCompleted +
					", Failed: " + this.opFailed;
			logger.debug(buf);
			logger.debug("=========End BATCH Request==========");
		}
	}

}
