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
    public final static String SYNC_ID = "id";
    public final static String SYNC_OPCNT = "opNum";
    public final static String SYNC_DATE = "date";
    public final static String SYNC_SOURCE = "source";
    public final static String SYNC_ACTOR = "actor";
    public final static String SYNC_REFS = "refs";
    public final static String SYNC_OPTYPE = "op";

    public final static String TRANS_CONTAINER = "Trans";

    private final static Schema tranSchema;
    private final static Attribute idAttr;
    private final static Attribute dateAttr;
    private final static Attribute opTypAttr;
    private final static Attribute sourceAttr;
    private final static Attribute actorAttr;
    private final static Attribute refsAttr;
    private final static Attribute opCntAttr;

    ResourceType syncType;

    static {

        tranSchema = new Schema(null);
        TransactionRecord.tranSchema.setName("Transaction Record");
        TransactionRecord.tranSchema.setId(ScimParams.SCHEMA_SCHEMA_SYNCREC);

        idAttr = new Attribute(SYNC_ID);
        TransactionRecord.idAttr.setPath(tranSchema.getId(),null);
        TransactionRecord.idAttr.setReturned(Attribute.RETURNED_default);
        TransactionRecord.idAttr.setType(Attribute.TYPE_String);

        opCntAttr = new Attribute(SYNC_OPCNT);
        TransactionRecord.opCntAttr.setPath(tranSchema.getId(),null);
        TransactionRecord.opCntAttr.setReturned(Attribute.RETURNED_default);
        TransactionRecord.opCntAttr.setType(Attribute.TYPE_Integer);

        opTypAttr = new Attribute(SYNC_OPTYPE);
        TransactionRecord.opTypAttr.setPath(tranSchema.getId(),null);
        TransactionRecord.opTypAttr.setReturned(Attribute.RETURNED_default);
        TransactionRecord.opTypAttr.setType(Attribute.TYPE_String);

        dateAttr = new Attribute(SYNC_DATE);
        TransactionRecord.dateAttr.setPath(tranSchema.getId(),null);
        TransactionRecord.dateAttr.setReturned(Attribute.RETURNED_default);
        TransactionRecord.dateAttr.setType(Attribute.TYPE_Date);

        sourceAttr = new Attribute(SYNC_SOURCE);
        TransactionRecord.sourceAttr.setPath(tranSchema.getId(),null);
        TransactionRecord.sourceAttr.setReturned(Attribute.RETURNED_default);
        TransactionRecord.sourceAttr.setType(Attribute.TYPE_String);

        actorAttr = new Attribute(SYNC_ACTOR);
        TransactionRecord.actorAttr.setPath(tranSchema.getId(),null);
        TransactionRecord.actorAttr.setReturned(Attribute.RETURNED_default);
        TransactionRecord.actorAttr.setType(Attribute.TYPE_String);

        refsAttr = new Attribute(SYNC_REFS);
        TransactionRecord.refsAttr.setPath(tranSchema.getId(),null);
        TransactionRecord.refsAttr.setReturned(Attribute.RETURNED_default);
        TransactionRecord.refsAttr.setType(Attribute.TYPE_String);
        TransactionRecord.refsAttr.setMultiValued(true);

        TransactionRecord.tranSchema.putAttribute(idAttr);
        TransactionRecord.tranSchema.putAttribute(opCntAttr);
        TransactionRecord.tranSchema.putAttribute(opTypAttr);
        TransactionRecord.tranSchema.putAttribute(dateAttr);
        TransactionRecord.tranSchema.putAttribute(sourceAttr);
        TransactionRecord.tranSchema.putAttribute(actorAttr);
        TransactionRecord.tranSchema.putAttribute(refsAttr);
    }

    public Operation op;

    public TransactionRecord(SchemaManager schemaManager, String clientId, Operation op) throws SchemaException {
        super(schemaManager);
        //this.op = op;
        initSchemas();
        this.op = op;
        this.id = op.getRequestCtx().getTranId();
        if (this.id == null)
            throw new SchemaException("Unexpected error - missing transaction id");
        addValue(new DateValue(TransactionRecord.dateAttr,op.getStats().getFinishDate()));

        if (clientId != null)
            addValue(new StringValue(TransactionRecord.sourceAttr, clientId));

        String type = op.getScimType();
        if (type != null)
            addValue(new StringValue(TransactionRecord.opTypAttr,type));

        addValue(new IntegerValue(TransactionRecord.opCntAttr,op.getStats().getRequestNumber()));

        SecurityIdentity identity = op.getRequestCtx().getSecSubject();
        if (identity != null)
            addValue(new StringValue(TransactionRecord.actorAttr, identity.getPrincipal().getName()));

        if (op.getResourceId() != null) {
            String ref = op.getResourceType().getEndpoint() + "/" + op.getResourceId();
            Attribute attr = TransactionRecord.refsAttr;
            StringValue val = new StringValue(attr,ref);
            MultiValue mval = new MultiValue(attr, List.of(val));
            addValue(mval);
        }
        this.meta = null; // suppress the meta object.
    }

    private  void initSchemas() {
        super.coreSchema = tranSchema;
        syncType = new ResourceType(smgr);
        syncType.setName(ScimParams.SCHEMA_SCHEMA_SYNCREC);
        syncType.setSchema(ScimParams.SCHEMA_SCHEMA_SYNCREC);

        super.type = syncType;

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

        item = node.get(SYNC_DATE);
        if (item != null) {
            addValue(new DateValue(dateAttr,item));
        }

        item = node.get(SYNC_OPCNT);
        if (item != null)
            addValue(new IntegerValue(opCntAttr,item));

        item = node.get(SYNC_OPTYPE);
        if (item != null)
            addValue(new StringValue(opTypAttr,item));

        item = node.get(SYNC_SOURCE);
        if (item != null)
            addValue(new StringValue(sourceAttr,item));

        item = node.get(SYNC_ACTOR);
        if (item != null)
            addValue(new StringValue(actorAttr,item));

        item = node.get(SYNC_REFS);
        if (item != null)
            addValue(new MultiValue(refsAttr,item,null));

        JsonNode meta = node.get("meta");
        if (meta != null)
            this.meta = new Meta(meta);
    }

}
