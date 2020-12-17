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

package com.independentid.scim.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.AccessControl;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.TreeMap;

@Startup
@Singleton
@Named("AccessMgr")
public class AccessManager {

    private final static Logger logger = LoggerFactory
            .getLogger(AccessManager.class);

    @ConfigProperty(name = "scim.security.acis.path", defaultValue = "classpath:/schema/acis.json")
    String acisPath;

    @Inject
    @Resource(name="SchemaMgr")
    SchemaManager smgr;

    public static class AciSet {
        public AciSet(String path) {
            this.path = path;
        }
        public final String path;
        public final ArrayList<AccessControl> acis = new ArrayList<>();
    }

    TreeMap<String, AciSet> pathMap = new TreeMap<>();

    public AccessManager() {

    }

    @PostConstruct
    public void init() throws IOException {
        InputStream aciStream = ConfigMgr.getClassLoaderFile(this.acisPath);
        JsonNode node = JsonUtil.getJsonTree(aciStream);
        JsonNode acis = node.get("acis");
        for (JsonNode anode : acis) {
            try {
                AccessControl aci = new AccessControl(smgr, anode);
                if (logger.isDebugEnabled())
                    logger.debug("Loaded ACI: "+aci.toJsonString());
                addAci(aci);
            } catch (ScimException e) {
               logger.error("Error parsing ACI. "+e.getLocalizedMessage());
            }
        }
    }

    public void addAci(AccessControl aci) {
        String path = aci.getAciPath();

        AciSet set = pathMap.get(path);
        if (set == null) {
            set = new AciSet(path);
            pathMap.put(path,set);
        }
        set.acis.add(aci);
    }

    /**
     * Given a path, return all of the ACIs that may apply. This includes ACIs on the full path plus each parent level
     * @param path The path to be evaluated
     * @return The set of ACIs that apply
     */
    public AciSet getAcisByPath(String path) {
        if (path.startsWith("/v2/"))
            path = path.substring(3);
        AciSet resp = new AciSet(path);
        // Do Global Level
        if (pathMap.containsKey("/")) // add global acis if any
            resp.acis.addAll(pathMap.get("/").acis);
        if (path.equals("/"))
            return resp;

        // Look for container ACIs
        String[] comps = path.split("/");
        String container = "/"+comps[1];
        if (pathMap.containsKey(container))
            resp.acis.addAll(pathMap.get(container).acis);

        // Look for resource level ACIs
        if (comps.length == 3 && pathMap.containsKey(path))
            resp.acis.addAll(pathMap.get(path).acis);

        return resp;
    }

    /**
     * @param path The path key to return.
     * @return The ACI set for a specific path. Does not include parent ACIs
     */
    public AciSet getAcisByKey(String path) {
        return pathMap.get(path);
    }

    public boolean isOperationValid(RequestCtx ctx, SecurityIdentity identity, HttpServletRequest req) {
        String method = req.getMethod();

        AciSet set = getAcisByPath(ctx.getPath());
        set.acis.removeIf(aci -> !aci.isOpPermitted(method,ctx,identity));

        if (set.acis.isEmpty())
            return false;
        ctx.setAccessContext(identity,set);
        return true;
    }

    public String toString() { return "AccessManager";}
}
