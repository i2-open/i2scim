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

package com.independentid.scim.test.mongo;


import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.IScimProvider;
import com.independentid.scim.backend.mongo.MongoProvider;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.test.misc.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Note this test may have some timing issues when run in debug stepping mode. Tests b thru d expects schema to be
 * initialized fairly quickly.
 */
@QuarkusTest
@TestProfile(ScimMongoTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class MongoConfigTest {

    private static final Logger logger = LoggerFactory.getLogger(MongoConfigTest.class);

    @Inject
    @Resource(name = "SchemaMgr")
    SchemaManager smgr;

    @Inject
    TestUtils testUtils;

    static IScimProvider provider = null;

    static Instant startTime = Instant.now();

    @Test
    public void a_beanCheckTest() {
        try {
            testUtils.resetProvider(true);
        } catch (ScimException | BackendException | IOException e) {
            Assertions.fail("Failed to reset provider: " + e.getMessage());
        }
        provider = InjectionManager.getInstance().getProvider();
        assertThat(provider).isNotNull();

        logger.info("==========   MongoConfig Tests ==========");

        logger.info("\t* Running initial persistance provider checks");
        assertThat(provider).as("MongoProvider is defined.").isNotNull();

        assertThat(provider.ready()).as("MongoProvider is ready").isTrue();

        logger.info("\t* Check that default schema was loaded by SchemaManager");
        Schema userById = smgr.getSchemaById("urn:ietf:params:scim:schemas:core:2.0:User");
        Schema userByName = smgr.getSchemaByName("User");

        assertThat(userById == userByName).as("Is the same Schema config instance").isTrue();
        assertThat(userById).as("Schema content equality is true").isEqualTo(userByName);

        assertThat(smgr.isSchemaLoadedFromProvider())
                .as("Confirm schema was loaded from default")
                .isFalse();
    }

    /**
     * After the initial reset boot up, syncConfig should have been run. If so, a {@link PersistStateResource} should be
     * available
     */
    @Test
    public void b_checkForConfigState() throws ParseException, ScimException, IOException {
        logger.info("\t* Checking for stored PersistStateResource");
        PersistStateResource cfgState = null;
        int cnt = 0;
        while (cfgState == null && cnt < 5) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            cnt++;
            cfgState = provider.getConfigState();
        }

        assertThat(cfgState)
                .as("Check configstate was persisted")
                .isNotNull();
    }

    /**
     * Now that config state was persisted, check that SchemaManager and provider agree.
     */
    @Test
    public void c_compareSchemaAndTypeCounts() throws ScimException {
        logger.info("\t* Checking schema and type count matches");
        Collection<Schema> mSchemas = provider.loadSchemas();

        assertThat(mSchemas.size())
                .as("Schema count is the same")
                .isEqualTo(smgr.getSchemaCnt());

        assertThat(provider.loadResourceTypes().size())
                .as("Check Resource Type count matches")
                .isEqualTo(smgr.getResourceTypeCnt());

    }


    @Test
    public void d_PersistedConfig() {

        logger.info("\t* Checking PersistStateResource");

        try {
            MongoProvider mprovider = (MongoProvider) provider;
            PersistStateResource cnfRes = mprovider.getConfigState();
            assertThat(cnfRes)
                    .as("Check for persisted config resource not Null")
                    .isNotNull();

            assertThat(cnfRes.getResTypeCnt())
                    .as("Check type count matches")
                    .isEqualTo(smgr.getResourceTypeCnt());
            assertThat(cnfRes.getSchemaCnt())
                    .as("Check that schema count persisted matches")
                    .isEqualTo(smgr.getSchemaCnt());

            assertThat(cnfRes.getLastSyncDate().toInstant())
                    .as("Check date is before now")
                    .isBefore(Instant.now());

            assertThat(cnfRes.getLastSyncDate().toInstant())
                    .as("Check sync date is after start time of test.")
                    .isAfter(startTime);

        } catch (ScimException | IOException | ParseException e) {
            logger.error("Error while loading persistant state config resource", e);
            fail("Error while loading persistant state config resource", e);
        }
    }

    /**
     * Check that a restart (withhout reset) properly loads the configs.
     */
    @Test
    public void e_CheckRestart() throws ScimException {
        logger.info("\t* Restart and re-load provider and SchemaManager");
        provider.shutdown();
        smgr.resetConfig();

            // This time, the schema should be loaded from MongoProvider
        smgr.init();

        try {
            provider.init();
        } catch (BackendException e) {
            logger.error("Error while restarting provider", e);
            fail("Error while restarting provider", e);
        }

        assertThat(smgr.isSchemaLoadedFromProvider())
                .as("Confirm schema was loaded from provider")
                .isTrue();
        //re-run the count test.
        c_compareSchemaAndTypeCounts();

    }


}
