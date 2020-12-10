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

package com.independentid.scim.security;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.protocol.ScimResponse;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import org.apache.http.auth.BasicUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.security.auth.Subject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.security.Principal;
import java.util.Set;

@WebFilter(filterName = "ScimSecurity",urlPatterns = "/*")
public class ScimSecurityFilter implements Filter {
    private final static Logger logger = LoggerFactory.getLogger(ScimSecurityFilter.class);

    @Inject
    ConfigMgr cmgr;

    @Inject
    SecurityIdentity identity;

    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("SCIM Security Filter started.");
        if (!cmgr.isSecurityEnabled())
            logger.warn("\tSecurity filter *disabled*.");
    }

    public void destroy() {

    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (logger.isDebugEnabled())
            logger.debug("\tEvaluating request for: User="+identity.getPrincipal().getName()+", Type="+identity.getPrincipal().getClass().toString());
        if (cmgr.isSecurityEnabled()) {

            Set<String> roles = identity.getRoles();

            if (roles.contains("root")) {
                chain.doFilter(request, response);
                return;
            }

            if(roles.contains("full")) {
                chain.doFilter(request, response);
                return;
            }

            if (response instanceof HttpServletResponse) {
                if (logger.isDebugEnabled())
                    logger.debug("No scopes matched for user: "+identity.getPrincipal().getName()+", Scopes Provided: "+roles.toString());
                //No further chain processing
                HttpServletResponse resp = (HttpServletResponse) response;
                resp.setStatus(ScimResponse.ST_UNAUTHORIZED);
                return;
            }
        }
        // Security disabled, pass the request on.

        chain.doFilter(request,response);
    }
}
