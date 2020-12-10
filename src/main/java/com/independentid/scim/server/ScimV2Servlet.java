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

package com.independentid.scim.server;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.PoolManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.*;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.serializer.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author pjdhunt
 * Main SCIM V2 Servlet. This class is used instead of a controller because full control over 
 * HTTP Request and Response is needed.  This class is written to be deployed in a Spring Boot Configuration.
 */

@Startup
@RequestScoped
@Named("ScimServlet")
@WebServlet("/")
public class ScimV2Servlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4864829511529176833L;

	private final Logger logger = LoggerFactory.getLogger(ScimV2Servlet.class);
	
	@Inject
	@Resource(name="ConfigMgr")
	ConfigMgr cfgMgr;
	
	//@Inject
	//private BackendHandler persistMgr;
	
	@Inject
	@Resource(name="PoolMgr")
  	PoolManager pool;

	@Context
	HttpServletResponse requestTest;

	public ScimV2Servlet() {
		
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
		if (req.getMethod().equalsIgnoreCase("PATCH")) 
			doPatch(req,resp);
		else
			super.service(req, resp);
	}

	protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {

		PatchOp op = new PatchOp(req, resp, cfgMgr);

		complete(op);
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		PutOp op = new PutOp(req, resp, cfgMgr);

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
			throws IOException {

		DeleteOp op = new DeleteOp(req, resp, cfgMgr);

		complete(op);
		
	}
	
	protected void doSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		
		SearchOp op = new SearchOp(req, resp, cfgMgr);
			
		complete(op);
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {

		//String cp = getServletContext().getContextPath();
		//String path = new UrlPathHelper().getPathWithinApplication(req);
		String path = reqPath(req);
		
		//Used when jwks-certs needs to be served locally (usually for testing)
		if (path.startsWith("/certs")) {
			//super.doGet(req, resp);

			ServletOutputStream out = resp.getOutputStream();
			FileInputStream instream = cfgMgr.getClassLoaderFile(path);
			instream.transferTo(out);
			instream.close();
			return;
		}
		if (path.startsWith("/v2"))
			path = path.substring(3);
		
		if (path.startsWith("/test") ) {
			logger.debug(" Servlet test endpoint called");
			resp.getWriter()
				.print("Hello Tester!");
			return;
		}
		
		
		GetOp op = new GetOp(req,resp, cfgMgr);

		complete(op);
	}

	protected void doBulk(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		
		BulkOps op = new BulkOps(req, resp, cfgMgr);
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
		//CreateOp op = CDI.current().getBeanManager().getExtension()

		//CreateOp op = getBean(CreateOp.class);
		CreateOp op = new CreateOp(req, resp, cfgMgr);

		complete(op);
		
	}

	/*
	@SuppressWarnings("unchecked")
	private <T> T getBean(Class<T> beanClass) {
		BeanManager bm = CDI.current().getBeanManager();
		Bean<T> bean = (Bean<T>) bm.getBeans(beanClass).iterator().next();
		CreationalContext<T> ctx = bm.createCreationalContext(bean);
		return (T) bm.getReference(bean, beanClass, ctx);
	}
	*/

	@Override
	public void init() {
		logger.info("====== SCIM V2 Servlet Initialized =====");
		if (!cfgMgr.isReady()) {
			try {
				cfgMgr.initializeConfiguration();
			} catch (ScimException | IOException e) {
				logger.error("Error initializing configuration manager: "+e.getLocalizedMessage(),e);
			}
		}

	}

	@PostConstruct
	@Override
	public void init(ServletConfig config) throws ServletException {
		// TODO Auto-generated method stub
		super.init(config);
		
		// trying to get ConfigMgr to load on startup...
		// suggested by: https://stackoverflow.com/questions/3600534/how-do-i-force-an-application-scoped-bean-to-instantiate-at-application-startup
		config.getServletContext().setAttribute("ConfigMgr", ConfigMgr.getConfig());

		//pool.initialize();
	}

	private void checkDone(Operation op) {
		if (!op.isDone()) {
			logger.error("Unexpected Job returned without execution: "
					+ op.toString());
		}
	}
	
	private void complete (Operation op) throws IOException {
		
		JsonGenerator gen = JsonUtil.getGenerator(op.getResponse().getWriter(), false);
		
		pool.addJobAndWait(op);

		checkDone(op);

		op.doResponse(gen); 

		gen.flush();
		gen.close();
	}


}
