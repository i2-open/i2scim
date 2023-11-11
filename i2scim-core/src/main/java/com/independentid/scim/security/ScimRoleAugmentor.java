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

// From: https://quarkus.io/guides/security-customization
package com.independentid.scim.security;

import com.independentid.scim.core.ConfigMgr;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.http.auth.BasicUserPrincipal;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
               builder.addRole("bearer");
            } else if (pal instanceof BasicUserPrincipal)
                builder.addRole("basic");
            if (pal.getName().equalsIgnoreCase(cmgr.getRootUser()))
                builder.addRole("root");

            // add role indicating bearer assertion

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
