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
