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
package com.independentid.scim.core;

import com.independentid.scim.events.PublishOperation;
import com.independentid.scim.op.Operation;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

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
@Priority(3)
@Named("PoolMgr")
public class PoolManager {

	private final static Logger logger = LoggerFactory
			.getLogger(PoolManager.class);

	//private List<Operation> actions = Collections
	//		.synchronizedList(new ArrayList<Operation>());
	
	@Inject	
	@Resource(name="ConfigMgr")
	ConfigMgr sconfig;
	
	private ForkJoinPool operationPool;
	private ForkJoinPool eventPool;

	private int opCnt = 0;

	//@Value("${pool.thread.count:5}")
	@SuppressWarnings("FieldCanBeLocal")
	private int threads = 50;

	private boolean ready = false;
	
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
		operationPool.shutdownNow();
		eventPool.shutdownNow();
	}

	@PostConstruct
	public void initialize() {
		//check if already started

		if (operationPool != null)
			return;
		logger.info("Transaction Pool Manager Starting...");
		threads = sconfig.getPoolThreadCount();
		//threads = sconfig.getPoolThreadCount();
		if (logger.isDebugEnabled())
			logger.debug("Pool Manager initializing with " + threads + " threads.");
				//pool = new ForkJoinPool(threads);

		//operationPool = new ForkJoinPool(threads);
		/*
		This is to solve a classloader issue around microprofiles. smallrye JWT won't load properly
		See: https://github.com/quarkusio/quarkus/issues/9397#issuecomment-630037633
		 */
		operationPool = new ForkJoinPool(threads, pool -> new ForkJoinWorkerThread(pool) {}, null, false);

		eventPool = new ForkJoinPool(5);
	
		//self = (PoolManager) this.ctx.getBean("PoolMgr");
		ready = true;
	}

	public synchronized void addJob(Operation task) {
		while (!ready) {
			//noinspection CatchMayIgnoreException
			try {
				//noinspection BusyWait
				sleep(100);
			} catch (InterruptedException e) {
			}
		}
		opCnt++;
		operationPool.execute(task); // arrange asyn execution
		// actions.add(task);
		
		if (logger.isDebugEnabled())
			logger.debug("Queued(async): " + task.toString());


	}

	public void addJobAndWait(Operation task)
			throws RejectedExecutionException {
		while (!ready) {
			//noinspection CatchMayIgnoreException
			try {
				//noinspection BusyWait
				sleep(100);
			} catch (InterruptedException e) {
			}
		}
		opCnt++;
		if (logger.isDebugEnabled())
			logger.debug("Queued(wait):  " + task.toString());
		operationPool.invoke(task);
		
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

	@Gauge(unit = MetricUnits.NONE, name = "Pool: Operation Count")
	public int getOperationCnt() {
		return this.opCnt;
	}

	@Gauge(unit = MetricUnits.NONE, name = "Pool: Active Threads")
	public int getThreadCnt() {
		return operationPool.getActiveThreadCount();
	}

	@Gauge(unit = MetricUnits.NONE, name = "Pool: Pending Ops")
	public int getPendingTasksCnt() {
		return operationPool.getQueuedSubmissionCount();
	}

	public boolean close(long time, TimeUnit unit) throws InterruptedException {
		return operationPool.awaitTermination(time, unit);
	}

	public synchronized PublishOperation addPublishOperation(PublishOperation task) {
		return (PublishOperation) eventPool.submit(task);

	}

}
