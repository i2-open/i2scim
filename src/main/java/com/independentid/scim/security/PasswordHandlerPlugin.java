/*
 * Copyright (c) 2021.
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
import com.independentid.scim.op.Operation;
import com.independentid.scim.plugin.IScimPlugin;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import java.security.SecureRandom;

@ApplicationScoped
public class PasswordHandlerPlugin implements IScimPlugin {
    @ConfigProperty(name="scim.pwd.plugin",defaultValue = "true")
    boolean enabled;

    @Override
    public void doPreOperation(Operation op) throws ScimException {
        if (!enabled)
            return;
    }

    @Override
    public void doPostOperation(Operation op) throws ScimException {
        if (!enabled)
            return;
        String test = "bleh";
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
    }
}
