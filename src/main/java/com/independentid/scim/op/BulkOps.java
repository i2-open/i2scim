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
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.core.err.TooLargeException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.SchemaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 794465867214870343L;

	/*
	@Inject
	@Resource(name="PoolMgr")
	protected PoolManager pool;
	*/

	protected RequestCtx ctx;
	protected ArrayList<Operation> ops;
	protected HashMap<String, Operation> bulkMap;
	protected HashMap<Operation, List<String>> bulkValMap;
	//protected BackendHandler handler;
	
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
		JsonNode snode = node.get("schemas");
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
		JsonNode fnode = node.get("failOnErrors");
		if (fnode != null) {
			this.failOnErrors = fnode.asInt();
		}
			
		JsonNode opsnode = node.get("Operations");
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
				op = parseOperation(oper,	requestNum);
			} catch (ScimException e) {
				setCompletionError(e);
				return;
			}

			/*
			String key = op.getBulkId();
			if (key == null)
				key = UUID.randomUUID().toString();
				*/

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


		JsonNode feNode = node.get("failOnErrors");
		if (feNode != null) {
			if (!feNode.isInt()) {
				setCompletionError(new SchemaException(
						"Expecting 'failOnErrors' to be an integer value."));
				return;
			}
			this.failOnErrors = feNode.asInt();
		}
	}

	/*
	private boolean checkReqdBulkIds(HashSet bset, List<String> needed) {
		Iterator<String> iter = needed.iterator();
		while (iter.hasNext()) {
			String bval = iter.next();
			if (!bset.contains(bval))
				return false;
		}
		return true;
	}
	*/

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

	protected Operation parseOperation(
			JsonNode bulkOpNode, int requestNum) throws ScimException {
		RequestCtx octx = new RequestCtx(bulkOpNode, schemaManager);

		JsonNode item = bulkOpNode.get("data");
		if (item == null) {
			if (!octx.getBulkMethod().equals(Operation.Bulk_Method_DELETE))
				throw new SchemaException(
						"Bulk request for POST/PUT/PATCH missing required attribute 'data'.");
		}

		switch (octx.getBulkMethod()) {
		case Operation.Bulk_Method_POST:
			return new CreateOp(item, octx, this, requestNum);

		case Operation.Bulk_Method_PUT:
			return new PutOp(item, octx, this, requestNum);

		case Operation.Bulk_Method_PATCH:
			return new PatchOp(item, octx, this, requestNum);

		case Operation.Bulk_Method_DELETE:
			return new DeleteOp(octx, this, requestNum);

		}
		return null;
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
		if (bulkId.toLowerCase().startsWith("bulkid:"))
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
		if (bulkId.toLowerCase().startsWith("bulkid:"))
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
			logger.debug("========Begin BATCH Request========");
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
