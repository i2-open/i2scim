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
