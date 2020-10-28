/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015 Phillip Hunt, All Rights Reserved                        *
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

package com.independentid.scim.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.backend.MemoryProvider;
import com.independentid.scim.op.BulkOps;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.DeleteOp;
import com.independentid.scim.op.GetOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.op.PutOp;
import com.independentid.scim.op.SearchOp;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.serializer.JsonUtil;

/**
 * @author pjdhunt
 * Main SCIM V2 Servlet. This class is used instead of a controller because full control over 
 * HTTP Request and Response is needed.  This class is written to be deployed in a Spring Boot Configuration.
 */
@Component
@WebServlet
public class ScimV2Servlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4864829511529176833L;

	private Logger logger = LoggerFactory.getLogger(ScimV2Servlet.class);
	
	public final static String PARAM_SCHEMA_PATH = "scim.schema.path";
	public final static String DEFAULT_SCHEMA_PATH = "/META-INF/scimSchema.json";

	public final static String PARAM_RTYPE_PATH = "scim.resourceType.path";
	public final static String DEFAULT_RTYPE_PATH = "/META-INF/resourceType.json";

	public final static String PARAM_PRETTY_JSON = "scim.json.pretty";
	public final static String DEFAULT_PRETTY_JSON = "false";

	public final static String PARAM_PROVIDER_CLASS = "scim.provider.class";
	public final static String DEFAULT_PROVIDER = MemoryProvider.class
			.getName();

	public final static String PARAM_MAX_RESULTS = "scim.maximum.resultsize";
	public final static String DEFAULT_MAX_RESULTS = "1000";

	@Resource(name="ConfigMgr")
	private ConfigMgr cfgMgr;
	
	@Resource(name="BackendHandler")
	private BackendHandler persistMgr = null;
	
	@Resource(name="PoolMgr")
	private PoolManager pool;
	
	public ScimV2Servlet() {
		
	}
	
	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#service(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (req.getPathInfo().startsWith("/certs")) {
			super.service(req, resp);
			return;
		}
		if (req.getMethod().equalsIgnoreCase("PATCH")) 
			doPatch(req,resp);
		else
			super.service(req, resp);
	}

	protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		PatchOp op = new PatchOp(req, resp);

		complete(op);
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

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
	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		DeleteOp op = new DeleteOp(req, resp);

		complete(op);
		
	}
	
	protected void doSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		SearchOp op = new SearchOp(req, resp);
			
		complete(op);
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		//String cp = getServletContext().getContextPath();
		String path = new UrlPathHelper().getPathWithinApplication(req);
		
		//Used when jwks-certs needs to be served locally (usually for testing)
		if (path.startsWith("/certs")) {
			//super.doGet(req, resp);
			File filePath = cfgMgr.findClassLoaderResource("classpath:"+path);
			ServletOutputStream out = resp.getOutputStream();
			FileInputStream instream = new FileInputStream(filePath);
			instream.transferTo(out);
			instream.close();
			return;
		}
		if (path.startsWith("/v2"))
			path = path.substring(3);
		
		if (path.startsWith("/test") ) {
			logger.info("doTest Called");
			resp.getWriter()
				.print("Hello Tester!");
			return;
		}
		
		
		GetOp op = new GetOp(req,resp);

		complete(op);
	}

	protected void doBulk(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
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
			throws ServletException, IOException {
		String path = new UrlPathHelper().getPathWithinApplication(req);
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
		
		CreateOp op = new CreateOp(req, resp);
		
		complete(op);
		
	}

	@Override
	public void init() throws ServletException {
		logger.info("====== SCIM V2 Servlet Initialized =====");

	}
	
	private void checkDone(Operation op) {
		if (!op.isDone()) {
			logger.error("Unexpected Job returned without execution: "
					+ op.toString());
		}
	}
	
	private void complete (Operation op) throws IOException {
		
		JsonGenerator gen = JsonUtil.getGenerator(op.getResponse().getWriter(), false);
		
		this.pool.addJobAndWait(op);

		checkDone(op);

		op.doResponse(gen); 

		gen.flush();
		gen.close();
	}


}
