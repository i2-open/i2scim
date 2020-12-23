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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.ScimBasicIdentityProvider;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;

import javax.ws.rs.HttpMethod;
import java.io.IOException;
import java.io.StringWriter;
import java.security.Principal;
import java.util.*;

/**
 * AccessControl is based originally upon the Sun Directory server aci (access control instruction) model. An access
 * control is composed of 4 parts:
 * - Resources - Composed of a path (required), targetAttrs, and targetFilter to describe the resources and attributes
 * to which a policy is applied
 * - Name - A descriptive name of an aci
 * - Clients - The actors to which a rule applies
 * - Rights - The rights granted such as add, modify, delete, read, search, compare
 *
 * This class represents a single ACI and is invoked {@link com.independentid.scim.security.AccessManager}
 */
public class AccessControl implements ScimSerializer {

    public enum Rights {all, add, modify, delete, read, search, compare}

    public enum ActorType {any, self, role, group, ref, filter}

    private final SchemaManager smgr;
    private String path;
    private String targetAttrs = null;
    private Filter targetFilter = null;
    private String name = null;
    private Collection<Rights> privs = new HashSet<>();
    private boolean isClientSelf = false;
    private boolean isAnyClient = false;

    private ArrayList<String> clientRoles = new ArrayList<>();
    //private ArrayList<ReferenceValue> actorGroups = new ArrayList<>( );
    private ArrayList<ReferenceValue> clientRefs = new ArrayList<>();
    private ArrayList<Filter> clientFilters = new ArrayList<>();

    public AccessControl(SchemaManager schemaManager, JsonNode aciNode) throws SchemaException {
        smgr = schemaManager;
        parseJson(aciNode);
    }

    // Setters and getters

    public String getAciPath() {
        return path;
    }

    public void setAciPath(String aciPath) {
        path = aciPath;
    }

    /**
     * Based on a provided path, evaluates if the ACI potentially applies.
     * @param requestPath a valid Scim HTTP Path to evaluate
     * @return true if this ACI applies to the requested path value.
     */
    public boolean isAciApplicable(String requestPath) {
        return requestPath.startsWith(path);
    }

    public boolean isClientSelf() {
        return isClientSelf;
    }

    public void setClientSelf(boolean self) {
        isClientSelf = self;
    }

    public boolean isAnyClient() {
        return isAnyClient;
    }

    public void setAnyClient(boolean any) {
        isAnyClient = any;
    }

    public ArrayList<String> getClientRoles() {
        return clientRoles;
    }

    /**
     * @param roles A list of role values for which a client must have at least 1 of.
     */
    public void setClientRoles(ArrayList<String> roles) {
        this.clientRoles = roles;
    }

    public ArrayList<ReferenceValue> getClientRefs() {
        return clientRefs;
    }

    /**
     * @param refs An array of {@link ReferenceValue} pointers to SCIM User resource to match an acting client
     */
    public void setClientRefs(ArrayList<ReferenceValue> refs) {
        this.clientRefs = refs;
    }

    public ArrayList<Filter> getClientFilters() {
        return clientFilters;
    }

    /**
     * @param filters When a client's resource is retrieved from SCIM, the client resource must match one of the filters specified.
     */
    public void setClientFilters(ArrayList<Filter> filters) {
        this.clientFilters = filters;
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
                this.targetFilter = Filter.parseFilter(item.asText(), null, smgr);
            } catch (BadFilterException e) {
                throw new SchemaException("Invalid filter specified for targetFilter: " + e.getLocalizedMessage());
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
                String sType = aline.substring(0, 3).toLowerCase();
                String[] parts = aline.split("=");
                switch (sType) {
                    case "any":
                        this.isAnyClient = true;
                        continue;
                    case "sel":
                        this.isClientSelf = true;
                        continue;
                    case "rol":
                        if (parts.length < 2)
                            throw new SchemaException("Invalid actor role specified: " + aline);
                        this.clientRoles.add(parts[1].trim());
                        continue;
                        /*
                    case "gro":
                        if(parts.length < 2)
                            throw new SchemaException("Invalid actor group specified: "+aline);
                        this.actorGroups.add(new ReferenceValue(null,parts[1]));
                        continue;

                         */
                    case "ref":
                        if (parts.length < 2)
                            throw new SchemaException("Invalid actor reference specified: " + aline);
                        this.clientRefs.add(new ReferenceValue(null, parts[1]));
                        continue;
                    case "fil":
                        if (parts.length < 2)
                            throw new SchemaException("Invalid actor reference specified: " + aline);
                        try {
                            this.clientFilters.add(Filter.parseFilter(parts[1], null, smgr));
                        } catch (BadFilterException e) {
                            throw new SchemaException("Invalid actor filter specified: " + aline);
                        }
                }
            }
        }

    }

    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
        serialize(gen, ctx, false);
    }

    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException, ScimException {
        gen.writeStartObject();

        gen.writeStringField("path", path);

        if (this.name != null)
            gen.writeStringField("name", this.name);

        if (this.targetFilter != null)
            gen.writeStringField("targetFilter", this.targetFilter.getFilterStr());

        if (this.targetAttrs != null)
            gen.writeStringField("targetAttrs", this.targetAttrs);

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
            gen.writeStringField("rights", builder.toString());
        }

        ArrayList<String> actorVals = new ArrayList<>();
        if (this.isAnyClient) actorVals.add("any");
        if (this.isClientSelf) actorVals.add("self");
        for (String arole : this.clientRoles)
            actorVals.add("role=" + arole);

        for (Filter fval : this.clientFilters)
            actorVals.add("filter=" + fval.getFilterStr());
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

    public JsonNode toJsonNode() {
        ObjectNode node = JsonUtil.getMapper().createObjectNode();
        node.put("path",path);
        if (name != null)
            node.put("name",name);

        if (targetFilter != null)
            node.put("targetFilter",targetFilter.getFilterStr());
        if (targetAttrs != null)
            node.put("targetAttrs",targetAttrs);

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
            node.put("rights",builder.toString());
        }

        ArrayList<String> actorVals = new ArrayList<>();
        if (this.isAnyClient) actorVals.add("any");
        if (this.isClientSelf) actorVals.add("self");
        for (String arole : this.clientRoles)
            actorVals.add("role=" + arole);

        for (Filter fval : this.clientFilters)
            actorVals.add("filter=" + fval.getFilterStr());
        if (actorVals.size() > 0) {
            ArrayNode anode = node.putArray("actors");
            for (String val : actorVals)
                anode.add(val);
        }

        return node;
    }

    public boolean isClientMatch(RequestCtx ctx, SecurityIdentity identity) {
        if (isAnyClient)
            return true;
        if (isClientSelf) {
            Principal pal = identity.getPrincipal();
            String pathId = ctx.getPathId();
            if (pathId != null) {
                if (pal instanceof JWTCallerPrincipal) {
                    JWTCallerPrincipal jpal = (JWTCallerPrincipal) pal;
                    if (jpal.getSubject().equals(pathId))
                        return true;
                } else {
                    String authId = (String) identity.getAttribute(ScimBasicIdentityProvider.ATTR_SELF_ID);
                    if (authId != null && authId.equals(pathId))
                        return true;
                }

            }
        }

        // Try seeing if there is a role match
        Set<String> roles = identity.getRoles();
        if (roles != null && !this.clientRoles.isEmpty()) {
            for (String arole : this.clientRoles) {
                if (roles.contains(arole))
                    return true;
            }
        }

        // Try seting if there is a filter match
        ScimResource actor = (ScimResource) identity.getAttribute(ScimBasicIdentityProvider.ATTR_ACTOR_RES);
        if (actor != null) {
            for (Filter fval : this.clientFilters) {
                try {
                    if (fval.isMatch(actor))
                        return true;
                } catch (BadFilterException e) {
                    // should never happen
                }
            }
        }

        // No actor match so user is not valid.
        return false;
    }

    /**
     * Checks to see if the requestor client can perform the operation requested. Note: targetFilter and targetAttrs are within
     * the Scim Operation. This method is normally called by {@link com.independentid.scim.security.ScimSecurityFilter}
     * @param method The {@link HttpMethod} type for the operation
     * @param ctx    The {@link RequestCtx} containing the Scim Request Context
     * @return true if this ACI permits the operation
     */
    public boolean isOpPermitted(String method, RequestCtx ctx, SecurityIdentity identity) {
        if (isClientMatch(ctx, identity)) {
            if (this.privs.contains(Rights.all))
                return true;
            switch (method) {
                case HttpMethod
                        .GET:
                    if (privs.contains(Rights.read) && ctx.getFilter() == null)
                        return true;
                    if (privs.contains(Rights.search) && ctx.getAcis() != null)
                        return true;
                    if (privs.contains(Rights.compare) && ctx.getFilter() != null)
                        return true;
                    break;
                case HttpMethod.DELETE:
                    if (privs.contains(Rights.delete))
                        return true;
                    break;
                case HttpMethod.PUT:
                case HttpMethod.PATCH:
                    if (privs.contains(Rights.modify))
                        return true;
                    break;
                case HttpMethod.POST:
                    if (ctx.getFilter() != null) {
                        if (privs.contains(Rights.search) || privs.contains(Rights.compare))
                            return true;
                        return false;
                    }
                    return (privs.contains(Rights.add));
            }
        }
        return false;
    }
}
