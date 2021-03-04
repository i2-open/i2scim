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

package com.independentid.scim.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.*;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;

/**
 * @author pjdhunt
 * Class to track the synchronization state of system schema and resource type definitions. This provides
 * the ability to detect whether initial provisioning of default schema has been fully sync'd to database
 * as well as to detect changes in memory that may not have been perissted.
 */
public class PersistStateResource extends ScimResource {

	public PersistStateResource(SchemaManager schemaManager, JsonNode resourceNode, IBulkIdResolver bulkIdResolver, String container)
			throws ParseException, ScimException {
		super(schemaManager, resourceNode, bulkIdResolver, container);
	}
	
	public PersistStateResource(SchemaManager schemaManager, int rCnt, int sCnt) {
		super(schemaManager);

		initSchemas();
		try {
			super.addValue(new IntegerValue(SystemSchemas.rTypeCntAttr,rCnt));
			super.addValue(new IntegerValue(SystemSchemas.sCntAttr,sCnt));
			super.addValue(new DateValue(SystemSchemas.syncDateAttr,new Date(System.currentTimeMillis())));
		} catch (SchemaException e) {
			e.printStackTrace();
		}
	}

	private void initSchemas() {
		coreSchema = smgr.getSchemaById(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
		setId(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
		type = smgr.getResourceTypeById(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);

		this.container = SystemSchemas.RESTYPE_CONFIG;

		this.schemas = new ArrayList<>();
		this.schemas.add(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
	}
	
	public Date getLastSyncDate() {
		DateValue val = (DateValue) getValue(SystemSchemas.syncDateAttr);
		return val.getDateValue();
	}
	
	public String getLastSync() {
		DateValue val = (DateValue) getValue(SystemSchemas.syncDateAttr);
		return val.toString();
	}	
	
	public int getResTypeCnt() {
		IntegerValue val = (IntegerValue) getValue(SystemSchemas.rTypeCntAttr);
		return val.getRawValue();
	}
	
	public int getSchemaCnt() {
		IntegerValue val = (IntegerValue) getValue(SystemSchemas.sCntAttr);

		return val.getRawValue();
	}


	public void parseJson(JsonNode node, SchemaManager schemaManager) throws ParseException, ScimException {

		initSchemas();

		JsonNode item = node.get("id");
		if (item != null) 
			this.id = item.asText();
		
		item = node.get("externalId");
		if (item != null)
			this.externalId = item.asText();
		
		item = node.get(SystemSchemas.FIELD_LAST_SYNC);
		if (item != null) {
			addValue(new DateValue(SystemSchemas.syncDateAttr,item));
		}

	
		item = node.get(SystemSchemas.FIELD_RTYPE_CNT);
		if (item != null)
			addValue(new IntegerValue(SystemSchemas.rTypeCntAttr,item));

		item = node.get(SystemSchemas.FIELD_SCHEMA_CNT);
		if (item != null)
			addValue(new IntegerValue(SystemSchemas.sCntAttr,item));

		JsonNode meta = node.get("meta");
		if (meta != null)
			this.meta = new Meta(meta);
	}

	@Override
	public String toString() {
		return this.toJsonString();

	}



}
