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

import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import jakarta.validation.constraints.NotNull;

import java.util.*;

/**
 * Provides the Memory Provider indexes for a particular container ({@link ResourceType}.
 */
public class IndexResourceType {
    Schema common;
    Schema schema;
    List<Schema> extSchemas;
    ResourceType resourceType;
    Map<String,ScimResource> resMap;

     List<Attribute>
            presAttrs,
            exactAttrs,
            orderAttrs,
            substrAttrs,
            uniqueAttrs;

    Map<Attribute,Map<Integer,ValResMap>> iExact;
    Map<Attribute,SortedMap<Value,ValResMap>> iOrder;
    Map<Attribute,SortedMap<String,ValResMap>> iSub;
    Map<Attribute,ValResMap> iPres;

    public IndexResourceType(@NotNull SchemaManager schemaManager, @NotNull ResourceType res, Map<String, ScimResource> containerMap, List<Attribute> indexed, List<Attribute> uniques) {

        resourceType = res;
        resMap = containerMap;

        schema = schemaManager.getSchemaById(res.getSchema());
        common = schemaManager.getSchemaById(ScimParams.SCHEMA_SCHEMA_Common);
        extSchemas = new ArrayList<>();
        for (String id : res.getSchemaExtensions().keySet())
            extSchemas.add(schemaManager.getSchemaById(id));

        presAttrs = new ArrayList<>();
        exactAttrs = new ArrayList<>();
        orderAttrs = new ArrayList<>();
        substrAttrs = new ArrayList<>();

        catalogAttrIndexes(common.getAttributes());

        catalogAttrIndexes(indexed.toArray(new Attribute[0]));

        // All unique attribues need an exact match index.
        this.uniqueAttrs = uniques;
        for (Attribute attr : uniques)
            if (!exactAttrs.contains(attr))
                exactAttrs.add(attr);

        initializeIndexMaps();

    }

    /* The following accessors are used for testing purposes */

    public List<Attribute> getPresAttrs() { return presAttrs; }
    public List<Attribute> getExactAttrs() { return exactAttrs; }
    public List<Attribute> getOrderAttrs() {return orderAttrs; }
    public List<Attribute> getSubstrAttrs() { return substrAttrs; }
    public SortedMap<Value,ValResMap> getOrderIndex(Attribute attr) { return iOrder.get(attr); }
    public SortedMap<String,ValResMap> getSubstrIndex(Attribute attr) { return iSub.get(attr); }

    /**
     * For the attributes requested, the appropriate indexes (exact, ordered, substring etc) are set up based on attribute Type.
     * @param attrs An array of attributes to be indexed.
     */
    private void catalogAttrIndexes(Attribute[] attrs) {
        for (Attribute attr: attrs) {
            presAttrs.add(attr);
            if (attr.getSchema().equals(resourceType.getSchema())
                || resourceType.getSchemaExtensions().containsKey(attr.getSchema())) {
                switch (attr.getType()) {
                    case Attribute.TYPE_String:
                        substrAttrs.add(attr);
                    case Attribute.TYPE_Complex:
                    case Attribute.TYPE_Reference:
                        orderAttrs.add(attr);
                        exactAttrs.add(attr);
                        break;
                    case Attribute.TYPE_Boolean:
                    case Attribute.TYPE_Binary:
                        exactAttrs.add(attr);
                        break;
                    case Attribute.TYPE_Date:
                    case Attribute.TYPE_Integer:
                    case Attribute.TYPE_Decimal:
                        exactAttrs.add(attr);
                        orderAttrs.add(attr);
                }
            }
        }
    }

    private void initializeIndexMaps() {
        iPres = Collections.synchronizedMap(new HashMap<>());
        iExact = Collections.synchronizedMap(new HashMap<>());
        iOrder = Collections.synchronizedMap(new HashMap<>());
        iSub = Collections.synchronizedMap(new HashMap<>());

        //Set up presence
        for (Attribute attr : presAttrs)
            iPres.put(attr,new ValResMap(new StringValue(attr,"*")));

        //Exact indexes
        for (Attribute attr : exactAttrs)
            iExact.put(attr,Collections.synchronizedMap(new HashMap<>()));

        for (Attribute attr : orderAttrs)
            iOrder.put(attr, Collections.synchronizedSortedMap(new TreeMap<>()));

        for (Attribute attr : substrAttrs)
            iSub.put(attr, Collections.synchronizedSortedMap(new TreeMap<>()));
    }

    /**
     * Checks an attribute value to see if there is a conflicting unique value. Note that this check uses a hash and thus
     * a true does not guarantee a conflict, only a high probability of one.
     * @param val The Value to be checked for uniqueness conflict.  If the attribute is not classed as unique false is treturned.
     * @return True if a conflict exists
     */
    public boolean checkUniqueAttr(Value val) {
        Attribute attr = val.getAttribute();
        if (!this.uniqueAttrs.contains(attr))
            return false;

        Map<Integer, ValResMap> attrMap = iExact.get(attr);
        int hashVal = val.hashCode();
        return attrMap.containsKey(hashVal);
    }

    /**
     * Checks a ScimResource to see if there is already a unique value that conflicts.
     * @param res The ScimResource to be checked
     * @return True if a conflict exists
     */
    public boolean checkUniques(ScimResource res) {
        for (Attribute attr : res.getAttributesPresent()) {
            Value val = res.getValue(attr);
            if (checkUniqueAttr(val))
                return true;
        }
        return false;
    }

    public boolean isAttributeIndexed(Attribute attr) {
        // Since all indexed attributes have a presence index, we can use presence to determine if an attr is indexed.
        return presAttrs.contains(attr);
    }


    /**
     * Removes the specified resource attribute from the index.
     * @param attr The Attribute indicating the index where the resource is to be remove
     * @param res The resource to be removed from the index
     */
    private void deleteId(Attribute attr, ScimResource res) {
        String id = res.getId();
        Value val = res.getValue(attr);

        //Remove from presence
        ValResMap vmap = iPres.get(attr);
        if (vmap != null)
            vmap.removeId(id);

        Map<Integer, ValResMap> exact = iExact.get(attr);
        Map<Value, ValResMap> order = iOrder.get(attr);
        Map<String, ValResMap> sub = iSub.get(attr);

        Value[] vals = new Value[1];
        if (val instanceof MultiValue)
            vals = ((MultiValue)val).getRawValue();
        else
            vals[0] = val;

        //Remove from exact
        if (exact != null)
            for(Value aval : vals) {
                ValResMap vrm = exact.get(aval.hashCode());
                if (vrm != null) {
                    vrm.removeId(id);
                    if (vrm.size() == 0)
                        exact.remove(aval.hashCode());
                }
            }

        //Remove from ordered
        if (order != null)
            for(Value aval : vals) {
                ValResMap vrm = order.get(aval);
                if (vrm != null) {
                    vrm.removeId(id);
                    if (vrm.size() == 0)
                        order.remove(aval);
                }
            }

        //Remove from substring
        if (sub != null)
            if (vals[0] instanceof StringValue)
            for(Value aval : vals) {
                String rval = ((StringValue)aval).reverseValue();
                ValResMap vrm = sub.get(rval);
                if (vrm != null) {
                    vrm.removeId(id);
                    if (vrm.size() == 0)
                        sub.remove(rval);
                }
            }
    }

    public void deIndexResource(ScimResource res) {
        for (Attribute attr: res.getAttributesPresent())
            if (isAttributeIndexed(attr))
                deleteId(attr,res);
    }

    private void addExactHash(Map<Integer,ValResMap> index,Value val, String id) {
        int hash = val.hashCode();
        ValResMap vrm = index.get(hash);
        if (vrm == null) {
            vrm = new ValResMap(val);
            index.put(hash,vrm);
        }
        vrm.addId(id);
    }

    private void addOrderValue(Map<Value,ValResMap> index, Value val, String id) {
        ValResMap vrm = index.get(val);
        if (vrm == null) {
            vrm = new ValResMap(val);
            index.put(val,vrm);
        }
        vrm.addId(id);
    }

    private void addSubStrValue(Map<String,ValResMap> index, Value val, String id) {
        if (val instanceof StringValue) {
            String rval = ((StringValue) val).reverseValue();
            ValResMap vrm = index.get(rval);
            if (vrm == null) {
                vrm = new ValResMap(val);
                index.put(rval, vrm);
            }
            vrm.addId(id);
        } else  // This shouldn't happen. Just here for debugging
            System.err.println("Unexpected value was not StringValue: "+val.toString());
    }

    public void indexResource(ScimResource res) {
        String id = res.getId();

        // Presence Index
        for (Attribute attr : presAttrs) {
            Value val = res.getValue(attr);
            if (val != null) {
                ValResMap vrm = iPres.get(attr);
                vrm.addId(id);
            }
        }

        // Exact Match Index
        for (Attribute attr : exactAttrs) {
            Value val = res.getValue(attr);
            if (val != null) {
                Map<Integer,ValResMap> attrIndex = iExact.get(attr);
                if (val instanceof MultiValue) {
                    MultiValue mval = (MultiValue) val;
                    for (Value aval : mval.getRawValue())
                        addExactHash(attrIndex, aval, id);
                } else {
                   addExactHash(attrIndex, val, id);
                }
            }
        }

        // Ordered Index
        for (Attribute attr : orderAttrs) {
            Value val = res.getValue(attr);
            if (val != null) {
                Map<Value,ValResMap> attrIndex = iOrder.get(attr);
                if (val instanceof MultiValue) {
                    MultiValue mval = (MultiValue) val;
                    for (Value aval : mval.getRawValue())
                        addOrderValue(attrIndex, aval, id);
                } else {
                    addOrderValue(attrIndex, val, id);
                }
            }
        }

        // Substring Index
        for (Attribute attr : substrAttrs) {
            Value val = res.getValue(attr);
            if (val != null) {
                Map<String,ValResMap> attrIndex = iSub.get(attr);
                if (val instanceof MultiValue) {
                    MultiValue mval = (MultiValue) val;
                    for (Value aval : mval.getRawValue())
                        addSubStrValue(attrIndex, aval, id);
                } else {
                    addSubStrValue(attrIndex, val, id);
                }
            }
        }

    }

    public Set<String> notList(Set<String> source, Set<String> subtracts) {
        Set<String> res = new HashSet<>(source);
        for (String id : subtracts)
            res.remove(id);
        return res;
    }

    public Set<String> getPotentialMatches(AttributeFilter filter) throws BadFilterException {
        Attribute attr = filter.getAttribute();
        Set<String> res = new HashSet<>();

        if (!isAttributeIndexed(attr) && !attr.getName().equals(ScimParams.ATTR_ID))
            return new HashSet<>(resMap.keySet());  // return all entities if not indexed

        switch (filter.getOperator()) {
            case AttributeFilter.FILTEROP_PRESENCE:
                if(presAttrs.contains(attr)) {
                    ValResMap vrm = iPres.get(attr);
                    res.addAll(vrm.getIds());
                    return res;
                }
                break;

            case AttributeFilter.FILTEROP_EQ:
                if (attr.getName().equals(ScimParams.ATTR_ID)) {
                    res.add(filter.asString());
                    break;
                }
                if(exactAttrs.contains(attr)) {
                    Map<Integer,ValResMap> map = iExact.get(attr);
                    ValResMap vrm = map.get(filter.getValue().hashCode());
                    return (vrm == null) ? res : vrm.getIds();
                }
                break;

            case AttributeFilter.FILTEROP_NE:
                if(exactAttrs.contains(attr)) {
                    Map<Integer,ValResMap> map = iExact.get(attr);
                    ValResMap vrm = map.get(filter.getValue().hashCode());
                    if (vrm == null)
                        break;  // return everythign
                    return notList(resMap.keySet(),vrm.getIds());
                }
                break;


            case AttributeFilter.FILTEROP_CONTAINS:
                // loop through all indexed values to find matches
                if (substrAttrs.contains(attr)) {
                    Map<Value,ValResMap> map = iOrder.get(attr);

                    for(Value val : map.keySet()) {
                        if (filter.isMatch(val))
                           res.addAll(map.get(val).getIds());
                    }
                    return res;
                }
                break;

            case AttributeFilter.FILTEROP_STARTSWITH:
                // Just loop starting with filter.value until no match
                if (substrAttrs.contains(attr)) {
                    SortedMap<Value,ValResMap> map = iOrder.get(attr);
                    SortedMap<Value, ValResMap> tailmap = map.tailMap(filter.getValue());

                    for (ValResMap vrm : tailmap.values()) {
                        if (filter.isMatch(vrm.getKey()))
                            res.addAll(vrm.getIds());
                        else
                            break;
                    }
                    return res;
                }
                break;

            case AttributeFilter.FILTEROP_ENDSWITH:
                if (substrAttrs.contains(attr)) {
                    Value filterval = filter.getValue();
                    if (filterval instanceof StringValue) {
                        StringValue matchVal = (StringValue) filterval;
                        SortedMap<String, ValResMap> map = iSub.get(attr);
                        SortedMap<String, ValResMap> tailmap = map.tailMap(matchVal.reverseValue());

                        for (ValResMap vrm : tailmap.values()) {
                            if (filter.isMatch(vrm.getKey()))
                                res.addAll(vrm.getIds());
                            else
                                break;
                        }
                        return res;
                    }
                }
                break;

            case AttributeFilter.FILTEROP_GREATER:
                if (orderAttrs.contains(attr)) {
                    SortedMap<Value,ValResMap> map = iOrder.get(attr);
                    Value filterval = filter.getValue();
                    SortedMap<Value, ValResMap> tailmap = map.tailMap(filterval);
                    for (ValResMap vrm : tailmap.values()) {
                        if (vrm.val.equals(filterval))
                            continue; // tail returns great or equal
                        res.addAll(vrm.getIds());
                    }
                    return res;
                }
                break;

            case AttributeFilter.FILTEROP_GREATEROREQUAL:
                if (orderAttrs.contains(attr)) {
                    SortedMap<Value,ValResMap> map = iOrder.get(attr);
                    SortedMap<Value, ValResMap> tailmap = map.tailMap(filter.getValue());
                    for (ValResMap vrm : tailmap.values()) {
                        res.addAll(vrm.getIds());
                    }
                    return res;
                }
                break;

            case AttributeFilter.FILTEROP_LESSOREQUAL:
                // do the equal part
                if (orderAttrs.contains(attr)) {
                    SortedMap<Value, ValResMap> map = iOrder.get(attr);
                    ValResMap vrm = map.get(filter.getValue());
                    if (vrm != null)
                        res.addAll(vrm.getIds());
                    return res;
                }
                // continue on and do the less than entries
            case AttributeFilter.FILTEROP_LESS:

                if (orderAttrs.contains(attr)) {
                    SortedMap<Value,ValResMap> map = iOrder.get(attr);
                    SortedMap<Value, ValResMap> headmap = map.headMap(filter.getValue());
                    for (ValResMap vrm : headmap.values()) {
                        res.addAll(vrm.getIds());
                    }
                    return res;
                }
                break;
        }
        // If no index match found, we simply return all as candidates
        return new HashSet<>(resMap.keySet());
    }

    public Set<String> getPotentialMatches(LogicFilter filter) throws BadFilterException {
        Set<String> f1ids = getPotentialMatches(filter.getValue1());
        Set<String> f2ids = getPotentialMatches(filter.getValue2());
        if (filter.isAnd())
            f1ids.removeIf(id -> !f2ids.contains(id));
        else
            f1ids.addAll(f2ids);
        return f1ids;
    }

    public Set<String> getPotentialMatches(PrecedenceFilter filter) throws BadFilterException {
        Set<String> res = getPotentialMatches(filter.getChildFilter());
        if (filter.isNot()) {
            return notList(resMap.keySet(),res);
        }
        return res;
    }

    public Set<String> getPotentialMatches(ValuePathFilter filter) throws BadFilterException {
        Attribute attr = filter.getAttribute();
        if (!presAttrs.contains(attr))
            return new HashSet<>(resMap.keySet());
        // TODO not sure if this will work!
        return getPotentialMatches(filter.getValueFilter());
    }

    public Set<String> getPotentialMatches(Filter filter) throws BadFilterException {
        if (filter instanceof AttributeFilter)
            return getPotentialMatches((AttributeFilter) filter);
        if (filter instanceof LogicFilter)
            return getPotentialMatches((LogicFilter) filter);
        if (filter instanceof PrecedenceFilter)
            return getPotentialMatches((PrecedenceFilter) filter);
        if (filter instanceof ValuePathFilter)
            return getPotentialMatches((ValuePathFilter) filter);

        return new HashSet<>();
    }

}
