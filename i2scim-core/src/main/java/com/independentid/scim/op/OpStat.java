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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

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
	 * @param completionError A flag indicating if there was an error on completion
	 */
	public void completeOp(boolean completionError) {
		this.executionNum = OpStat.getExecutionNum();
		this.completionError = completionError;
		this.finishDate = Instant.now();
	}
	
	public boolean isCompletedWithError() {
		return this.completionError;
	}
	
	public String getStartDateStr() {
		return this.receiveDate.toString();
	}
	
	public String getFinishDateStr() {
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
		buf.append(",\tReceived: ").append(getStartDateStr());
		buf.append(",\tFinished: ").append(getFinishDateStr());
		buf.append(",\tElapsed: ").append(getElapsed());
		if (this.bulkRequestNum != -1) {
			buf.append("\nBulk Req Num: ").append(this.bulkRequestNum);
			buf.append(",\tBulk Exec Num: ").append(this.bulkExecutionNum);
		}
		return buf.toString();
	}

	public Integer getRequestNumber() { return this.requestNum; }

	public Date getStartDate() { return Date.from(this.receiveDate);}

	public Date getFinishDate() { return Date.from(this.finishDate);}

	protected static synchronized int getRequestNum() {
		return OpStat.requestCounter++;
	}
	
	public static synchronized int getExecutionNum() {
		return OpStat.execCounter++;
	}
}
