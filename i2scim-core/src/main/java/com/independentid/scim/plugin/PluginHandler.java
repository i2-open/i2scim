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

package com.independentid.scim.plugin;

import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;
import com.independentid.scim.schema.SchemaManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                    plugin.init();
                } catch (ServletException e) {
                    logger.error("Error initializing plugin: "+plugin.getClass().getName()+": "+e.getLocalizedMessage(),e);
                    iter.remove();
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
