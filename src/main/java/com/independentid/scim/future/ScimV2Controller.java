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

package com.independentid.scim.future;

import java.io.IOException;
import java.util.Optional;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.core.JsonGenerator;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.DeleteOp;
import com.independentid.scim.op.GetOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.op.PutOp;
import com.independentid.scim.op.SearchOp;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.PoolManager;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */

@Controller("ScimV2Controller")
public class ScimV2Controller  {

	//RegEx for UUID:  ([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}){1}
	private Logger logger = LoggerFactory.getLogger(ScimV2Controller.class);
	
	@Autowired
	ServletContext servletContext;
	
	@Resource(name="BackendHandler")
	private BackendHandler persistMgr;
	
	@Resource(name="ConfigMgr")
	private ConfigMgr config;
	
	@Resource(name="PoolMgr")
	private PoolManager pool;

	
	@PatchMapping("/{type}/{id}")
	public void doPatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		JsonGenerator gen = JsonUtil.getGenerator(resp.getWriter(), false);

		PatchOp op = new PatchOp(req, resp);

		this.pool.addJobAndWait(op);

		checkDone(op);

		op.doResponse(gen);

		gen.flush();
		gen.close();

	}

	@PutMapping("/{type}/{id}")
	public void doPut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		JsonGenerator gen = JsonUtil.getGenerator(resp.getWriter(), false);

		PutOp op = new PutOp(req, resp);

		this.pool.addJobAndWait(op);

		checkDone(op);

		op.doResponse(gen);

		gen.flush();
		gen.close();
	}
	
	private void checkDone(Operation op) {
		if (!op.isDone()) {
			logger.error("Unexpected Job returned without execution: "
					+ op.toString());
		}
	}

	@DeleteMapping("/{type}/{id}")
	public void doDelete(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		// We will need a generator for our response
		JsonGenerator gen = JsonUtil.getGenerator(resp.getWriter(), false);

		DeleteOp op = new DeleteOp(req, resp);

		this.pool.addJobAndWait(op);

		checkDone(op);

		op.doResponse(gen);

		gen.flush();
		gen.close();
	}
   
	@RequestMapping("**.search")
	public void doSearch(@PathVariable String type, @PathVariable String id, HttpServletRequest req,
			HttpServletResponse resp) throws ServletException, IOException {
		// Queries for /{resourceEndpoint}/{id}
		JsonGenerator gen = null;

		gen = JsonUtil.getGenerator(resp.getWriter(), false);

		SearchOp op = new SearchOp(req, resp);
			
		this.pool.addJobAndWait(op);
	
		checkDone(op);

		op.doResponse(gen);

		gen.flush();
		gen.close();
		
	}
	
	@GetMapping("/test") 
	public void doTest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		logger.info("doTest Called");
		logger.info("req.pathinfo: "+req.getPathInfo());
		logger.info("req.getServletPath :"+req.getServletPath());
		logger.info("req.getContextPath:"+ req.getContextPath());
		resp.getWriter()
			.print("Hello Tester!");
		
	}
	
	
	@GetMapping(value = {"/{type}","/{type}/{id}"})
	public void doGet(@PathVariable String type, @PathVariable Optional<String> id, HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.info("Get called...");
		logger.info("req.pathinfo: "+req.getPathInfo());
		logger.info("req.getServletPath :"+req.getServletPath());
		logger.info("req.getContextPath:"+ req.getContextPath());
		JsonGenerator gen = JsonUtil.getGenerator(resp.getWriter(), false);

		GetOp op = new GetOp(req,resp);
			
		this.pool.addJobAndWait(op);
	
		checkDone(op);

		op.doResponse(gen);

		gen.flush();
		gen.close();
		
	}

	@PostMapping("/bulk")
	public void doBulk(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("Bulk request received.");
		
		logger.warn("Bulk not implemented.");
		
		// TODO Must implement BULK or proper error reesponse!!!
		
	}
	
	
	@PostMapping("/{type}")
	protected void doPost(@PathVariable String type, @PathVariable String id,HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		logger.debug("doPost received: [type="+type+", id="+id+"]");
		// This section should no longerbe needed because mapping qualifiers will invoke directly.
		/*
			String path = req.getPathInfo();
			if (path.endsWith(ScimParams.PATH_SEARCH)) {
				// This is a search request.
				doSearch(type,id,req, resp);
				return;
			}
			
			if (path.startsWith(ScimParams.PATH_BULK)) {
				doBulk(req,resp);
				return;
			};
		*/
		
		// The request should now be a normal object creation request
		
		JsonGenerator gen = JsonUtil.getGenerator(resp.getWriter(), false);
		
		CreateOp op = new CreateOp(req, resp);
		this.pool.addJobAndWait(op);

		checkDone(op);

		op.doResponse(gen);

		gen.flush();
		gen.close();
	}

	/**
	 * This init causes the ConfigMgr to parse and initialize the server schema and will initialize 
	 * the backend provider which should already be injected. 
	 * @throws SchemaException 
	 * @throws ServletException
	 */
	public void init() throws ScimException,IOException, SchemaException {
		
		logger.info("======SCIM Server Initializing=====");
		
		logger.debug("...Parsing Schema Configuration");
		config.initializeConfiguration();
		
		logger.debug("...Initializing SCIM Provider");
		
		try {
			this.persistMgr.init();
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | BackendException e) {
			e.printStackTrace();
			throw new ScimException(
					"Exception occured while initializing BackendHandler", e);
		}

		//This should be handled by ConfigMgr
		/*
		if (config.getPersistSchema()) {
			persistMgr.doPersistSchema(config);
		}
		*/
		
		logger.debug("...SCIM Initialization complete.");
		
	}

}
