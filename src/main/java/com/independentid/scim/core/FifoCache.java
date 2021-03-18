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

package com.independentid.scim.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class FifoCache<T> {
    private final int size;
    private final List<T> list;
    private final Lock lock;

    public FifoCache(int size) {
        this.size = size;
        list = new ArrayList<>(size);
        lock = new ReentrantLock();
    }

    public void add(T item) {
        if (size ==0)
            return; // cache disabled
        lock.lock();
        try {
            while (list.size() >= size)
                list.remove(list.size() - 1);
            list.add(0, item);
        } finally {
            lock.unlock();
        }
    }

    public T get(int ind) {
        lock.lock();
        try {
            return list.get(ind);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return list.size();
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return list.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            list.clear();
        } finally {
            lock.unlock();
        }
    }
}
