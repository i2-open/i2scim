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

import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.SchemaManager;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.HttpMethod;
import java.io.IOException;
import java.util.Set;

@WebFilter(filterName = "ScimSecurity", urlPatterns = "/*")
public class ScimSecurityFilter implements Filter {
    private final static Logger logger = LoggerFactory.getLogger(ScimSecurityFilter.class);

    @Inject
    AccessManager amgr;

    @Inject
    SecurityIdentity identity;

    @Inject
    SchemaManager schemaManager;

    @ConfigProperty(name = "scim.security.enable", defaultValue="true")
    boolean isSecurityEnabled;

    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("SCIM Security Filter started.");
        if (!isSecurityEnabled)
            logger.warn("\t** SCIM Security filter *disabled*.");
    }

    public void destroy() {

    }

    /**
     * Detects the requested SCIM operation and assigns the correct SCIM Right to the context.
     * @param req The HttpServletRequest for the operation
     * @param ctx The SCIM RequestCtx which holds the requested rights (to be assigned by this method).
     */
    public void assignOperationRights(HttpServletRequest req, RequestCtx ctx) {
        String method = req.getMethod();
        switch (method) {
            case HttpMethod
                    .DELETE:
                ctx.setRight(AccessControl.Rights.delete);
                return;

            case HttpMethod.PATCH:
            case HttpMethod.PUT:
                ctx.setRight(AccessControl.Rights.modify);
                return;

            case HttpMethod.POST:
                if (ctx.isPostSearch()) {
                    ctx.setRight(AccessControl.Rights.search);

                } else
                    ctx.setRight(AccessControl.Rights.add);
                return;

            case HttpMethod.GET:
            case HttpMethod.HEAD:   // note, HEAD not yet supported!
                if (ctx.getFilter() != null) {
                    ctx.setRight(AccessControl.Rights.search);
                } else
                    ctx.setRight(AccessControl.Rights.read);

        }
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (isSecurityEnabled) {
            if (logger.isDebugEnabled())
                logger.debug("\tEvaluating request for: User=" + identity.getPrincipal().getName() + ", Type=" + identity.getPrincipal().getClass().toString());

            HttpServletRequest hrequest;
            if (request instanceof HttpServletRequest) {
                 hrequest = (HttpServletRequest) request;
            } else {
                logger.error("Unexpected servlet request type received: "+request.getClass().toString());
                return;
            }

            // Liveness/health check do not require authorization
            if (hrequest.getPathInfo().startsWith("/q")) {
                // allow healthcheck to proceed
                chain.doFilter(request,response);
                return;
            }

            RequestCtx ctx = (RequestCtx) request.getAttribute(RequestCtx.REQUEST_ATTRIBUTE);
            if (ctx == null) {
                try {
                    ctx = new RequestCtx(hrequest, (HttpServletResponse) response, schemaManager);
                    request.setAttribute(RequestCtx.REQUEST_ATTRIBUTE, ctx);
                } catch (ScimException e) {
                    e.printStackTrace();
                }
            }
            assert ctx != null;
            assignOperationRights(hrequest, ctx);
            if (amgr.filterRequestandInitAcis(ctx, identity)) {
                chain.doFilter(request, response);
                return;
            }

            // Process default overrides
            Set<String> roles = identity.getRoles();

            if (roles.contains("root")) {
                if (logger.isDebugEnabled())
                    logger.debug("Allowing request for " + identity.getPrincipal().getName() + " based on root access rights");
                chain.doFilter(request, response);
                return;
            }

            if (roles.contains("full")) {
                if (logger.isDebugEnabled())
                    logger.debug("Allowing request for " + identity.getPrincipal().getName() + " based on \"full\" role.");
                chain.doFilter(request, response);
                return;
            }

            if (response instanceof HttpServletResponse) {
                if (logger.isDebugEnabled())
                    logger.debug("No acis/roles matched for user: " + identity.getPrincipal().getName() + ", Scopes Provided: " + roles);
                //No further chain processing
                HttpServletResponse resp = (HttpServletResponse) response;
                if (identity.isAnonymous())
                    resp.setStatus(ScimResponse.ST_UNAUTHORIZED);
                else
                    resp.setStatus(ScimResponse.ST_FORBIDDEN);
                return;
            }
        }
        // Security disabled, pass the request on.

        chain.doFilter(request, response);
    }
}
