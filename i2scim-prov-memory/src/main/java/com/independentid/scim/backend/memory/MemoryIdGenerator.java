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

package com.independentid.scim.backend.memory;

import com.independentid.scim.backend.IIdentifierGenerator;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
@Priority(10)
public class MemoryIdGenerator implements IIdentifierGenerator {

    /**
     * Generates and obtains an identifier usable for objects within the persistence provider. This is most typically
     * used for generating transactionIds which will be ultimately stored in the providers "Trans" container.
     * @return A String value that can be used as an object identifier.
     */
    @Override
    public String getNewIdentifier() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getProviderClass() {
        return MemoryProvider.class.getName();
    }
}
