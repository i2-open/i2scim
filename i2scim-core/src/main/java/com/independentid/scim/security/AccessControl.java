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

package com.independentid.scim.security;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ReferenceValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTCallerPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final static Logger logger = LoggerFactory
            .getLogger(AccessControl.class);
    public static final String FIELD_NAME = "name";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_TARGETATTRS = "targetAttrs";
    public static final String FIELD_TARGET_FILTER = "targetFilter";
    public static final String FIELD_RIGHTS = "rights";
    public static final String FIELD_ACTORS = "actors";

    public enum Rights {all, add, modify, delete, read, search}

    public enum ActorType {any, self, role, group, ref, filter}

    private final SchemaManager smgr;

    private String path;
    private String targetAttrs = null;
    private Filter targetFilter = null;
    private String name = null;
    private Collection<Rights> privs = new HashSet<>();
    private boolean isClientSelf = false;
    private boolean isAnyClient = false;

    private final HashSet<Attribute> allowedAttrs = new HashSet<>();
    private final HashSet<Attribute> excludedAttrs = new HashSet<>();
    private boolean isAllAttrs = false;

    private ArrayList<String> clientRoles = new ArrayList<>();
    //private ArrayList<ReferenceValue> actorGroups = new ArrayList<>( );
    private ArrayList<ReferenceValue> clientRefs = new ArrayList<>();
    private ArrayList<Filter> clientFilters = new ArrayList<>();
    private RequestCtx aclCtx;

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

    public Collection<Attribute> getAttrsAllowed() { return allowedAttrs; }
    public Collection<Attribute> getAttrsExcluded() { return excludedAttrs; }
    public boolean isAllAttrs() { return isAllAttrs; }

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
        JsonNode item = node.get(FIELD_NAME);
        if (item != null)
            this.name = item.asText();

        item = node.get(FIELD_PATH);
        if (item == null)
            this.path = "/"; // default to global
        else
            this.path = item.asText();

        try {
            aclCtx = new RequestCtx(path,null,null,smgr);
        } catch (ScimException e) {
            //should not happen
            e.printStackTrace();
        }

        item = node.get(FIELD_TARGETATTRS);
        if (item != null) {
            this.targetAttrs = item.asText();
            parseTargetAttrs();
        }

        item = node.get(FIELD_TARGET_FILTER);
        if (item != null) {
            try {
                this.targetFilter = Filter.parseFilter(item.asText(), aclCtx);
            } catch (BadFilterException e) {
                throw new SchemaException("Invalid aci: invalid filter specified for targetFilter: " + e.getLocalizedMessage());
            }
        }

        if (this.targetAttrs == null && this.targetFilter == null)
            throw new SchemaException("Invalid aci: no target specified: \n"+ node);

        item = node.get(FIELD_RIGHTS);
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
            }
        }

        item = node.get(FIELD_ACTORS);
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
                        this.clientRoles.addAll(parseRoles(parts[1].trim()));
                        continue;

                    case "ref":
                        if (parts.length < 2)
                            throw new SchemaException("Invalid actor reference specified: " + aline);
                        this.clientRefs.add(new ReferenceValue(null, parts[1]));
                        continue;
                    case "fil":
                        if (parts.length < 2)
                            throw new SchemaException("Invalid actor reference specified: " + aline);
                        try {
                            this.clientFilters.add(Filter.parseFilter(parts[1], aclCtx));
                        } catch (BadFilterException e) {
                            throw new SchemaException("Invalid actor filter specified: " + aline);
                        }
                }
            }
        }

    }

    private List<String> parseRoles(String val) {
        String[] vals = val.split("[, ]+");
        return Arrays.asList(vals);
    }

    private void parseTargetAttrs() {
        if (targetAttrs == null)
            return;

        String[] elems = targetAttrs.split("[, ]+");
        for(String aname : elems) {
            if (aname.equals("*")) {
                this.isAllAttrs = true;
                continue;
            }
            boolean exclude = false;
            if (aname.startsWith("-")) {
                exclude = true;
                aname = aname.substring(1).trim();  // trim in case a space between - and attr
            }
            Attribute attr = smgr.findAttribute(aname,aclCtx);
            if (attr == null) {
                if (logger.isDebugEnabled())
                    logger.debug("Unable to locate ACL attribute: "+aname);
            }
            if (exclude)
                excludedAttrs.add(attr);
            else
                allowedAttrs.add(attr);
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

    /**
     * Check's to see if the identity provided matches the "actors" requirements of the current rule.
     * @param ctx A {@link RequestCtx} object containing the parsed SCIM request made by the client.
     * @param identity The {@link SecurityIdentity} provided by Quarkus and the login filters.
     * @return True if the client matches the ACI's actor clause
     */
    public boolean isClientMatch(RequestCtx ctx, SecurityIdentity identity) {
        if (isAnyClient())
            return true;
        if (isClientSelf()) {
            Principal pal = identity.getPrincipal();
            String pathId = ctx.getPathId();
            if (pathId != null) {
                if (pal instanceof JWTCallerPrincipal) {
                    JWTCallerPrincipal jpal = (JWTCallerPrincipal) pal;
                    if (jpal.getSubject().equals(pathId))
                        return true;
                } else {
                    String authId = identity.getAttribute(ScimBasicIdentityProvider.ATTR_SELF_ID);
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
        ScimResource actor = identity.getAttribute(ScimBasicIdentityProvider.ATTR_ACTOR_RES);
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
     * if a targetFilter value is specified checks that the resource matfches correctly. If unspecified
     * returns true (as condition is silent).
     * @param res The ScimResource to be evaluated for a match
     * @return true if matched or unspecified
     */
    public boolean isTargetFilterOk(ScimResource res ) {
        if (this.targetFilter == null)
            return true;
        try {
            return targetFilter.isMatch(res);
        } catch (BadFilterException e) {
            //e.printStackTrace();
        }
        return false;
    }
}
