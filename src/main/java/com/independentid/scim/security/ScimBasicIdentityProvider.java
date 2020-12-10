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

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import org.apache.http.auth.BasicUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.InternalServerErrorException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;

/**
 * This provider supports HTTP Basic Authentication in Quarkus. The current function supports authentication for the
 * configured root account defined by SCIM Properties: scim.security.authen.basic = true,
 * scim.security.root.enable=true, scim.security.root.username, and scim.security.root.password
 */
@ApplicationScoped
public class ScimBasicIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {
    private static final Logger logger = LoggerFactory.getLogger(ScimBasicIdentityProvider.class);

    @Inject
    ConfigMgr cmgr;

    @Inject
    BackendHandler handler;

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request, AuthenticationRequestContext context) {
        String userName = request.getUsername();
        PasswordCredential cred = request.getPassword();

        //String azHeader = context.request().getHeader(HttpHeaderNames.AUTHORIZATION);
        if (cmgr.isRootEnabled() && cmgr.isAuthBasic()) {

            if (userName.equalsIgnoreCase(cmgr.getRootUser())) {
                //Ok we have the root user. Authenticate locally.
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

            return doInternalAuthentication(userName, cred);
        }

        //TODO If another username password database was defined, it would go here.

        //Since no authenticaiton was possible, return a failure
        return Uni.createFrom().failure(new AuthenticationFailedException());
    }

    private Uni<SecurityIdentity> doInternalAuthentication(String user, PasswordCredential cred) {
        String pwd = new String(cred.getPassword());
        if (pwd.contains("\"")) // check for embedded quotes for injection attack
            return Uni.createFrom().failure(new AuthenticationFailedException("Invalid password detected"));

        String filter = URLEncoder.encode("UserName eq \"" + user + "\" and password eq \"" + pwd + "\"", StandardCharsets.UTF_8);
        RequestCtx ctx;
        try {
            ctx = new RequestCtx("/Users", null, filter, cmgr);
        } catch (ScimException e) {
            logger.warn("Exception creating search filter for user " + user + ": " + e.getLocalizedMessage(), e);
            return Uni.createFrom().failure(new AuthenticationFailedException());
        }
        if (!handler.isReady()) {
            try {
                handler.init(cmgr);
            } catch (ClassNotFoundException | InstantiationException | BackendException e) {
                return Uni.createFrom().failure(new InternalServerErrorException());
            }
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

                //Get the roles from the matched user resource
                Attribute role = resource.getAttribute("roles", ctx);
                Value val = resource.getValue(role);

                QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
                builder
                        .setPrincipal(new BasicUserPrincipal(user))
                        .addAttribute("uri", resource.getMeta().getLocation())
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
