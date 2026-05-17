package com.independentid.scim.test.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.signals.IdentifierExtractor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Unit tests for {@link IdentifierExtractor} — the pure extraction of a
 * {@code User}'s primary login identifier value from a {@link ScimResource}.
 * Slice 3 (#89).
 */
@QuarkusTest
@TestProfile(RiscMapperTestProfile.class)
public class IdentifierExtractorTest {
    private final static Logger logger = LoggerFactory.getLogger(IdentifierExtractorTest.class);

    @Inject
    SchemaManager schemaManager;

    /** Builds a User {@link ScimResource} from a JSON document. */
    private ScimResource user(String json) throws Exception {
        JsonNode node = JsonUtil.getJsonTree(json);
        return new ScimResource(schemaManager, node, "Users");
    }

    @Test
    public void singularUserNameExtracted() {
        logger.info("IdentifierExtractor: singular userName");
        try {
            ScimResource res = user("{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],"
                    + "\"userName\":\"alice@example.com\"}");
            assertThat(IdentifierExtractor.primaryValue(res, "userName"))
                    .as("Singular userName extracted").isEqualTo("alice@example.com");
        } catch (Exception e) {
            fail("Error extracting singular userName: " + e.getMessage(), e);
        }
    }

    @Test
    public void absentIdentifierReturnsNull() {
        logger.info("IdentifierExtractor: an absent identifier");
        try {
            ScimResource res = user("{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],"
                    + "\"userName\":\"alice@example.com\"}");
            assertThat(IdentifierExtractor.primaryValue(res, "emails"))
                    .as("Absent emails identifier extracts as null").isNull();
        } catch (Exception e) {
            fail("Error extracting absent identifier: " + e.getMessage(), e);
        }
    }

    @Test
    public void loneEmailTreatedAsPrimary() {
        logger.info("IdentifierExtractor: a lone email with no primary flag");
        try {
            ScimResource res = user("{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],"
                    + "\"userName\":\"alice@example.com\","
                    + "\"emails\":[{\"value\":\"only@example.com\",\"type\":\"work\"}]}");
            assertThat(IdentifierExtractor.primaryValue(res, "emails"))
                    .as("Lone email value treated as primary").isEqualTo("only@example.com");
        } catch (Exception e) {
            fail("Error extracting lone email: " + e.getMessage(), e);
        }
    }

    @Test
    public void multiValuedEmailsPrimaryExtracted() {
        logger.info("IdentifierExtractor: emails with an explicit primary");
        try {
            ScimResource res = user("{\"schemas\":[\"urn:ietf:params:scim:schemas:core:2.0:User\"],"
                    + "\"userName\":\"alice@example.com\","
                    + "\"emails\":[{\"value\":\"home@example.com\",\"type\":\"home\"},"
                    + "{\"value\":\"work@example.com\",\"type\":\"work\",\"primary\":true}]}");
            assertThat(IdentifierExtractor.primaryValue(res, "emails"))
                    .as("Primary email value extracted").isEqualTo("work@example.com");
        } catch (Exception e) {
            fail("Error extracting primary email: " + e.getMessage(), e);
        }
    }
}
