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

package com.independentid.scim.resource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.IVirtualValue;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The GroupsValue virtual attribute enables the "groups" attribute to automatically return values by looking up the current user's
 * membership in Groups resources. Note, this still allows
 */
public class GroupsValue extends MultiValue implements IVirtualValue {
    static BackendHandler handler;
    static SchemaManager smgr;

    static Attribute dispNameAttr;
    static Attribute displayAttr;
    static Attribute refAttr;
    static Attribute valAttr;
    static Attribute typeAttr;
    static StringValue typeDirectValue;

    ScimResource res;
    boolean loaded = false;

    public GroupsValue(ScimResource parent, Attribute attr, JsonNode node) throws SchemaException, ConflictException, ParseException {
        super(attr,node,null);
        this.res = parent;
    }

    public GroupsValue(ScimResource parent, Value val) {
        this(parent,val.getAttribute());
        if (val instanceof MultiValue) {
            MultiValue mval = (MultiValue) val;
            for (Value aval : mval.getRawValue())
                super.addValue(aval);
        } else
            addValue(val);
    }


    public GroupsValue(ScimResource parent, Attribute attr) {
        super();
        this.attr = attr;
        this.res = parent;
    }

    public static void init(SchemaManager schemaManger,BackendHandler handler) {
        GroupsValue.handler = handler;
        GroupsValue.smgr = schemaManger;
        Attribute attr = smgr.findAttribute("User:groups",null);
        displayAttr = attr.getSubAttribute("display");
        refAttr = attr.getSubAttribute("$ref");
        valAttr = attr.getSubAttribute("value");
        typeAttr = attr.getSubAttribute("type");
        typeDirectValue = new StringValue(typeAttr,"direct");

        dispNameAttr = smgr.findAttribute("Group:displayName",null);
    }

    public void refreshValues() {
        loaded = false;
        loadValues();
    }

    /**
     * Load values is intended to be called once per the life-cycle of the Value. In order to minimize calculation,
     * dynamic calculation is only calculated once the first accessor is called.
     */
    private void loadValues() {
        if (loaded) return;
        try {
            RequestCtx ctx = new RequestCtx("/Groups",null,"Group:members.value eq "+res.getId(),smgr);
            ctx.setAttributes("displayName");
            ScimResponse resp = handler.get(ctx);

            if (resp instanceof ListResponse) {
                ListResponse lresp = (ListResponse) resp;
                for(ScimResource item : lresp.getResults())
                    processGroup(item);
            }
        } catch (ScimException | BackendException e) {
            System.err.println("Error looking up group membership: "+e.getMessage());
            e.printStackTrace();
        }
        loaded = true;
    }

    private void processGroup(ScimResource res) {
        Map<Attribute,Value> map = new HashMap<>();
        try {
            Value displayName =  res.getValue(dispNameAttr);
            Value value = new StringValue(valAttr,res.getId());
            Value ref = new ReferenceValue(refAttr,res.getMeta().getLocation());
            map.put(refAttr,ref);
            map.put(valAttr,value);
            if (displayName != null) {  // Convert from displayName to display...
                Value display = new StringValue(displayAttr,displayName.toString());
                map.put(displayAttr, display);
            }
            map.put(typeAttr,typeDirectValue);
            ComplexValue cval = new ComplexValue(this.attr,map);
            addValue(cval);
        } catch (SchemaException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
        if (ctx != null)
            loadValues();  // calculate the values at return time.
        //if (ctx == null || this.values().size() ==0)
        //    return;  // don't store or replicate groups value
        super.serialize(gen, ctx);
    }

    @Override
    public JsonNode toJsonNode(ObjectNode parent, String aname) {
        if (this.values().size() == 0) {
            if (parent == null)
                parent = JsonUtil.getMapper().createObjectNode();
            parent.putArray(aname);  // create an empty array
            return parent;
        }
        return super.toJsonNode(parent, aname);
    }

    @Override
    public boolean isVirtual() {
        return true;
    }

    @Override
    public int size() {
        loadValues();
        return super.size();
    }

    @Override
    public Value getMatchValue(Filter filter) throws BadFilterException {
        loadValues();
        return super.getMatchValue(filter);
    }

    @Override
    public Collection<Value> values() {
        loadValues();
        return super.values();
    }

    @Override
    public Value[] getRawValue() {
        loadValues();
        return super.getRawValue();
    }

}
