/*
 * Copyright (c) 2021.
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

package com.independentid.scim.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.*;
import io.quarkus.security.identity.SecurityIdentity;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class TransactionRecord extends ScimResource {

    public Operation op;

    public TransactionRecord(SchemaManager schemaManager, JsonNode node, String container) throws ScimException, ParseException {
        super(schemaManager,node,null,container);
    }

    public TransactionRecord(SchemaManager schemaManager, String clientId, Operation op) throws SchemaException {
        super(schemaManager);
        //this.op = op;
        initSchemas();
        this.op = op;
        this.id = op.getRequestCtx().getTranId();
        if (this.id == null)
            throw new SchemaException("Unexpected error - missing transaction id");
        addValue(new DateValue(SystemSchemas.dateAttr,op.getStats().getFinishDate()));

        if (clientId != null)
            addValue(new StringValue(SystemSchemas.sourceAttr, clientId));

        String type = op.getScimType();
        if (type != null)
            addValue(new StringValue(SystemSchemas.opTypAttr,type));

        addValue(new IntegerValue(SystemSchemas.opCntAttr,op.getStats().getRequestNumber()));

        SecurityIdentity identity = op.getRequestCtx().getSecSubject();
        if (identity != null)
            addValue(new StringValue(SystemSchemas.actorAttr, identity.getPrincipal().getName()));

        if (op.getResourceId() != null) {
            String ref = op.getResourceType().getEndpoint() + "/" + op.getResourceId();
            Attribute attr = SystemSchemas.refsAttr;
            StringValue val = new StringValue(attr,ref);
            MultiValue mval = new MultiValue(attr, List.of(val));
            addValue(mval);
        }
        this.meta = null; // suppress the meta object.
    }

    private  void initSchemas() {
        super.coreSchema = smgr.getSchemaById(ScimParams.SCHEMA_SCHEMA_SYNCREC);

        super.type = smgr.getResourceTypeById(ScimParams.SCHEMA_SCHEMA_SYNCREC);

        schemas = new ArrayList<>();
        schemas.add(ScimParams.SCHEMA_SCHEMA_SYNCREC);
    }

    public Operation getOp() {return this.op;}

    public void parseJson(JsonNode node, SchemaManager schemaManager) throws ParseException, ScimException {

        initSchemas();

        JsonNode item = node.get("id");
        if (item != null)
            this.id = item.asText();

        item = node.get("externalId");
        if (item != null)
            this.externalId = item.asText();

        item = node.get(SystemSchemas.SYNC_DATE);
        if (item != null) {
            addValue(new DateValue(SystemSchemas.dateAttr,item));
        }

        item = node.get(SystemSchemas.SYNC_OPCNT);
        if (item != null)
            addValue(new IntegerValue(SystemSchemas.opCntAttr,item));

        item = node.get(SystemSchemas.SYNC_OPTYPE);
        if (item != null)
            addValue(new StringValue(SystemSchemas.opTypAttr,item));

        item = node.get(SystemSchemas.SYNC_SOURCE);
        if (item != null)
            addValue(new StringValue(SystemSchemas.sourceAttr,item));

        item = node.get(SystemSchemas.SYNC_ACTOR);
        if (item != null)
            addValue(new StringValue(SystemSchemas.actorAttr,item));

        item = node.get(SystemSchemas.SYNC_REFS);
        if (item != null)
            addValue(new MultiValue(SystemSchemas.refsAttr,item,null));

        JsonNode meta = node.get("meta");
        if (meta != null)
            this.meta = new Meta(meta);
    }

}
