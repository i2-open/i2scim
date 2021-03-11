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
