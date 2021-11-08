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

package com.independentid.scim.server;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.op.*;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import java.io.IOException;

/**
 * @author pjdhunt
 * Main SCIM V2 Servlet. This class is used instead of a controller because full control over 
 * HTTP Request and Response is needed.  This class is written to be deployed in a Spring Boot Configuration.
 */

@Startup
//@RequestScoped
//@Named("ScimServlet")
//@WebServlet("/")
@WebServlet(name = "ScimServlet", urlPatterns = "/*")
public class ScimV2Servlet extends HttpServlet {

	private static final long serialVersionUID = 4864829511529176833L;

	private final Logger logger = LoggerFactory.getLogger(ScimV2Servlet.class);
	
	@Inject
	@Resource(name="ConfigMgr")
	ConfigMgr cfgMgr;

	@Inject
	@Resource(name="PoolMgr")
	PoolManager pool;

	//@Inject
	//EventManager eventManager;

	public ScimV2Servlet() {
		logger.info("Scim Servlet Constructed");
	}

	private String reqPath(HttpServletRequest req) {
		String pathInfo = req.getPathInfo();
		if (pathInfo != null) // This seems to be needed for SpringBoot
			return pathInfo;
		return req.getRequestURI();
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		if (reqPath(req).startsWith("/certs")) {
			super.service(req, resp);
			return;
		}
		if (req.getMethod().equals(HttpMethod.PATCH))
			doPatch(req,resp);
		else
			super.service(req, resp);
	}


	@Counted(name="scim.ops.patch.count",description="Counts the number of SCIM Patch requests")
	@Timed (name="scim.ops.patch.timer",description = "Measures SCIM Patch operation times")
	protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		PatchOp op = new PatchOp(req, resp);

		complete(op);
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Counted(name="scim.ops.put.count",description="Counts the number of SCIM Put requests")
	@Timed (name="scim.ops.put.timer",description = "Measures SCIM Put operation times")
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		PutOp op = new PutOp(req, resp);

		complete(op);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest
	 * , javax.servlet.http.HttpServletResponse)
	 */
	@Counted(name="scim.ops.delete.count",description="Counts the number of SCIM Delete requests")
	@Timed (name="scim.ops.delete.timer",description = "Measures SCIM Delete operation times")
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		DeleteOp op = new DeleteOp(req, resp);

		complete(op);
	}

	@Counted(name="scim.ops.search.count",description="Counts the number of SCIM Post Search requests")
	@Timed (name="scim.ops.search.timer",description = "Measures SCIM Post Search operation times")
	protected void doSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		SearchOp op = new SearchOp(req, resp);
			
		complete(op);
	}

	@Counted(name="scim.ops.head.count",description="Counts the number of SCIM HEAD requests")
	@Timed (name="scim.ops.head.timer",description = "Measures SCIM HEAD times")
	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		HeadOp op = new HeadOp(req,resp);
		complete(op);
	}

	@Counted(name="scim.ops.get.count",description="Counts the number of SCIM Get requests")
	@Timed (name="scim.ops.get.timer",description = "Measures SCIM Get operation times")
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		String path = reqPath(req);
		//Used when jwks-certs needs to be served locally (usually for testing)
		if (path.startsWith("/certs")) {
			System.err.println("Cert path called: "+path);
			/*
			ServletOutputStream out = resp.getOutputStream();
			InputStream instream = ConfigMgr.findClassLoaderResource(path);
			if (instream == null) {
				resp.setStatus(HttpStatus.SC_NOT_FOUND);
				return;
			}
			instream.transferTo(out);
			instream.close();
			return;

			 */
			resp.setStatus(HttpStatus.SC_NOT_FOUND);
			return;
		}

		GetOp op = new GetOp(req,resp);
		complete(op);
	}

	@Counted(name="scim.ops.bulk.count",description="Counts the number of SCIM Bulk requests")
	@Timed (name="scim.ops.bulk.timer",description = "Measures SCIM Bulk operation times")
	protected void doBulk(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		
		BulkOps op = new BulkOps(req, resp);
		complete(op);

	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest
	 * , javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		String path = reqPath(req);
		//String path = req.getPathInfo();
				// Check if this is a search.
		if (path.endsWith(ScimParams.PATH_SUBSEARCH)) {
			doSearch(req,resp);
			return;
		}
				
		//Check if this is a bulk request and dispatch
		if (path.startsWith(ScimParams.PATH_BULK)) {
			doBulk(req,resp);
			return;
		}
		
		//Assuming this is a create operation.
		doCreate(req,resp);
		
	}

	@Counted(name="scim.ops.create.count",description="Counts the number of SCIM Create requests")
	@Timed (name="scim.ops.create.timer",description = "Measures SCIM Create operation times")
	protected void doCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		CreateOp op = new CreateOp(req, resp);

		complete(op);

	}

	@Override
	public void init() {
		logger.info("====== SCIM V2 Servlet Initialized =====");
		/*  This is already PostConstruct and should self start.
			try {
				cfgMgr.initializeConfiguration();
			} catch (ScimException | IOException e) {
				logger.error("Error initializing configuration manager: "+e.getLocalizedMessage(),e);
			}
		*/

	}

	@PostConstruct
	@Override
	public void init(ServletConfig config) throws ServletException {
		// TODO Auto-generated method stub
		super.init(config);
		
		// trying to get ConfigMgr to load on startup...
		// suggested by: https://stackoverflow.com/questions/3600534/how-do-i-force-an-application-scoped-bean-to-instantiate-at-application-startup
		config.getServletContext().setAttribute("ConfigMgr", cfgMgr);
		Operation.initialize(cfgMgr);
		//pool.initialize();
	}

	private void checkDone(Operation op) {
		if (!op.isDone()) {
			logger.error("Unexpected Job returned without execution: "
					+ op);
		}
	}


	@Counted(name="opsCnt",description="Counts the total number of SCIM operations")
	@Timed (name="opsTimer",description = "Measures SCIM operation times (all types)")
	protected void complete (Operation op) throws IOException {

		pool.addJobAndWait(op);

		checkDone(op);

		JsonGenerator gen = JsonUtil.getGenerator(op.getResponse().getWriter(), false);
		op.doResponse(gen); 

		gen.flush();
		gen.close();
	}


}
