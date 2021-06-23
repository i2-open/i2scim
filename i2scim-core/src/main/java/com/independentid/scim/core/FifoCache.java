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
