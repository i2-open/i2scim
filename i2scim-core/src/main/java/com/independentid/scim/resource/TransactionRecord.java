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

package com.independentid.scim.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.*;

import java.security.Principal;
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

        Principal userPrincipal = op.getRequestCtx().getPrincipal();
        if (userPrincipal != null) {
            addValue(new StringValue(SystemSchemas.actorAttr,userPrincipal.getName()));
        }
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
        super.mainSchema = smgr.getSchemaById(ScimParams.SCHEMA_SCHEMA_SYNCREC);

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
