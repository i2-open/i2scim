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
import com.independentid.scim.resource.AccessControl;
import com.independentid.scim.serializer.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.TreeMap;

@Singleton
@Startup
@Named("AccessMgr")
public class AccessManager {
    private final static Logger logger = LoggerFactory
            .getLogger(AccessManager.class);

    @Inject
    ConfigMgr cmgr;

    public static class AciSet {
        public AciSet(String path) {
            this.path = path;
        }
        public String path;
        public ArrayList<AccessControl> acis = new ArrayList<>();
    }

    TreeMap<String, AciSet> pathMap = new TreeMap<>();

    public AccessManager() {

    }

    @PostConstruct
    public void init() throws IOException {
        InputStream aciStream = cmgr.getAcisStream();
        JsonNode node = JsonUtil.getJsonTree(aciStream);
        JsonNode acis = node.get("acis");
        for (JsonNode anode : acis) {
            try {
                AccessControl aci = new AccessControl(anode);
                if (logger.isDebugEnabled())
                    logger.debug("Loaded ACI: "+aci.toJsonString());
                addAci(aci);
            } catch (ScimException e) {
               logger.error("Error parsing ACI. "+e.getLocalizedMessage());
            }
        }
    }

    public void addAci(AccessControl aci) {
        String path = aci.getPath();

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


}
