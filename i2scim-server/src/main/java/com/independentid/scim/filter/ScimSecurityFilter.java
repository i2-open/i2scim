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

package com.independentid.scim.filter;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AccessControl;
import com.independentid.scim.security.AccessManager;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

@WebFilter(filterName = "ScimSecurity", urlPatterns = "/*")
public class ScimSecurityFilter implements Filter {
    private final static Logger logger = LoggerFactory.getLogger(ScimSecurityFilter.class);

    public final static String ACCESS_TYPE_I2SCIM = "i2scim";
    @Inject
    AccessManager accessManager;

    @Inject
    SecurityIdentity identity;

    @Inject
    SchemaManager schemaManager;

    @Inject
    ConfigMgr configMgr;

    boolean enabled = true;

    public void init(FilterConfig filterConfig) {


        if (!configMgr.isSecurityEnabled()) {
            logger.warn("\t** SCIM Security filter *disabled*.");
            enabled = false;
            return;
        }
        if (configMgr.getAciMode().equals(ACCESS_TYPE_I2SCIM))
            logger.info("SCIM Security Filter started.");
        else
            enabled = false;
    }

    public void destroy() {

    }

    /**
     * Detects the requested SCIM operation and assigns the correct SCIM Right to the context.
     * @param req The HttpServletRequest for the operation
     * @param ctx The SCIM RequestCtx which holds the requested rights (to be assigned by this method).
     */
    public static void assignOperationRights(HttpServletRequest req, RequestCtx ctx) {
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
        if (enabled) {
            if (logger.isDebugEnabled())
                logger.debug("\tEvaluating request for: User=" + identity.getPrincipal().getName() + ", Type=" + identity.getPrincipal().getClass().toString());

            HttpServletRequest httpServletRequest;
            if (request instanceof HttpServletRequest) {
                httpServletRequest = (HttpServletRequest) request;
            } else {
                logger.error("Unexpected servlet request type received: " + request.getClass().toString());
                return;
            }

            String path = httpServletRequest.getPathInfo();
            if (path == null)
                path = httpServletRequest.getRequestURI();
            // Liveness/health check do not require authorization
            if (path.startsWith("/q")) {
                // allow healthcheck to proceed
                chain.doFilter(request, response);
                return;
            }

            RequestCtx ctx = (RequestCtx) request.getAttribute(RequestCtx.REQUEST_ATTRIBUTE);
            if (ctx == null) {
                try {
                    ctx = new RequestCtx(httpServletRequest, (HttpServletResponse) response, schemaManager);
                    request.setAttribute(RequestCtx.REQUEST_ATTRIBUTE, ctx);
                } catch (ScimException e) {
                    e.printStackTrace();
                }
            }
            assert ctx != null;
            assignOperationRights(httpServletRequest, ctx);
            if (accessManager.filterRequestandInitAcis(ctx, identity)) {
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
