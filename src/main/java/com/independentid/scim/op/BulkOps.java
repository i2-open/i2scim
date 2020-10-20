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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ConflictException;
import com.independentid.scim.server.InvalidSyntaxException;
import com.independentid.scim.server.PoolManager;
import com.independentid.scim.server.ScimException;
import com.independentid.scim.server.TooLargeException;

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
	
	protected static ConfigMgr cfg = ConfigMgr.getInstance();

	protected static PoolManager pool = PoolManager.getInstance();
	
	protected RequestCtx ctx;
	protected ArrayList<Operation> ops;
	protected HashMap<String, Operation> bulkMap;
	protected HashMap<Operation, List<String>> bulkValMap;
	//protected BackendHandler handler;
	
	protected int opCompleted = 0, opFailed = 0, opRequested = 0;

	

	protected int failOnErrors = 0;

	public BulkOps(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		super (req,resp,false);
		
		this.ops = new ArrayList<Operation>();
		this.bulkMap = new HashMap<String, Operation>();
		this.bulkValMap = new HashMap<Operation, List<String>>();
		
		ServletInputStream input = req.getInputStream();
		if (input == null) {
			logger.warn("Missing body for Bulk request received");
			setCompletionError(new InvalidSyntaxException(
					"Request body missing."));
			return;
		}
		ObjectMapper mapper = JsonUtil.getMapper();
		JsonNode node;
		try {
			node = mapper.readTree(input);
		} catch (Exception e) {
			logger.info(
					"JSON Parsing error parsing patch request: "
							+ e.getMessage(), e);
			setCompletionError(new InvalidSyntaxException(
					"JSON parsing error found parsing SCIM BULK request: "
							+ e.getLocalizedMessage(), e));
			return;
		}
		
		try {
			parseJson(node, null);
		} catch (ScimException | SchemaException e) {
			// TODO Auto-generated catch block
			setCompletionError(new InvalidSyntaxException("SCIM operation parsing error found parsing SCIM BULK request: "+e.getLocalizedMessage(),e));
			return;
		}
		
		
	}
	/**
	 * @throws ScimException
	 * 
	 */
	public BulkOps(JsonNode node, RequestCtx ctx) {
		super(ctx, 0);
		this.ops = new ArrayList<Operation>();
		this.bulkMap = new HashMap<String, Operation>();
		this.bulkValMap = new HashMap<Operation, List<String>>();
		
		try {
			parseJson(node, null);
		} catch (ScimException | SchemaException e) {
			// TODO Auto-generated catch block
			setCompletionError(new InvalidSyntaxException("SCIM operation parsing error found parsing SCIM BULK request: "+e.getLocalizedMessage(),e));
			return;
		}
	}

	public void parseJson(JsonNode node, ResourceType type) throws ScimException, SchemaException {
		JsonNode snode = node.get("schemas");
		if (snode == null)
			throw new SchemaException("JSON is missing 'schemas' attribute.");
	
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
	
		if (invalidSchema)
			throw new SchemaException(
					"Expecting JSON with schemas attribute of: "
							+ ScimParams.SCHEMA_API_BulkRequest);
	
		this.failOnErrors = this.sconfig.getBulkMaxErrors();
		JsonNode fnode = node.get("failOnErrors");
		if (fnode != null) {
			this.failOnErrors = fnode.asInt();
		}
			
		JsonNode opsnode = node.get("Operations");
		if (opsnode == null)
			throw new SchemaException("Missing 'Operations' attribute array.");
	
		if (!opsnode.isArray()) {
			throw new SchemaException("Expecting 'Operations' to be an array.");
		}
	
		int requestNum = 0;
		int maxOps = this.sconfig.getBulkMaxOps();
		Iterator<JsonNode> oiter = opsnode.elements();
		while (oiter.hasNext()) {
			JsonNode oper = oiter.next();
			requestNum++;
			if (requestNum > maxOps)
				throw new TooLargeException(
						"Bulk request exceeds server maximum operations of "
								+ maxOps);
			
			Operation op = parseOperation(oper,	requestNum);
			
			String key = op.getBulkId();
			if (key == null)
				key = UUID.randomUUID().toString();
			this.ops.add(op); // add to the list of operations
			
	
			String bulkId = op.getBulkId();
			if (bulkId != null) {
				// Check if this is a repeat bulkId
				if (this.bulkMap.containsKey(bulkId))
					throw new ConflictException("Detected repeated bulkId "
							+ bulkId + " at operation number " + requestNum);
				this.bulkMap.put(bulkId, op);
			}
	
			List<String> bulkVals = op.getBulkIdsRequired();
			if (bulkVals != null && bulkVals.size() > 0) {
				this.bulkValMap.put(op, bulkVals);
			}
		}
	
		this.opRequested = this.ops.size();
		
		validateBulkIds();
		
	
		JsonNode feNode = node.get("failOnErrors");
		if (feNode != null) {
			if (!feNode.isInt())
				throw new SchemaException(
						"Expecting 'failOnErrors' to be an integer value.");
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
	 * @param a
	 * @param b
	 * @throws ScimException
	 */
	protected void checkCircular(Operation a, Operation b) throws ScimException {
		// We know a references b. Does b reference a?

		List<String> vals = this.bulkValMap.get(b);
		if (vals == null)
			return;
		Iterator<String> viter = vals.iterator();
		while (viter.hasNext()) {
			String bvalue = viter.next();
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

		Iterator<Operation> oiter = this.bulkValMap.keySet().iterator();
		while (oiter.hasNext()) {
			Operation op = oiter.next();
			List<String> vals = this.bulkValMap.get(op);
			if (vals == null)
				continue;
			Iterator<String> viter = vals.iterator();
			while (viter.hasNext()) {
				String bvalue = viter.next();
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
			JsonNode bulkOpNode, int requestNum) throws ScimException,
			SchemaException {
		RequestCtx octx = new RequestCtx(bulkOpNode);

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

		String id = bulkId;
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

		String id = bulkId;
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
	protected void doOperation() throws ScimException {
		int batchExecNum = 0;
		if (logger.isDebugEnabled()) {
			logger.debug("========Begin BATCH Request========");
		}
		Iterator<Operation> iter = this.ops.iterator();
		while (iter.hasNext()) {
			Operation op = iter.next();
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
			StringBuffer buf = new StringBuffer();
			buf.append("Bulk Ops Requesed: ").append(this.opRequested);
			buf.append(", Completed: ").append(this.opCompleted);
			buf.append(", Failed: ").append(this.opFailed);
			logger.debug(buf.toString());
			logger.debug("=========End BATCH Request==========");
		}
	}

}
