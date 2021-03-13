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

import com.independentid.scim.core.ConfigMgr;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;

import javax.servlet.ServletException;


public class ScimServer {

	public static void main(final String[] args) throws ServletException {

		String host = System.getProperty(ConfigMgr.SCIM_SERVER_HOST,ConfigMgr.SCIM_SERVER_HOST_DEF);
		int port = Integer.parseInt(System.getProperty(ConfigMgr.SCIM_SERVER_PORT,ConfigMgr.SCIM_SERVER_PORT_DEF));
		String root = System.getProperty(ConfigMgr.SCIM_SERVER_PATH,ConfigMgr.SCIM_SERVER_PATH_DEF);
		DeploymentInfo servletBuilder = Servlets.deployment()
		        .setClassLoader(ScimV2Servlet.class.getClassLoader())
		        .setContextPath("/scim")
		        .setDeploymentName("test.war")
		        .addServlets(
		                Servlets.servlet("ScimV2Servlet", ScimV2Servlet.class)
		                        .addMapping("/*"));

		DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
		manager.deploy();
		PathHandler path = Handlers.path(Handlers.redirect(root))
		        .addPrefixPath(root, manager.start());

		Undertow server = Undertow.builder()
		        .addHttpListener(port, host)
		        .setHandler(path)
		        .build();
		server.start();
	}

}
