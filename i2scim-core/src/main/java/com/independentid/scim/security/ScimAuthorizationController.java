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
import io.quarkus.security.spi.runtime.AuthorizationController;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import javax.interceptor.Interceptor;

@Alternative
@Priority(Interceptor.Priority.LIBRARY_AFTER)
@ApplicationScoped
public class ScimAuthorizationController extends AuthorizationController {
    @Inject
    ConfigMgr cmgr;

    public ScimAuthorizationController() {
        super();
    }

    @Override
    public boolean isAuthorizationEnabled() {

        return cmgr.isSecurityEnabled();
    }
}
