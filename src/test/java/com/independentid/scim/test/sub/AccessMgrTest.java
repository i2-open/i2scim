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

package com.independentid.scim.test.sub;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.resource.AccessControl;
import com.independentid.scim.security.AccessManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class AccessMgrTest {
    private final Logger logger = LoggerFactory.getLogger(AccessMgrTest.class);

    @Inject
    @Resource(name="ConfigMgr")
    ConfigMgr cmgr;

    @Inject
    @Resource(name="AccessMgr")
    AccessManager amgr;

    @Test
    public void a_AmgrInitTest() {
        assertThat(amgr)
                .as("Access Manger is available")
                .isNotNull();

        AccessManager.AciSet set = amgr.getAcisByPath("/");
        assertThat(set.acis.size())
                .as("Some ACI returned for root")
                .isGreaterThan(0);

        set = amgr.getAcisByPath("/ServiceProviderConfig");

        AccessManager.AciSet setnode = amgr.getAcisByKey("/ServiceProviderConfig");
        assertThat(set.acis.size())
                .as("Check aci by path has more acis than by node")
                .isGreaterThan(setnode.acis.size());
        assertThat(setnode.acis.size())
                .as("At least one ACI for ServiceProviderConfig")
                .isGreaterThan(0);
        AccessControl aci = setnode.acis.get(0);
        logger.info("ServiceProviderConfig aci\n"+aci.toString());
        assertThat(aci.isActorAny())
                .as("ServiceProviderConfig aci applies to any actor")
                .isTrue();
    }
}
