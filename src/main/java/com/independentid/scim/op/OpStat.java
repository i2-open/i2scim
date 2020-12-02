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

import java.time.Duration;
import java.time.Instant;

/**
 * @author pjdhunt
 *
 */
public class OpStat {

	//protected int opCompleted = 0, opFailed = 0, opRequested = 0;

	private static int requestCounter = 0;
	private static int execCounter = 0;
	
	/**
	 * The request number since server startup. The number is assigned based on
	 * the order it was received
	 */
	protected int requestNum;

	/**
	 * The execution number since server startup. Used to compare actual order
	 * of events in a server
	 */
	protected int executionNum;

	/**
	 * The position number of a request received in a Bulk request
	 */
	protected int bulkRequestNum;

	/**
	 * The actual execution number within a bulk request set
	 */
	protected int bulkExecutionNum;

	protected Instant receiveDate;
	protected Instant finishDate;
	
	protected boolean completionError = false;
	
	/**
	 * Used to track statistics and reporting of a normal operation.
	 * 
	 * For bulk operations it tracks request and execution numbers.
	 */
	public OpStat() {
		this.requestNum = OpStat.getRequestNum();
		this.executionNum = -1;
		this.bulkExecutionNum = -1;
		this.bulkRequestNum = -1;
		this.receiveDate = Instant.now();
		this.finishDate = null;
	}

	/**
	 * Used to track statistics and reporting of a normal operation.
	 * 
	 * For bulk operations it tracks request and execution numbers.
	 */
	public OpStat(int bulkRequestNum) {
		this.requestNum = OpStat.getRequestNum();
		this.executionNum = -1;
		this.bulkExecutionNum = -1;
		this.bulkRequestNum = bulkRequestNum;
		this.receiveDate = Instant.now();
		this.finishDate = null;
	}
	
	/**
	 * Marks that the operation is now complete, a finish time, and assigned a system execution number
	 * @param execNum The system execution number (regardless of completion state)
	 */
	public void completeOp(boolean completionError) {
		this.executionNum = OpStat.getExecutionNum();
		this.completionError = completionError;
		this.finishDate = Instant.now();
	}
	
	public boolean isCompletedWithError() {
		return this.completionError;
	}
	
	public String getStartDate() {
		return this.receiveDate.toString();
	}
	
	public String getFinishDate() {
		return (this.finishDate == null)?"":this.finishDate.toString();
	}
	
	public String getElapsed() {
		Duration elapsed = Duration.between(this.receiveDate, this.finishDate);
		
		return elapsed.getSeconds()+"."+elapsed.getNano()+ "secs";
	}
	
	public int getBulkExecNumber() {
		return this.bulkExecutionNum;
	}
	
	public void setBulkExecNumber(int execNum) {
		this.bulkExecutionNum = execNum;
	}
	
	public String toString() {
		StringBuilder buf = new StringBuilder("RequestNum: ");
		buf.append(this.requestNum);
		buf.append(",\tCompleted: ").append(!this.completionError);
		buf.append("\nExec Num: ").append(this.executionNum);
		buf.append(",\tReceived: ").append(getStartDate());
		buf.append(",\tFinished: ").append(getFinishDate());
		buf.append(",\tElapsed: ").append(getElapsed());
		if (this.bulkRequestNum != -1) {
			buf.append("\nBulk Req Num: ").append(this.bulkRequestNum);
			buf.append(",\tBulk Exec Num: ").append(this.bulkExecutionNum);
		}
		return buf.toString();
	}
	
	public static synchronized int getRequestNum() {
		return OpStat.requestCounter++;
	}
	
	public static synchronized int getExecutionNum() {
		return OpStat.execCounter++;
	}
}
