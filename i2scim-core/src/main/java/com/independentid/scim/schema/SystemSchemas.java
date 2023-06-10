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

package com.independentid.scim.schema;

import com.independentid.scim.protocol.ScimParams;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/**
 * This class holds internal schemas not defined externally. They are used to co-ordinate synchronization of schema
 * within providers and to define the schema for the transaction log. The transaction log is used to determine if a
 * transaction id is already held in the current database provider instance (for de-duping and diagnosis purposes).
 */
@ApplicationScoped
public class SystemSchemas {

    @Inject
    SchemaManager smgr;

    public final static List<String> SCIM_COMMON_ATTRS = Arrays.asList(
            "id","externalid","schemas","meta");

    public final static String SYNC_ID = "id";
    public final static String SYNC_OPCNT = "opNum";
    public final static String SYNC_DATE = "date";
    public final static String SYNC_SOURCE = "source";
    public final static String SYNC_ACTOR = "actor";
    public final static String SYNC_REFS = "refs";
    public final static String SYNC_OPTYPE = "op";

    public final static String TRANS_CONTAINER = "Trans";

    public static Attribute idAttr;
    public static Attribute dateAttr;
    public static Attribute opTypAttr;
    public static Attribute sourceAttr;
    public static Attribute actorAttr;
    public static Attribute refsAttr;
    public static Attribute opCntAttr;
    public static Attribute saltAttr;


    public static String FIELD_RTYPE_CNT = "rTypeCnt";
    public static String FIELD_LAST_SYNC = "lastSyncDate";
    public static String FIELD_SCHEMA_CNT = "schemaCnt";
    public final static String FIELD_SYNC_SALT = "salt";

    public static String RESTYPE_CONFIG = ScimParams.PATH_SERV_PROV_CFG;

    public static Attribute rTypeCntAttr;
    public static Attribute syncDateAttr;
    public static Attribute sCntAttr;

    static Schema persistSchema ;
    static ResourceType persistType ;

    static Schema tranSchema ;
    static ResourceType tranType ;

    @PostConstruct
    void defineConfigStateSchema() {
        // Define the schema synchronization state schema (persist4ed state)
        persistSchema = new Schema(smgr);
        persistSchema.setName("Persisted Configuration State");
        persistSchema.setId(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);

        syncDateAttr = createAttribute(FIELD_LAST_SYNC,persistSchema.getId(),Attribute.TYPE_Date);
        rTypeCntAttr = createAttribute(FIELD_RTYPE_CNT,persistSchema.getId(),Attribute.TYPE_Integer);
        sCntAttr = createAttribute(FIELD_SCHEMA_CNT,persistSchema.getId(),Attribute.TYPE_Integer);
        saltAttr = createAttribute(FIELD_SYNC_SALT,persistSchema.getId(), Attribute.TYPE_Binary);

        persistSchema.putAttribute(syncDateAttr);
        persistSchema.putAttribute(rTypeCntAttr);
        persistSchema.putAttribute(sCntAttr);
        persistSchema.putAttribute(saltAttr);

        persistType = new ResourceType(smgr);
        persistType.setName(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
        persistType.setSchema(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);

        // Now the transaction record schema and type
        tranSchema = new Schema(null);
        tranSchema.setName("Transaction Record");
        tranSchema.setId(ScimParams.SCHEMA_SCHEMA_SYNCREC);

        idAttr = createAttribute(SYNC_ID,tranSchema.getId(), Attribute.TYPE_String);

        opCntAttr = createAttribute(SYNC_OPCNT,tranSchema.getId(), Attribute.TYPE_Integer);
        opTypAttr = createAttribute(SYNC_OPTYPE,tranSchema.getId(), Attribute.TYPE_String);
        dateAttr = createAttribute(SYNC_DATE,tranSchema.getId(), Attribute.TYPE_Date);
        sourceAttr = createAttribute(SYNC_SOURCE,tranSchema.getId(), Attribute.TYPE_String);
        actorAttr = createAttribute(SYNC_ACTOR,tranSchema.getId(), Attribute.TYPE_String);
        refsAttr = createAttribute(SYNC_REFS,tranSchema.getId(), Attribute.TYPE_String);
        refsAttr.setMultiValued(true);

        tranSchema.putAttribute(idAttr);
        tranSchema.putAttribute(opCntAttr);
        tranSchema.putAttribute(opTypAttr);
        tranSchema.putAttribute(dateAttr);
        tranSchema.putAttribute(sourceAttr);
        tranSchema.putAttribute(actorAttr);
        tranSchema.putAttribute(refsAttr);

        tranType = new ResourceType(smgr);
        tranType.setName(ScimParams.SCHEMA_SCHEMA_SYNCREC);
        tranType.setSchema(ScimParams.SCHEMA_SCHEMA_SYNCREC);
        try {
            tranType.setEndpoint(new URI("/" + TRANS_CONTAINER));
        } catch (URISyntaxException ignore) {

        }
    }

    private Attribute createAttribute(String name, String schema, String type) {
        Attribute attr = new Attribute(name);
        attr.setPath(schema, null);
        attr.setReturned(Attribute.RETURNED_default);
        attr.setType(type);
        return attr;
    }

    public boolean isSystemSchema(String id) {
        if (id == null) return false;
        switch (id) {
            case ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE:
            case ScimParams.SCHEMA_SCHEMA_SYNCREC:
                return true;
        }
        return false;
    }

    public Schema getSystemSchema(String id) {
        if (persistSchema == null)
            defineConfigStateSchema();
        switch (id) {
            case ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE:
                return persistSchema;
            case ScimParams.SCHEMA_SCHEMA_SYNCREC:
                return tranSchema;
        }

        return null;
    }

    public ResourceType getSystemResTypeBySchemaId(String id) {
        if (persistSchema == null)
            defineConfigStateSchema();
        switch (id) {
            case ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE:
                return persistType;
            case ScimParams.SCHEMA_SCHEMA_SYNCREC:
                return tranType;
        }

        return null;
    }

    public ResourceType getSystemResTypeByPath(String path) {
        if (persistSchema == null)
            defineConfigStateSchema();
        switch (path) {
            case ScimParams.PATH_SERV_PROV_CFG:
                return persistType;
            case TRANS_CONTAINER:
                return tranType;
        }

        return null;
    }

}
