package com.independentid.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class SsfConfigJsonMigrator {

    private static final String LEGACY_FIELD = "errorState";
    private static final String STATUS_FIELD = "status";
    private static final String ERROR_MSG_FIELD = "errorMsg";
    private static final String MIGRATION_REASON = "migrated from legacy errorState";

    private SsfConfigJsonMigrator() {
    }

    public static JsonNode migrate(JsonNode root) {
        if (root == null || !root.isObject()) {
            return root;
        }
        migrateStream((ObjectNode) root, "pushStream");
        migrateStream((ObjectNode) root, "pollStream");
        return root;
    }

    private static void migrateStream(ObjectNode root, String streamField) {
        JsonNode stream = root.get(streamField);
        if (stream == null || !stream.isObject()) {
            return;
        }
        ObjectNode streamObj = (ObjectNode) stream;
        if (streamObj.has(STATUS_FIELD) || !streamObj.has(LEGACY_FIELD)) {
            return;
        }
        boolean wasError = streamObj.get(LEGACY_FIELD).asBoolean(false);
        streamObj.remove(LEGACY_FIELD);
        if (wasError) {
            streamObj.put(STATUS_FIELD, StreamStatus.DISABLED.name());
            streamObj.put(ERROR_MSG_FIELD, MIGRATION_REASON);
        } else {
            streamObj.put(STATUS_FIELD, StreamStatus.ENABLED.name());
        }
    }
}
