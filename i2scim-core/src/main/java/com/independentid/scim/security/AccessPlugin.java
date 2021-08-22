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

import com.independentid.scim.core.err.NotImplementedException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.*;
import com.independentid.scim.plugin.IScimPlugin;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;

@ApplicationScoped
@Priority(2)
public class AccessPlugin implements IScimPlugin {
    private final static Logger logger = LoggerFactory
            .getLogger(AccessPlugin.class);

    @ConfigProperty(name = "scim.security.enable", defaultValue="true")
    boolean isSecurityEnabled;

    @Inject @Resource(name="AccessMgr")
    AccessManager accessManager;

    @Override
    public void init() throws ServletException {

        if (!isSecurityEnabled)
            logger.warn("Access management plugin *DISABLED*. Authorization policy will NOT be enforced");

    }

    /*
     At this stage the aci set has been filtered to match the path and client. We now only have to check
     attribute policy for the operation (targetAttrs and targetFilter).
     */
    @Override
    public void doPreOperation(Operation op) throws ScimException {
        if (!isSecurityEnabled)
            return;

        if (op instanceof GetOp || op instanceof SearchOp) {
            // Need to check filter attributes
            AccessManager.checkRetrieveOp(op);
            return;
        }

        if (op instanceof CreateOp) {
            AccessManager.checkCreatePreOp((CreateOp) op);
            return;
        }

        if (op instanceof DeleteOp) {
            AccessManager.checkDeletePreOp((DeleteOp) op);
            return;
        }

        if (op instanceof PatchOp) {
            AccessManager.checkPatchOp((PatchOp) op);
            return;
        }

        if (op instanceof PutOp) {

            AccessManager.checkPutOp((PutOp) op);
            return;
        }
        if (!(op instanceof BulkOps))
            throw new ScimException("Unexepected operation type encountered: "+op.getClass().getName());
        // we won't check BulkOps, rather each individual sub-operation is checked.

    }

    @Override
    public void doPostOperation(Operation op) {
        if (!isSecurityEnabled)
            return;

        AccessManager.checkReturnResults(op);

    }

}
