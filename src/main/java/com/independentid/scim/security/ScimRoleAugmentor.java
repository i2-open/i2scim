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

// From: https://quarkus.io/guides/security-customization
package com.independentid.scim.security;

import com.independentid.scim.core.ConfigMgr;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.security.Principal;
import java.util.function.Supplier;

@ApplicationScoped
public class ScimRoleAugmentor implements SecurityIdentityAugmentor {

    private static final Logger logger = LoggerFactory.getLogger(ScimRoleAugmentor.class);

    @Inject
    ConfigMgr cmgr;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            logger.debug("Anonymous request detected.");

        }
        return Uni.createFrom().item(build(identity));

        // Do 'return context.runBlocking(build(identity));'
        // if a blocking call is required to customize the identity
    }

    @Counted(name="scim.auth.jwt.count",description="Counts the number JWT identities")
    @Timed(name="scim.auth.jwt.timer",description = "Measures JWT processing time")
    public Supplier<SecurityIdentity> build(SecurityIdentity identity) {

        if(identity.isAnonymous()) {
            return () -> identity;
        } else {
            // create a new builder and copy principal, attributes, credentials and roles from the original identity
            QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
            Principal pal = identity.getPrincipal();
            if (pal instanceof JWTCallerPrincipal) {
               buildJwt(builder,identity);
            }
            if (pal.getName().equalsIgnoreCase(cmgr.getRootUser()))
                builder.addRole("root");

            // add role indicating bearer assertion
            builder.addRole("bearer");
            return builder::build;
        }
    }

    public void buildJwt(QuarkusSecurityIdentity.Builder builder,SecurityIdentity identity) {

        Principal pal = identity.getPrincipal();

        JWTCallerPrincipal jpal = (JWTCallerPrincipal) pal;
        Object val = jpal.getClaim(cmgr.getJwtScopeClaim());
        assert val instanceof String;
        String[] scopes = ((String) val).split(" ");
        for (String scope: scopes) {
            builder.addRole(scope);
        }

    }


}
