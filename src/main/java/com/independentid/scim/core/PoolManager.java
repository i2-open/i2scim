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
package com.independentid.scim.core;

import com.independentid.scim.op.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author pjdhunt
 * PoolManager handles the thread pool for processing SCIM requests. It is designed to be 
 * a layer between thousands of incoming HTTP request threads and moderating processing 
 * to a level optimum for the backend Persistance Provider (e.g. Mongo DB).
 */
//@Component("PoolMgr")
//@ApplicationScoped
@Singleton  //Note Java Inject Singleton is different from ejb. PoolMgr handles its own threading issues
// See: https://www.baeldung.com/jee-cdi-vs-ejb-singleton
@Startup //This to ensure @PostConstruct is fired at startup to avoid NPE when servlet tries to invoke Operations
// This is the equivalent of @ApplicationScoped according to Quarkus.io
//@Startup
@Named("PoolMgr")
public class PoolManager {

	private final static Logger logger = LoggerFactory
			.getLogger(PoolManager.class);

	//private List<Operation> actions = Collections
	//		.synchronizedList(new ArrayList<Operation>());
	
	@Inject	
	@Resource(name="ConfigMgr")
	ConfigMgr sconfig;
	
	private ForkJoinPool pool;

	private int opCnt = 0;

	//@Value("${pool.thread.count:5}")
	@SuppressWarnings("FieldCanBeLocal")
	private int threads = 50;
	
	//@Autowired
	//ApplicationContext ctx;
	
	/**
	 * 
	 */
	public PoolManager() {
		//threads = sconfig.getPoolThreadCount();
		//logger.debug("Pool Manager initializing with " + threads + " threads.");
		//pool = new ForkJoinPool(threads);
		//pool = new ForkJoinPool(5);
		
	}

	/**
	 * Forces all threads to shutdown immediately.
	 */
	@PreDestroy
	public void shutdown() {
		pool.shutdownNow();
	}

	@PostConstruct
	public void initialize() {
		//check if already started

		if (pool != null)
			return;
		logger.info("Transaction Pool Manager Starting...");
		threads = sconfig.getPoolThreadCount();
		//threads = sconfig.getPoolThreadCount();
		if (logger.isDebugEnabled())
			logger.debug("Pool Manager initializing with " + threads + " threads.");
				//pool = new ForkJoinPool(threads);
		pool = new ForkJoinPool(threads);
	
		//self = (PoolManager) this.ctx.getBean("PoolMgr");

	}

	public synchronized Operation addJob(Operation task) {
		opCnt++;
		Operation op = (Operation) pool.submit(task);
		// actions.add(task);
		
		if (logger.isDebugEnabled())
			logger.debug("Queued(async): " + task.toString());

		return op;
	}
	public void addJobAndWait(Operation task)
			throws RejectedExecutionException {
		opCnt++;
		if (logger.isDebugEnabled())
			logger.debug("Queued(wait):  " + task.toString());
		pool.invoke(task);
		
	}

	/**
	 * Cancels a SCIM Operation task.
	 * 
	 * @param task
	 *            The previously submitted Operation to be cancelled.
	 * @param interruptIfRunning
	 *            If true, the server will attempt to abort an executing
	 *            operation (may cause loss of data).
	 * @return true if the operation was cancelled cleanly. False if the
	 *         operation failed due to exception or partial execution.
	 */
	public synchronized boolean cancelJob(Operation task,
			boolean interruptIfRunning) {
		if (task.isCompletedNormally())
			return false;

		task.cancel(interruptIfRunning);
		if (task.isCancelled()) {
			this.opCnt--;
			return true;
		}
		return false;
	}

	public int getOperationCnt() {
		return this.opCnt;
	}

	public int getThreadCnt() {
		return pool.getActiveThreadCount();
	}

	public int getPendingTasksCnt() {
		return pool.getQueuedSubmissionCount();
	}

	public boolean close(long time, TimeUnit unit) throws InterruptedException {
		return pool.awaitTermination(time, unit);
	}

}
