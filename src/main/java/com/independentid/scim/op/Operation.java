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
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RecursiveAction;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ResourceResponse;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.InternalException;
import com.independentid.scim.server.InvalidValueException;
import com.independentid.scim.server.ScimException;

/**
 * Defines a basic SCIM operation. This class is intended to be extended 
 * by each of the various SCIM operations (create, put, delete, patch, 
 * get, search, bulk)
 * @author pjdhunt
 *
 */
public abstract class Operation extends RecursiveAction {
	private final static Logger logger = LoggerFactory.getLogger(Operation.class);

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public final static String Bulk_Method_POST = "POST";
	public final static String Bulk_Method_PUT = "PUT";
	public final static String Bulk_Method_PATCH = "PATCH";
	public final static String Bulk_Method_DELETE = "DELETE";
	
	
	protected final long tCreate = System.nanoTime();

	public final static String SCIM_OP_ATTR = "SCIM_OP";
	
	public static enum OpState {pending, preOp, executing, postOp, done, invalid, fatal };

	protected OpState state;
	private OpState finalState = OpState.pending;
	private OpStat stats; //Used for tracking operation nos and timing
	private HttpServletRequest req;
	private HttpServletResponse resp;
	
	
	protected ConfigMgr sconfig = ConfigMgr.getInstance();
	
	
	protected BackendHandler handler = BackendHandler.getInstance();
	
	protected RequestCtx ctx;
	protected Date start = new Date();
	protected Date end = null;
	private Exception err = null;
	public ScimResponse scimresp = null;
	protected ScimResource newResource = null;
	protected List<String> bulkList = null;
	protected int bulkOpNumber = 0;
	
	protected String bulkExecNumber = null;
	protected ArrayList<Operation> predicateOps;

	/**
	 * Constructor for a SCIM operation. Typically used in bulk requests.
	 * @param ctx The RequestCtx
	 * @param requestNum To be used for tracking bulkId request numbers in a series (TBI)
	 * @throws IOException
	 */
	public Operation(RequestCtx ctx, int requestNum) {
		this.stats = new OpStat(requestNum); // Start collecting stats as part of a bulk request.
		this.state = OpState.pending;
		this.ctx = ctx;
		this.req = null;
		this.resp = null; 
		
	
		this.bulkOpNumber = requestNum;
		this.predicateOps = new ArrayList<Operation>();
		
	}

	
	/**
	 * Constructor for a SCIM operation. 
	 * @param req The HttpServletRequest
	 * @param resp The HttpServiceResponse
	 * @param parseRequestBody True indicates the request body contains search parameters
	 * @throws IOException
	 */
	public Operation(HttpServletRequest req, HttpServletResponse resp, boolean parseRequestBody) {	
		this.stats = new OpStat(); // Collect stats as part of a normal single request operation
		this.state = OpState.pending;
		this.req = req;
		this.resp = resp;
		
		//this.handler = handler;
		this.predicateOps = new ArrayList<Operation>();
		try {
			this.ctx = new RequestCtx(req, resp, parseRequestBody);
		} catch (ScimException e) {
			this.setCompletionError(e);
			this.state = OpState.invalid;
		} catch (IOException e) {
			setCompletionError(new InternalException("Error reading input: "+e.getMessage(),e));
			this.state = OpState.invalid;
		}
		
		req.setAttribute(SCIM_OP_ATTR, this);
	}
	
	protected abstract void parseJson(JsonNode node, ResourceType type) throws ScimException, SchemaException;
	
	/**
	 * @return The current runnable execution state. Valid states are
	 * 'pending', 'preOp', 'executing', 'postOp', 'done', 'invalid', 'fatal'
	 */
	public OpState getStatus() {
		return this.state;
	}
	
	public boolean isRunning() {
		return (this.state == OpState.preOp ||
				this.state == OpState.executing ||
				this.state == OpState.postOp);
	}
	
	/**
	 * @return Returns the last successful execution state
	 */
	public OpState getFinalStatus() {
		return this.finalState;
	}
	
	/**
	 * @return The original HttpServletRequest object
	 */
	public HttpServletRequest getRequest() {
		return this.req;
	}
	
	public RequestCtx getRequestCtx() {
		return this.ctx;
	}
	
	public String getBulkId() {
		if (this.ctx == null) return null;
		return this.ctx.getBulkId();
	}
	
	
	/**
	 * @return The HttpServletResponse object
	 */
	public HttpServletResponse getResponse() {
		return this.resp;
	}
	
	/**
	 * @return The ConfigMgr of the SCIM server that is handling this request.
	 */
	public ConfigMgr getConfigMgr() {
		return this.sconfig;
	}
	
	/**
	 * @return Returns the RequestCtx which represents the parsed SCIM request URL and params
	 */
	public RequestCtx getScimRequestCtx() {
		return this.ctx;
	}
	
	/**
	 * Convenience method that creates a new generator for the current SCIM server
	 * @param compact Indicates whether results should be compact or pretty form
	 * @return A JsonGenerator object used to create a Json structure
	 * @throws IOException
	 */
	protected JsonGenerator getGenerator(boolean compact) throws IOException {
		if (resp ==  null) 
			return null;
		return JsonUtil.getGenerator(resp.getWriter(), compact);
	}
	
	/**
	 * @return Returns the request ResourceType based on the endpoint of the request or null.
	 */
	public ResourceType getResourceType() {
		return this.sconfig.getResourceTypeByPath(ctx.getResourceContainer());
	}
	
	/**
	 * Automatically generates the appropriate response (success or failure)
	 * and adds it to the current JsonGenerator. The caller will eventually
	 * need to flush and close the generator.
	 * @param gen
	 * @throws IOException
	 */
	public void doResponse(JsonGenerator gen) throws IOException {
		if (isError()) {
			doFailure(gen);
		} else
			doSuccess(gen);
	}
	
	
	/**
	 * This will add this operation's success response to the current JsonGenerator.
	 * The caller will eventually need to flush & close the generator.
	 * @param gen JsonGenerator object
	 * @throws IOException
	 */
	public void doSuccess(JsonGenerator gen) throws IOException {
		
		this.scimresp.serialize(gen, ctx); 
	}
	
	/**
	 * This will add this operation's failure response to the current JsonGenerator.
	 * The caller will eventually need to flush & close the generator.
	 * @param gen JsonGenerator object
	 * @throws IOException
	 */
	public void doFailure(JsonGenerator gen) throws IOException {
		if (this.err instanceof ScimException) {
			ScimException se = (ScimException) this.err;
			se.serialize(gen, resp);
		} else {
			// This should not happen?
			logger.error("Unexpected error in result was not of type ScimException: "+this.err.getMessage(),this.err);
			doErrorResp(this.err,400,this.err.getMessage(),gen);
		}
	}
	
	
	private void doErrorResp(Exception ex, int httpErrNum, String scimErrMsg, JsonGenerator gen) {
		try {
			ScimResponse sresp = new ScimResponse(httpErrNum,ex.getMessage(),scimErrMsg);
			sresp.serialize(gen, null, false);
			this.resp.setStatus(sresp.getStatus());
		} catch (IOException e) {
			logger.error("Error generating SCIM response.",e);
			this.err = e;
		}	
	}
	
	protected BackendHandler getHandler() {
		
		return this.handler;
	}
	
	protected abstract void doOperation() throws ScimException;
	
	protected void doPreOperation() throws ScimException {
		
	}
	
	protected void doPostOperation() throws ScimException {
		
	}
	
	/**
	 * @return true if this request completed with an error.
	 */
	public boolean isError () {
		return (this.err != null);
	}
	
	protected void setCompletionError(Exception e) {
		this.err = e;
		// Set the thread as having completed with an exception
	    // TODO What happens if this is invoked prior to execution or after?
		//if (isRunning())
		//	super.completeExceptionally(e);
		//this.stats.completeOp(true);
	}
	
	public Exception getCompletionError() {
		return this.err;
	}
	
	protected void doPredicateOperations() {
		if (this.predicateOps == null ||
				this.predicateOps.size() == 0) return;
		
		int execNum = this.stats.getBulkExecNumber();
		Iterator<Operation> iter = this.predicateOps.iterator();
		while (iter.hasNext()) {
			Operation op = iter.next();
			if (!op.isDone()) {
				OpStat ostat = op.getStats();
				ostat.setBulkExecNumber(execNum);
				execNum++;
				op.compute();
			}
			if (op.isError() || op.getLocation() == null)
				this.setCompletionError(new InvalidValueException("BulkId could not be resolved due to prior error."));
			this.stats.setBulkExecNumber(execNum);
		}
	}
	
	public void compute() {

		try {
			
			if (this.predicateOps.size() > 0)
				doPredicateOperations();
			
			if (this.isError())
				return; // Nothing to do, parsing error
			
			this.state = OpState.preOp;

			// Check that predicate oeprations have completed. If not, wait.
			/*
			if (this.predicateOps != null) {
				while (this.predicateOps.size() > 0) {
					Iterator<Operation> oiter = this.predicateOps.iterator();
					while (oiter.hasNext()) {
						Operation pop = oiter.next();
						if (pop.isCompletedAbnormally()) {
							this.setCompletionError(new ScimException(
									"Was unable to execute due to bulkId dependence."
											+ pop.getBulkId()));
							return;
						}
						if (!pop.isDone())
							try {
								pop.wait(100);
							} catch (InterruptedException e) {
								// ignore
							}
						else {
							oiter.remove(); // The operation is done so nothing
											// to wait for.
						}
					}
				}
			}
			*/
			
			if (logger.isTraceEnabled())
				logger.trace("Start operation pre-proccesing ["
						+ getClass().getSimpleName() + "]");
			doPreOperation();

			this.finalState = this.state;
			this.state = OpState.executing;
			if (logger.isTraceEnabled())
				logger.trace("Starting operation ["
						+ getClass().getSimpleName() + "]");
			doOperation();

			this.finalState = this.state;
			this.state = OpState.postOp;
			if (logger.isTraceEnabled())
				logger.trace("Start operation post-proccesing ["
						+ getClass().getSimpleName() + "]");
			doPostOperation();
		} catch (ScimException e) {
			this.finalState = this.state;
			this.state = OpState.fatal;
			this.err = e;
		}
		// Mark the request completed.
		this.stats.completeOp(isError());
		if (logger.isDebugEnabled()) {
			logger.debug(this.toString());
			logger.debug("Op Stats: "+this.stats.toString());
		}
	}

	public String toString() {
		StringBuffer buf = new StringBuffer("Op: ");
		buf.append(this.getClass().getSimpleName());
		buf.append(", State: ").append(this.state).append(", ").append(this.start.toString());
		return buf.toString();
	}
	
	public String getResourceId() {
		if (isError()) return null;
		if (isDone()) {
			if (this.scimresp == null)
				return null;
			if (this.scimresp instanceof ListResponse) {
				ListResponse lresp = (ListResponse) this.scimresp;
				return lresp.getId();
			} else if (this.scimresp instanceof ResourceResponse) {
				ResourceResponse rresp = (ResourceResponse) this.scimresp;
				return rresp.getId();
			}
		}
		
		return null;
	}
	
	public String getLocation() {
		if (isError()) return null;
		if (isDone()) {
			if (this.scimresp == null)
				return null;
			if (this.scimresp instanceof ListResponse) {
				ListResponse lresp = (ListResponse) this.scimresp;
				return lresp.getLocation();
			}			
		}
		
		return null;
	}
	
	public ScimResource getTransactionResource() {
		return this.newResource;
	}
	
	public boolean hasBulkIdValues() {
		// Does this operation have a body? (e.g. not for delete)
		if (this.newResource == null) 
			return false;
		
		if (this.bulkList != null)
			return (this.bulkList.size() > 0);
		
		// First time run?  Process and obtain the bulkIds (which are stored in bulkList)
		getBulkIdsRequired();
		
		if (this.bulkList != null)
			return (this.bulkList.size() > 0);	

		return false;
	}
	
	public List<String> getBulkIdsRequired() {
		if (this.newResource == null)
			return null;
		
		if (this.newResource instanceof IBulkIdTarget) {
			if (this.bulkList != null) 
				return this.bulkList;
			this.bulkList = new ArrayList<String>();
			IBulkIdTarget bres = (IBulkIdTarget) this.newResource;
			bres.getBulkIdsRequired(this.bulkList);
		}
		return this.bulkList;
	}
	
	public int getBulkOpNumber() {
		return this.bulkOpNumber;
	}
	
	/**
	 * Adds an operation which must be completed before the current operation
	 * may proceed. Usually these are operations for which the current operation
	 * has bulkId values that must be resolved. When set, the current operation
	 * will check the prerequisite operation to see if it is complete. If not
	 * complete, the current operation will call the pre-req operation first 
	 * during the compute/doOperation phase.
	 * 
	 * @param op
	 *            The SCIM operation (bulk) to be executed prior to the current
	 *            operation.
	 * @throws ScimException
	 */
	public synchronized void addPrerequisiteOperation(Operation op) throws ScimException {
		// This should not happen. Only happens if a bulk op invokes the wrong operation constructor.
		if (this.predicateOps == null)
			throw new ScimException("Unexpected dependend operation detected for non-bulk operation.");
		
		this.predicateOps.add(op);
	}
	
	/**
	 * Tests whether the current operation depends on the operation supplied (is a predicate).
	 * @param op An Operation value that should execute before the current one.
	 * @return True if the operation is defined as a predicate operation.
	 * @throws ScimException
	 */
	public boolean isChildOperation(Operation op) throws ScimException {
		// This should not happen. Only happens if a bulk op invokes the wrong
		// operation constructor.
		if (this.predicateOps == null)
			throw new ScimException(
					"Unexpected dependend operation detected for non-bulk operation.");
		return this.predicateOps.contains(op);
	}
	
	public OpStat getStats() {
		return this.stats;
	}

}
