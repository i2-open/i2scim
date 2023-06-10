package com.independentid.scim.core;

import com.independentid.scim.backend.IIdentifierGenerator;
import com.independentid.scim.backend.IScimProvider;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class InjectionManager {

    private final static Logger logger = LoggerFactory.getLogger(InjectionManager.class);

    private static IIdentifierGenerator generator;

    private static Instance<IIdentifierGenerator> generators;

    private static Instance<IScimProvider> providers;

    private static String providerName;

    private static InjectionManager singleton;

    private static IScimProvider provider = null;

    private static boolean initialized = false;

    private static ConfigMgr configMgr;

    private InjectionManager() {
    }

    // This is used when InjectionManager is created outside of normal CDI injection
    public synchronized static InjectionManager getInstance() {
        if (singleton != null)
            return singleton;
        singleton = new InjectionManager();
        logger.info("Initializing Injection Manager...");
        IScimProvider prov = singleton.getProvider();
        IIdentifierGenerator gen = singleton.getGenerator();
        if (prov == null | gen == null)
            logger.error("Provider and Identifier generators did not initialize");
        // Initialization needs to occur later to give schemamanager time to start.
        return singleton;
    }

    public IIdentifierGenerator getGenerator() {
        if (!initialized)
            getProvider();
        return generator;
    }

    private synchronized void initGenerator() {
        logger.info("Initializing identifier generator.");
        if (generators == null) {
                generators = CDI.current().select(IIdentifierGenerator.class);
        }
        if (generators == null || generators.isUnsatisfied()) {
            System.err.println("**** No transaction generators beans found (including CDI loader) ****");
        }
        String name = provider.getGeneratorClass();
        logger.info("Looking for generator matching ["+name+"]");
        for (IIdentifierGenerator gen : generators) {
            String provClass = gen.getClass().getName();  // fix because provName may have "proxy" at end
            logger.info("  Generator: "+provClass);
            if (provClass.startsWith(name)) {
                generator = gen;
                logger.info("Matched.");
                // This makes generator available via schemaManager.
                return;
            }
        }
    }

    public synchronized IScimProvider getProvider() {
        if (provider == null) {

            CDI<Object> cur;

            try {
                cur = CDI.current();
                providers = cur.select(IScimProvider.class);

                if (providers == null || providers.isUnsatisfied()) {
                    logger.error("*** No backend IScimProvider beans detected. ***");
                }
            } catch (IllegalStateException e) {
                logger.error("ERROR: Unable to instantiate CDI");
            }


            // lookup the requested scim provider
            Config config = ConfigProvider.getConfig();

            Optional<String> optProv = config.getOptionalValue("scim.prov.providerClass", String.class);
            optProv.ifPresent(s -> providerName = s);

            if (providers.isAmbiguous()) {   // More than one found
                logger.warn("Multiple backend providers found...selecting one.");
                for (IScimProvider prov : providers) {
                    String provClass = prov.getClass().getName();  //note provClass may have "proxy" proxy at the end
                    logger.debug("Checking provider class: " + provClass + " for match against " + providerName);
                    if (provClass.startsWith(providerName)) {
                        logger.debug("Requested provider matched!");
                        provider = prov;
                    }
                }
            } else {
                provider = providers.get();
                String foundName = provider.getClass().getName();
                if (providerName == null) {
                    providerName = foundName;
                    logger.info("Single backend provider detected, selecting: " + providerName);
                } else {
                    if (foundName.startsWith(providerName))  // foundName may have ClientProxy at the end
                        logger.info("Matched requested provider");
                    else {
                        providerName = foundName;
                        logger.warn("Requested backend provider not found. Using: "+foundName);
                    }
                }
            }
            if (provider == null) {
                logger.error("Backend provider was not matched ("+((providerName != null)?providerName:"NULL")+").");
                System.exit(1);
            }
            initGenerator();

            Instance<ConfigMgr> configInstance = CDI.current().select(ConfigMgr.class);
            configMgr = configInstance.get();

            initialized = true;
        }
        return provider;
    }


    public synchronized String getProviderName()  {
        if (providerName == null) {
            Optional<String> name = ConfigProvider.getConfig().getOptionalValue("scim.prov.providerClass", String.class);
            providerName = name.orElse("com.independentid.scim.backend.mongo.MongoProvider");
        }
        return providerName;
    }

    /**
     * Obtains an identifier that acceptable to the backend provider.
     * @return A unique identifier String for a transaction or resource identifier
     */
    public String generateTransactionId() {
        if (!initialized)
            getProvider();
        return generator.getNewIdentifier();
    }

    public ConfigMgr getConfigMgr() {
        if (!initialized)
            getProvider();
        return configMgr;
    }

}
