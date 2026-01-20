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

import com.independentid.scim.core.ConfigMgr;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.smallrye.jwt.runtime.auth.JWTAuthMechanism;
import io.quarkus.vertx.http.runtime.security.BasicAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.apache.http.auth.BasicUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * This {@link HttpAuthenticationMechanism} enables SCIM to support both Basic and JWT Authentication.
 * The module works by having a higher priority that JWTAuthMechanism and BasicAuthMechanism.
 * Based on the authorization prefix, the module routes authentication to the correct delegate.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class ScimAuthMechanism implements HttpAuthenticationMechanism {
    private static final Logger logger = LoggerFactory.getLogger(ScimAuthMechanism.class);

    @Inject
    ConfigMgr cmgr;

    @Inject
    JWTAuthMechanism jdelegate;

    @Inject
    BasicAuthenticationMechanism bdelegate;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        // do some custom action and delegate
        String authz = context.request().headers().get(HttpHeaderNames.AUTHORIZATION);

        if (authz == null) {
            // Assume the ScimSecurityFilter will decide regarding anonymous
            /*
            String path = context.request().absoluteURI();
            if (cmgr.isSecurityEnabled() && !path.contains("/health"))
                return Uni.createFrom().failure(new AuthenticationFailedException());

             */

            // This is an anonymous user
            SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                    .setPrincipal(new BasicUserPrincipal("anonymous"))
                    .addRole("anonymous")
                    .build();
            return Uni.createFrom().item(identity);
        }

        String prefix = authz.substring(0,6).toLowerCase(Locale.ENGLISH);

        if (prefix.equals("bearer")) {
            if (!cmgr.isSecurityEnabled()) {
                // Ignore the authorization and proceed as anonymous
                SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                        .setPrincipal(new BasicUserPrincipal("anonymous"))
                        .addRole("anonymous")
                        .build();
                return Uni.createFrom().item(identity);
            }
            //Must be a JWT Token
            return jdelegate.authenticate(context,identityProviderManager);
        }

        //Otherwise process as BasicAuth
        return bdelegate.authenticate(context, identityProviderManager);

    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        if (cmgr.isAuthJwt()) {
            // if we support JWT then use the JWT response as it is preferred.
            ChallengeData result = new ChallengeData(HttpResponseStatus.UNAUTHORIZED.code(), HttpHeaderNames.WWW_AUTHENTICATE, "Bearer {token}");
            return Uni.createFrom().item(result);
        }
        //Otherwise respond with basic auth challenge
        return bdelegate.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        // This class handles both BasicAuth and JwtAuth

        HashSet<Class<? extends AuthenticationRequest>> types = new HashSet<>();
        types.addAll(jdelegate.getCredentialTypes());
        types.addAll(bdelegate.getCredentialTypes());
        return types;
    }

}
