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

package com.independentid.scim.plugin;

import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;
import com.independentid.scim.schema.SchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

@Singleton
@Named("ScimPlugins")
public class PluginHandler {
    private final Logger logger = LoggerFactory.getLogger(PluginHandler.class);
    @Inject
    Instance<IScimPlugin> pluginsInjected;

    @Inject
    @Resource(name="SchemaMgr")
    SchemaManager schemaManager;

    ArrayList<IScimPlugin> plugins = new ArrayList<>();
    ArrayList<IScimPlugin> revPlugins = new ArrayList<>();

    public PluginHandler () {

    }

    @PostConstruct
    public void initialize() {
        if (pluginsInjected.isUnsatisfied())
            logger.warn("No SCIM plugins configured.");
        else {
            StringBuilder plist = new StringBuilder();
            Iterator<IScimPlugin> iter = pluginsInjected.iterator();
            while (iter.hasNext()) {
                IScimPlugin plugin = iter.next();
                plugins.add(plugin);
                revPlugins.add(plugin);
                plist.append(plugin.getClass().getName());
                if (iter.hasNext())
                    plist.append(", ");
                try {
                    plugin.init(schemaManager);
                } catch (ServletException e) {
                    logger.error("Error initializing plugin: "+plugin.getClass().getName()+": "+e.getLocalizedMessage(),e);
                    iter.remove();;
                }

            }

            Collections.reverse(revPlugins);

            //plugins.sort(Comparator.comparing(IScimPlugin::getPriority));

            logger.info("Loaded SCIM Plugins: " + plist);

        }
    }

    public void doPreOperations(Operation op) throws ScimException {
        for (IScimPlugin iScimPlugin : plugins)
            iScimPlugin.doPreOperation(op);

    }

    public void doPostOperations(Operation op) throws ScimException {
        for (IScimPlugin iScimPlugin : revPlugins)
            iScimPlugin.doPostOperation(op);
    }
}
