package com.independentid.scim.test.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.signals.SsfConfigJsonMigrator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsfConfigJsonMigratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyErrorStateTrueBecomesDisabledWithMigrationErrorMsg() throws Exception {
        JsonNode legacy = mapper.readTree(
                "{\"pushStream\":{\"errorState\":true,\"endpointUrl\":\"https://example.com/events\"}}");

        JsonNode migrated = SsfConfigJsonMigrator.migrate(legacy);

        JsonNode pushStream = migrated.get("pushStream");
        assertThat(pushStream.get("status").asText()).isEqualTo("DISABLED");
        assertThat(pushStream.get("errorMsg").asText()).isEqualTo("migrated from legacy errorState");
        assertThat(pushStream.has("errorState")).isFalse();
        assertThat(pushStream.get("endpointUrl").asText()).isEqualTo("https://example.com/events");
    }

    @Test
    void legacyErrorStateFalseBecomesEnabledWithNoErrorMsg() throws Exception {
        JsonNode legacy = mapper.readTree(
                "{\"pollStream\":{\"errorState\":false,\"endpointUrl\":\"https://example.com/poll\"}}");

        JsonNode migrated = SsfConfigJsonMigrator.migrate(legacy);

        JsonNode pollStream = migrated.get("pollStream");
        assertThat(pollStream.get("status").asText()).isEqualTo("ENABLED");
        assertThat(pollStream.has("errorMsg")).isFalse();
        assertThat(pollStream.has("errorState")).isFalse();
    }

    @Test
    void newShapeJsonPassesThroughUnchanged() throws Exception {
        JsonNode original = mapper.readTree(
                "{\"pushStream\":{\"status\":\"DISABLED\",\"errorMsg\":\"403 Forbidden\",\"endpointUrl\":\"https://example.com/events\"}," +
                "\"pollStream\":{\"status\":\"ENABLED\",\"endpointUrl\":\"https://example.com/poll\"}}");

        JsonNode migrated = SsfConfigJsonMigrator.migrate(original);

        assertThat(migrated.get("pushStream").get("status").asText()).isEqualTo("DISABLED");
        assertThat(migrated.get("pushStream").get("errorMsg").asText()).isEqualTo("403 Forbidden");
        assertThat(migrated.get("pollStream").get("status").asText()).isEqualTo("ENABLED");
    }

    @Test
    void missingBothFieldsLeavesStreamUntouched() throws Exception {
        JsonNode original = mapper.readTree(
                "{\"pushStream\":{\"endpointUrl\":\"https://example.com/events\"}}");

        JsonNode migrated = SsfConfigJsonMigrator.migrate(original);

        JsonNode pushStream = migrated.get("pushStream");
        assertThat(pushStream.has("status")).isFalse();
        assertThat(pushStream.has("errorState")).isFalse();
        assertThat(pushStream.get("endpointUrl").asText()).isEqualTo("https://example.com/events");
    }
}
