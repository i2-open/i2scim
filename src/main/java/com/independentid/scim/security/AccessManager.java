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
import com.independentid.scim.core.err.ForbiddenException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.DeleteOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PutOp;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

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

    /*
     * acisByPathMap stores acis specific to a path.
     */
    private final HashMap<String,AciSet> acisResContainerMap = new HashMap<>();

    private final HashMap<String,AciSet> childAcis = new HashMap<>();

    private final AciSet globalAcis = new AciSet("/", AccessControl.Rights.all);

    public AccessManager() {

    }

    @PostConstruct
    public void init() throws IOException {
        logger.info("Access Manager starting using: "+this.acisPath);

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

    /**
     * Adds an aci to the root set for the path. Initially all acis for a right are added into the same set. Use AciSet.filterCopy
     * to extract acis for a specific right.
     * @param aci An AccessControl to be added.
     */
    private void addAci(AccessControl aci) {

        String path = aci.getAciPath();
        path = checkPath(path);
        String[] pathElems = path.split("/");

        switch (pathElems.length) {
            case 0: case 1:
                globalAcis.addAci(aci);
                return;
            case 2:
                AciSet set = acisResContainerMap.get(path);
                if (set == null) {
                    set = new AciSet(path, AccessControl.Rights.all);
                    acisResContainerMap.put(path,set);
                }
                set.addAci(aci);
                return;
            default:
                addResourceAci(path,aci);

        }

    }

    private void addResourceAci(String path, AccessControl aci) {

        AciSet set = acisResContainerMap.get(path);
        if (set == null) {
            set = new AciSet(path, AccessControl.Rights.all);
            childAcis.put(path,set);
        }
        set.addAci(aci);
    }

    private String checkPath(String path) {
        String resp = path;
        if (path.contains("//")) {  // if the path contains a full uri, parse out the path
            try {
                URL url = new URL(path);
                resp = url.getPath();
            } catch (MalformedURLException e) {
                return path;
            }
        }
        if (path.startsWith("/v2/"))
            resp = path.substring(3);
        return resp;
    }

    /**
     * Given a path, return all of the ACIs that may apply. This includes ACIs on the full path plus each parent level.
     * The order of acis returned is from longest path first to global.
     * @param ctx The {@link RequestCtx} for which the ACIs are requested
     * @return An {@link AciSet} containing applicable acis in order of resource, container, global acis.
     */
    public AciSet getAcisByPath(RequestCtx ctx) {
        //Create a new AciSet
        String path = ctx.getPath();
        AciSet resp = new AciSet(path, ctx.getRight());

        // Look for resource level ACIs
        if (ctx.getPathId() != null && childAcis.containsKey(path)) {
            AciSet set = childAcis.get(path);
            if (set != null)
                resp.addAll(set.getAcis(), ctx);
        }
        // Look for container ACIs
        if (ctx.getResourceContainer() != null && !ctx.getResourceContainer().equals("/")) {
            String container = "/" + ctx.getResourceContainer();
            if (acisResContainerMap.containsKey(container))
                resp.addAll(acisResContainerMap.get(container).getAcis(), ctx);
        }

        // Add Global Level
        resp.addAll(globalAcis.getAcis(), ctx);

        return resp;
    }

    /**
     * Used to return the ACIs related to a particular resource. This is used by the Meta object
     * to generate the "acis" operational attribute.
     * @param path The path of the resource for home the ACIs are to be returned
     * @return A List of AccessControls comprising the ACIs for the resource indicated by path
     */
    public List<AccessControl> getResourceAcis(String path) {
        ArrayList<AccessControl> resp = new ArrayList<>();
        String[] pathElems = path.split("/");
        if (pathElems.length == 3) {
            AciSet set = childAcis.get(path);
            if (set != null)
                resp.addAll(set.getAcis());
        }
        // Look for container ACIs

        String container = "/" + pathElems[1];
        if (acisResContainerMap.containsKey(container))
            resp.addAll(acisResContainerMap.get(container).getAcis());

        resp.addAll(globalAcis.getAcis());

        return resp;
    }

    /**
     * Given the path (with slashes) for a container, return the container's ACIs. This includes all ACIs
     * regardless of method or client.
     * @param path The path of the container to return (e.g. /Users)
     * @return The AciSet containing the ACIs for a particular path.
     */
    public AciSet getAcisForResourceContainer(String path) {
        return acisResContainerMap.get(path);
    }

    /*
    public AciSet getAcisForResource(RequestCtx ctx) {
        return childAcis.get(ctx.getPath());
    }*/

    /**
     * Normally called by the servlet filter to check if the received request is valid an an HTTP level. The method
     * checks that the HTTP Method is correct given the ACIs defined and the actor involved (specified by SecurityIdentity).
     * If authorized, places the set of ACIs and the SecurityIdentity into the RequestCtx object.
     * @param ctx The {@link RequestCtx} containing the parsed HTTP request.
     * @param identity The {@link SecurityIdentity} performing the request
     * @return true if authorized.
     */
    public boolean filterRequestandInitAcis(RequestCtx ctx, SecurityIdentity identity) {
        ctx.setSecSubject(identity);

        AciSet set = getAcisByPath(ctx);
        if (set == null)
            return false; // path is not permitted
        ctx.setAciSet(set);

        return true;
    }

    public static void checkCreatePreOp(CreateOp op) {
        RequestCtx ctx = op.getRequestCtx();
        AciSet set = ctx.getAcis();

        if (!set.checkCreatePreOp(op))
            // Operation not permitted by any ACI
            markOpUnauthorized(op,"SCIM Create not authorized due to target attribute or filter policy.");
    }

    public static void checkDeletePreOp(DeleteOp op) {
        RequestCtx ctx = op.getRequestCtx();
        AciSet set = ctx.getAcis();

        if (!set.checkDeletePreOp(op))
           // Operation not permitted by any ACI
           markOpUnauthorized(op,"SCIM Delete not authorized due to target attribute or filter policy.");
    }

    public static void checkRetrieveOp(Operation op) {
        RequestCtx ctx = op.getRequestCtx();
        AciSet set = ctx.getAcis();
        if(!set.checkFilterOp(op))
            markOpUnauthorized(op,"SCIM filtered search request not allowed due to attribute/rights policy.");
    }

    public static void checkReturnResults(Operation op) {
        AciSet set = op.getRequestCtx().getAcis();
        ScimResponse resp = op.getScimResponse();
        resp.applyAciSet(set);
        if (logger.isDebugEnabled())
            logger.debug("Retrieve ACIs being evaluated:\n"+ set.getAcis().toString());
    }

    public static void checkPutOp(PutOp op) {
        RequestCtx ctx = op.getRequestCtx();
        AciSet set = ctx.getAcis();
        if (!set.checkPutPreOp(op))
            markOpUnauthorized(op, "SCIM PUT unauthorized due to aci targetFilter rule");
    }

    public static List<Filter> getTargetFilterList(Operation op) {
        RequestCtx ctx = op.getRequestCtx();
        AciSet set = ctx.getAcis();
        ArrayList<Filter> filters = new ArrayList<>();
        for (AccessControl aci : set.getAcis()) {
            Filter filter = aci.getTargetFilter();
            if (filter != null)
                filters.add(filter);
        }
        return filters;
    }

    private static void markOpUnauthorized (Operation op, String reason) {
        op.setCompletionError(new ForbiddenException(reason));
    }

    public String toString() {
        return "AccessManager ("+
                "global="+globalAcis.getAcis().size()+
                ", type="+this.acisResContainerMap.size()+
                ", res="+childAcis.size()+")";
    }
}
