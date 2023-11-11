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

package com.independentid.scim.security;

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.http.auth.BasicUserPrincipal;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;

/**
 * This provider supports HTTP Basic Authentication in Quarkus. The current function supports authentication for the
 * configured root account defined by SCIM Properties: scim.security.authen.basic = true,
 * scim.security.root.enable=true, scim.security.root.username, and scim.security.root.password
 */
@ApplicationScoped
public class ScimBasicIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {
    public static final String ATTR_SELF_ID = "id";
    public static final String ATTR_ACTOR_RES = "scimActorResource";

    private static final Logger logger = LoggerFactory.getLogger(ScimBasicIdentityProvider.class);

    @Inject
    ConfigMgr cmgr;

    @Inject
    SchemaManager smgr;

    BackendHandler handler = BackendHandler.getInstance();

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Counted(name = "scim.auth.root",description = "Number of times root has authenticated via basic auth")
    public Uni<SecurityIdentity> doRootAuthentication(String userName, PasswordCredential cred) {
        String pwd = new String(cred.getPassword());
        if (pwd.equals(cmgr.getRootPassword())) {
            SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                    .setPrincipal(new BasicUserPrincipal(userName))
                    .addRole("root")
                    .addCredential(new PasswordCredential(pwd.toCharArray()))
                    .build();

            return Uni.createFrom().item(identity);
        }
        logger.debug("Failed authentication of root user.");
        return Uni.createFrom().failure(new AuthenticationFailedException());

    }

    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request, AuthenticationRequestContext context) {
        String userName = request.getUsername();
        PasswordCredential cred = request.getPassword();

        //String azHeader = context.request().getHeader(HttpHeaderNames.AUTHORIZATION);
        if (cmgr.isRootEnabled() && cmgr.isAuthBasic()) {

            if (userName.equalsIgnoreCase(cmgr.getRootUser()))
                return doRootAuthentication(userName,cred);

            return doInternalAuthentication(userName, cred);
        }

        //TODO If another username password database was defined, it would go here.

        //Since no authenticaiton was possible, return a failure
        return Uni.createFrom().failure(new AuthenticationFailedException());
    }

    @Counted(name = "scim.auth.internal.count",description = "Number of times intenral users have been authenticated.")
    @Timed(name="scim.auth.internal.timer",description = "Measures internal(users in SCIM) SCIM user authentication rates")
    Uni<SecurityIdentity> doInternalAuthentication(String user, PasswordCredential cred) {
        String pwd = new String(cred.getPassword());
        if (pwd.contains("\"")) // check for embedded quotes for injection attack
            return Uni.createFrom().failure(new AuthenticationFailedException("Invalid password detected"));

        String filter  //URLEncoder.encode("UserName eq \"" + user + "\" and password eq \"" + pwd + "\"", StandardCharsets.UTF_8);
        //        = "userName eq \""+user+"\" and password eq \""+pwd+"\"";
                = "userName eq \""+user+"\"";   // cannot search for a password value directly

        RequestCtx ctx;
        try {
            ctx = new RequestCtx("/Users", null, filter, smgr);
        } catch (ScimException e) {
            logger.warn("Exception creating search filter for user " + user + ": " + e.getLocalizedMessage(), e);
            return Uni.createFrom().failure(new AuthenticationFailedException());
        }
        if (!handler.isReady()) {
            logger.warn("Basic Identity Provider: **BackendHandler is not ready**");
        }

        // Note: this does not go through the SCIM server threading system. This goes direct to the backend!
        try {
            ScimResponse resp = handler.get(ctx);
            if (resp instanceof ListResponse) {
                ListResponse lresp = (ListResponse) resp;
                if (lresp.getSize() == 0)  // No MATCH, return failure
                    return Uni.createFrom().failure(new AuthenticationFailedException());

                Iterator<ScimResource> entries = lresp.entries();
                // Since username is unique, there should only be one entry.
                ScimResource resource = entries.next();

                Filter pfilt = Filter.parseFilter("password eq "+pwd,null,ctx);
                if (!pfilt.isMatch(resource))
                    return Uni.createFrom().failure(new AuthenticationFailedException());

                //Get the roles from the matched user resource
                Attribute role = resource.getAttribute("roles", ctx);
                Value val = resource.getValue(role);
                String id = resource.getId();
                QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
                builder
                        .setPrincipal(new BasicUserPrincipal(user))
                        .addAttribute("uri", resource.getMeta().getLocation())
                        .addAttribute(ATTR_SELF_ID,id)
                        .addAttribute(ATTR_ACTOR_RES,resource)
                        .addCredential(new PasswordCredential(pwd.toCharArray()))
                        .addRole("user"); // add the default role

                // Map the "roles" attribute and add to Identity
                if (val instanceof MultiValue) {
                    Collection<Value> vals = ((MultiValue) val).values();
                    for (Value aval : vals) {
                        StringValue sval = (StringValue) aval;
                        builder.addRole(sval.toString());
                    }

                } else {
                    if (val instanceof StringValue) {
                        StringValue sval = (StringValue) val;
                        builder.addRole(sval.toString());
                    }
                }

                return Uni.createFrom().item(builder::build);

            }

        } catch (ScimException | BackendException e) {
            logger.error("Unexpected error searching SCIM for: " + user + ": " + e.getLocalizedMessage(), e);
            return Uni.createFrom().failure(new AuthenticationFailedException());
        }

        return Uni.createFrom().failure(new AuthenticationFailedException());
    }


}
