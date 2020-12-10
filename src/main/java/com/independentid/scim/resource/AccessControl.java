/*
 * Copyright (c) 2020.
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

package com.independentid.scim.resource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

public class AccessControl implements ScimSerializer {

    public enum Rights {all, add, modify, delete, read, search, compare}
    public enum ActorType {any, self, role, group, ref, filter}

    private String path;
    private String targetAttrs = null;
    private Filter targetFilter = null;
    private String name = null;
    private Collection<Rights> privs = new HashSet<>();
    private boolean isSelf = false;
    private boolean isAny = false;

    private ArrayList<String> actorRoles = new ArrayList<>();
    //private ArrayList<ReferenceValue> actorGroups = new ArrayList<>( );
    private ArrayList<ReferenceValue> actorRefs = new ArrayList<>();
    private ArrayList<Filter> actorFilters = new ArrayList<>();

    public AccessControl(JsonNode aciNode) throws SchemaException {
        parseJson(aciNode);
    }

    // Setters and getters

    public String getPath() {
        return path;
    }

    public void setPath(String aciPath) {
        path = aciPath;
    }

    /**
     * Based on a provided path, evaluates if the ACI potentially applies.
     * @param requestPath a valid Scim HTTP Path to evaluate
     * @return true if this ACI applies to the requested path value.
     */
    public boolean aciApplies(String requestPath) {
        return requestPath.startsWith(path);
    }

    public boolean isActorSelf() {
        return isSelf;
    }

    public void setActorSelf(boolean self) {
        isSelf = self;
    }

    public boolean isActorAny() {
        return isAny;
    }

    public void setActorAny(boolean any) {
        isAny = any;
    }

    public ArrayList<String> getActorRoles() {
        return actorRoles;
    }

    public void setActorRoles(ArrayList<String> roles) {
        this.actorRoles = roles;
    }

    /*
    public ArrayList<ReferenceValue> getActorGroups() {
        return actorGroups;
    }

    public void setActorGroups(ArrayList<ReferenceValue> groups) {
        this.actorGroups = groups;
    }
     */

    public ArrayList<ReferenceValue> getActorRefs() {
        return actorRefs;
    }

    public void setActorRefs(ArrayList<ReferenceValue> refs) {
        this.actorRefs = refs;
    }

    public ArrayList<Filter> getActorFilters() {
        return actorFilters;
    }

    public void setActorFilters(ArrayList<Filter> filters) {
        this.actorFilters = filters;
    }

    public String getTargetAttrs() {
        return targetAttrs;
    }

    public void setTargetAttrs(String targetAttrs) {
        this.targetAttrs = targetAttrs;
    }

    public Filter getTargetFilter() {
        return targetFilter;
    }

    public void setTargetFilter(Filter targetFilter) {
        this.targetFilter = targetFilter;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Collection<Rights> getRights() {
        return privs;
    }

    public void setRights(Collection<Rights> rights) {
        this.privs = rights;
    }

    // Parsing and serialization

    @Override
    public void parseJson(JsonNode node) throws SchemaException {
        JsonNode item = node.get("name");
        if (item != null)
            this.name = item.asText();

        item = node.get("path");
        if (item == null)
            this.path = "/"; // default to global
        else
            this.path = item.asText();

        item = node.get("targetattrs");
        if (item != null)
            this.targetAttrs = item.asText();

        item = node.get("targetfilter");
        if (item != null) {
            try {
                this.targetFilter = Filter.parseFilter(item.asText(),null);
            } catch (BadFilterException e) {
                throw new SchemaException("Invalid filter specified for targetFilter: "+e.getLocalizedMessage());
            }
        }

        item = node.get("rights");
        if (item == null)
            throw new SchemaException("ACI missing rights value.");
        privs = new ArrayList<>();
        for (String val : item.asText().split("[, ]+")) {
            switch (val.toLowerCase()) {
                case "all":
                    privs.add(Rights.all);
                    continue;
                case "add":
                    privs.add(Rights.add);
                    continue;
                case "modify":
                    privs.add(Rights.modify);
                    continue;
                case "delete":
                    privs.add(Rights.delete);
                    continue;
                case "read":
                    privs.add(Rights.read);
                    continue;
                case "search":
                    privs.add(Rights.search);
                    continue;
                case "compare":
                    privs.add(Rights.compare);
            }
        }

        item = node.get("actors");
        if (item != null) {
            for (JsonNode vnode : item) {
                String aline = vnode.asText();
                String sType = aline.substring(0,3).toLowerCase();
                String[] parts = aline.split("=");
                switch (sType) {
                    case "any":
                        this.isAny = true;
                        continue;
                    case "sel":
                        this.isSelf = true;
                        continue;
                    case "rol":
                        if(parts.length < 2)
                            throw new SchemaException("Invalid actor role specified: "+aline);
                        this.actorRoles.add(parts[1].trim());
                        continue;
                        /*
                    case "gro":
                        if(parts.length < 2)
                            throw new SchemaException("Invalid actor group specified: "+aline);
                        this.actorGroups.add(new ReferenceValue(null,parts[1]));
                        continue;

                         */
                    case "ref":
                        if(parts.length < 2)
                            throw new SchemaException("Invalid actor reference specified: "+aline);
                        this.actorRefs.add(new ReferenceValue(null,parts[1]));
                        continue;
                    case "fil":
                        if(parts.length < 2)
                            throw new SchemaException("Invalid actor reference specified: "+aline);
                        try {
                            this.actorFilters.add(Filter.parseFilter(parts[1],null));
                        } catch (BadFilterException e) {
                            throw new SchemaException("Invalid actor filter specified: "+aline);
                        }
                }
            }
        }

    }

    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
        serialize(gen,ctx,false);
    }

    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException, ScimException {
        gen.writeStartObject();

        gen.writeStringField("path",path);

        if (this.name != null)
            gen.writeStringField("name",this.name);

        if (this.targetFilter != null)
            gen.writeStringField("targetFilter",this.targetFilter.getFilterStr());

        if (this.targetAttrs != null)
            gen.writeStringField("targetAttrs",this.targetAttrs);

        if (!this.privs.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            Iterator<Rights> riter = this.privs.iterator();
            while (riter.hasNext()) {
                switch (riter.next()) {
                    case all:
                        builder.append("all");
                        break;
                    case add:
                        builder.append("add");
                        break;
                    case modify:
                        builder.append("modify");
                        break;
                    case delete:
                        builder.append("delete");
                        break;
                    case read:
                        builder.append("read");
                        break;
                    case search:
                        builder.append("search");
                        break;
                    case compare:
                        builder.append("compare");
                }
                if (riter.hasNext())
                    builder.append(",");
            }
            gen.writeStringField("rights",builder.toString());
        }

        ArrayList<String> actorVals = new ArrayList<>();
        if (this.isAny) actorVals.add("any");
        if (this.isSelf) actorVals.add("self");
        for (String arole : this.actorRoles)
            actorVals.add("role="+arole);
        /*
        for (ReferenceValue gRef : actorGroups)
            actorVals.add("group="+gRef.toString());

         */

        for (Filter fval : this.actorFilters)
            actorVals.add("filter="+fval.getFilterStr());
        if (actorVals.size() > 0) {
            gen.writeFieldName("actors");
            gen.writeStartArray();
            for (String val : actorVals)
                gen.writeString(val);
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

    public String toString() {
        try {
            return toJsonString();
        } catch (ScimException e) {
            //e.printStackTrace();
            return e.getLocalizedMessage();
        }
    }

    public String toJsonString() throws ScimException {
        StringWriter writer = new StringWriter();
        try {
            JsonGenerator gen = JsonUtil.getGenerator(writer, true);
            this.serialize(gen, null, false);
            gen.close();
            writer.close();
            return writer.toString();
        } catch (IOException e) {
            // Should not happen
            e.printStackTrace();
        }
        return super.toString();
    }

    public JsonNode toJsonNode() throws ScimException {
        StringWriter writer = new StringWriter();
        try {
            JsonGenerator gen = JsonUtil.getGenerator(writer, true);
            this.serialize(gen, null, false);
            gen.close();
            writer.close();
            return JsonUtil.getJsonTree(writer.toString());
        } catch (IOException e) {
            // Should not happen
            e.printStackTrace();
        }
        return null;
    }


}
