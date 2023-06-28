package com.independentid.scim.test.events;


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.memory.MemoryProvider;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.schema.SchemaManager;
import jakarta.annotation.Priority;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

@Singleton
@Priority(6)
public class TestUtils {

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @ConfigProperty(name = "scim.prov.memory.persist.dir", defaultValue = "./scimdata")
    String storeDir;

    @Inject
    MemoryProvider memoryProvider;

    public static String mapPathToReqUrl(URL baseUrl, String path) throws MalformedURLException {
        URL rUrl = new URL(baseUrl, path);
        return rUrl.toString();
    }

    public void resetMemoryDb() throws ScimException, BackendException, IOException {
        logger.warn("\t*** Resetting Memory Provider Data [" + storeDir + "] ***");
        // Shut the provider down
        if (memoryProvider.ready())
            memoryProvider.shutdown();
        smgr.resetConfig();

        resetMemDirectory();

        // restart and reload
        smgr.init();
        memoryProvider.init();

    }

    public void resetMemDirectory() {
        // Reset the memory provider
        logger.warn("\t*** Resetting Memory database files in " + storeDir + " ***");
        File memdir = new File(storeDir);
        File[] files = memdir.listFiles();
        if (files != null)
            for (File afile : files)
                afile.delete();

    }
}
