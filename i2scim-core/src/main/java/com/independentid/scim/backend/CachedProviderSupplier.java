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

package com.independentid.scim.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Cache provider is used to solve a @PostConstruct init conflict problem. see: https://levelup.gitconnected.com/stop-using-postconstruct-in-your-java-applications-2a66fb202cb8
 */
public class CachedProviderSupplier<IScimProvider> implements Supplier<IScimProvider> {
    private final Supplier<IScimProvider> supplier;
    private final ArrayList<IScimProvider> cache;

    public CachedProviderSupplier(Supplier<IScimProvider> supplier) {
        this.supplier = supplier;
        this.cache = new ArrayList<>(1);
    }

    public IScimProvider get() {
        if (cache.isEmpty())
            cache.add(supplier.get());
        return cache.get(0);
    }
}
