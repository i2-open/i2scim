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

import com.independentid.scim.resource.Value;

import java.util.*;

public class ValResMap implements Comparable<ValResMap>{

    Value val;

    Set<String> idList = new HashSet<>();

    public ValResMap(Value value) {
        this.val = value;
    }

    public void addId(String id) {
        idList.add(id);
    }

    public void removeId(String id) {
        idList.remove(id);
    }

    public boolean containsId(String id) {
        return idList.contains(id);
    }

    public Set<String> getIds() {
        return idList;
    }

    public Value getKey() { return val;}


    @Override
    public int compareTo(ValResMap o) {
        return val.compareTo(o.getKey());

    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ValResMap)
            return val.equals(((ValResMap)obj).getKey());
        return false;
    }

    public int size() {
        return idList.size();
    }
}
