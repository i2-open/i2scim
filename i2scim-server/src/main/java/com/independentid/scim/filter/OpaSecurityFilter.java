/*
 * Copyright (c) 2021.  Independent Identity Incorporated
 * <P/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <P/>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <P/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.independentid.scim.filter;

import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AccessManager;
import io.quarkus.security.identity.SecurityIdentity;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(filterName = "OpaFilter", urlPatterns = "/*")
public class OpaSecurityFilter implements Filter {
    private final static Logger logger = LoggerFactory.getLogger(OpaSecurityFilter.class);
    public final static String ACCESS_TYPE_OPA = "OPA";

    @Inject
    SchemaManager schemaManager;

    @Inject
    AccessManager amgr;

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "scim.security.enable", defaultValue = "true")
    boolean isSecurityEnabled;

    @ConfigProperty(name = "scim.security.mode", defaultValue = "i2scim")
    String aciMode;

    @ConfigProperty(name= "scim.opa.authz.url", defaultValue = "http://localhost:8181/v1/data/i2scim")
    String opaUrl;

    boolean enabled = true;

    CloseableHttpClient opaClient = HttpClients.createDefault();

    /**
     * <p>
     * Called by the web container to indicate to a filter that it is being placed into service.
     * </p>
     *
     * <p>
     * The servlet container calls the init method exactly once after instantiating the filter. The init method must
     * complete successfully before the filter is asked to do any filtering work.
     * </p>
     *
     * <p>
     * The web container cannot place the filter into service if the init method either
     * </p>
     * <ol>
     * <li>Throws a ServletException
     * <li>Does not return within a time period defined by the web container
     * </ol>
     * @param filterConfig a <code>FilterConfig</code> object containing the filter's configuration and initialization
     *                     parameters
     * @throws ServletException if an exception has occurred that interferes with the filter's normal operation
     * @implSpec The default implementation takes no action.
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (!isSecurityEnabled) {
            enabled = false;
            return;
        }
        if (aciMode.equals(ACCESS_TYPE_OPA)) {
            logger.info("OPA Security Filter started.");
        } else
            enabled = false;
    }

    /**
     * The <code>doFilter</code> method of the Filter is called by the container each time a request/response pair is
     * passed through the chain due to a client request for a resource at the end of the chain. The FilterChain passed
     * in to this method allows the Filter to pass on the request and response to the next entity in the chain.
     *
     * <p>
     * A typical implementation of this method would follow the following pattern:
     * <ol>
     * <li>Examine the request
     * <li>Optionally wrap the request object with a custom implementation to filter content or headers for input
     * filtering
     * <li>Optionally wrap the response object with a custom implementation to filter content or headers for output
     * filtering
     * <li>
     * <ul>
     * <li><strong>Either</strong> invoke the next entity in the chain using the FilterChain object
     * (<code>chain.doFilter()</code>),
     * <li><strong>or</strong> not pass on the request/response pair to the next entity in the filter chain to block the
     * request processing
     * </ul>
     * <li>Directly set headers on the response after invocation of the next entity in the filter chain.
     * </ol>
     * @param request  the <code>ServletRequest</code> object contains the client's request
     * @param response the <code>ServletResponse</code> object contains the filter's response
     * @param chain    the <code>FilterChain</code> for invoking the next filter or the resource
     * @throws IOException      if an I/O related error has occurred during the processing
     * @throws ServletException if an exception occurs that interferes with the filter's normal operation
     * @see UnavailableException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        if (enabled) {
            logger.info("OpaSecurityFilter called.");
            RequestCtx ctx = (RequestCtx) request.getAttribute(RequestCtx.REQUEST_ATTRIBUTE);
            if (ctx == null) {
                try {
                    ctx = new RequestCtx((HttpServletRequest) request, (HttpServletResponse) response, schemaManager);
                    ctx.setSecSubject(identity);
                    ScimSecurityFilter.assignOperationRights((HttpServletRequest) request,ctx);
                    request.setAttribute(RequestCtx.REQUEST_ATTRIBUTE, ctx);
                } catch (ScimException e) {
                    e.printStackTrace();
                }
            }

            assert ctx != null;
            String input = ctx.toOpaInput();
            logger.info("Calling OPA at: "+opaUrl);
            logger.info("input:\n" + input);
            HttpPost post = new HttpPost(opaUrl);
            StringEntity body = new StringEntity(input, ContentType.APPLICATION_JSON);
            post.setEntity(body);
            try {
                CloseableHttpResponse resp = opaClient.execute(post);
                if (resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    HttpEntity entity = resp.getEntity();
                    if (entity == null) {
                        logger.error("Error calling OpenPolicyAgent: OPA may not be properly configured.");
                        ((HttpServletResponse)response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }
                    String result = EntityUtils.toString(entity);
                    if (result.isEmpty() || result.equals("{}")) {
                        logger.error("Error calling OpenPolicyAgent: OPA may not be properly configured.");
                        ((HttpServletResponse)response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        return;
                    }

                    logger.info("Result:\n"+result);
                    resp.close();
                    if (amgr.filterRequestOpaAcis(ctx,result)) {
                        chain.doFilter(request,response);
                        return;
                    }
                    ((HttpServletResponse)response).setStatus(ScimResponse.ST_UNAUTHORIZED);
                }
            } catch (IOException e) {
                logger.error("Error calling OpenPolicyAgent: "+e.getMessage(),e);
                ((HttpServletResponse)response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }


        }

        // security disabled, just carry on
        chain.doFilter(request,response);
    }

    /**
     * <p>
     * Called by the web container to indicate to a filter that it is being taken out of service.
     * </p>
     *
     * <p>
     * This method is only called once all threads within the filter's doFilter method have exited or after a timeout
     * period has passed. After the web container calls this method, it will not call the doFilter method again on this
     * instance of the filter.
     * </p>
     *
     * <p>
     * This method gives the filter an opportunity to clean up any resources that are being held (for example, memory,
     * file handles, threads) and make sure that any persistent state is synchronized with the filter's current state in
     * memory.
     * </p>
     * @implSpec The default implementation takes no action.
     */
    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
