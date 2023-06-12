package com.independentid.scim.test.set;

import com.independentid.scim.test.events.SsefEventTestProfile;
import com.independentid.set.SecurityEventBuilder;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusTest
@TestProfile(SsefEventTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SetTest {
    private final static Logger logger = LoggerFactory.getLogger(SetTest.class);
    @Test
    public void a_CreateSetTests() {
        SecurityEventBuilder builder = SecurityEventBuilder.Claims();

        builder.audience("abc");
        builder.issuer("DEFAULT");

        String tokenString= builder.toString();

        logger.info("Token: \n"+tokenString);
    }
}
