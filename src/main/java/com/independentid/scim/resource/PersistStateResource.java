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

	public static String RESTYPE_CONFIG = ScimParams.PATH_SERV_PROV_CFG;
	public static String CONFIG_ID = "ConfigState";
	public static String FIELD_LAST_SYNC = "lastSyncDate";
	public static String FIELD_RTYPE_CNT = "rTypeCnt";
	public static String FIELD_SCHEMA_CNT = "schemaCnt";

	static final Attribute syncDateAttr = new Attribute(FIELD_LAST_SYNC);
	static final Attribute rTypeCntAttr = new Attribute(FIELD_RTYPE_CNT);
	static final Attribute sCntAttr = new Attribute(FIELD_SCHEMA_CNT);
	Schema persistSchema;
	ResourceType persistType;

	public PersistStateResource(SchemaManager schemaManager, JsonNode resourceNode, IBulkIdResolver bulkIdResolver, String container)
			throws ParseException, ScimException {
		super(schemaManager, resourceNode, bulkIdResolver, container);
	}
	
	public PersistStateResource(SchemaManager schemaManager, int rCnt, int sCnt) {
		super(schemaManager);

		initSchemas();
		try {
			super.addValue(new IntegerValue(rTypeCntAttr,rCnt));
			super.addValue(new IntegerValue(sCntAttr,sCnt));
			super.addValue(new DateValue(syncDateAttr,new Date(System.currentTimeMillis())));
		} catch (SchemaException e) {
			e.printStackTrace();
		}
	}

	private void initSchemas() {
		persistSchema = new Schema(smgr);
		persistSchema.setName("Persisted Configuration State");
		persistSchema.setId(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
		persistSchema.putAttribute(syncDateAttr);
		persistSchema.putAttribute(rTypeCntAttr);
		persistSchema.putAttribute(sCntAttr);

		syncDateAttr.setPath(persistSchema.getId(),null);
		syncDateAttr.setType(Attribute.TYPE_Date);
		rTypeCntAttr.setPath(persistSchema.getId(),null);
		rTypeCntAttr.setType(Attribute.TYPE_Integer);
		sCntAttr.setPath(persistSchema.getId(),null);
		sCntAttr.setType(Attribute.TYPE_Integer);


		coreSchema = persistSchema;
		setId(CONFIG_ID);

		persistType = new ResourceType(smgr);
		persistType.setName(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
		persistType.setSchema(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);

		type = persistType;

		this.schemas = new ArrayList<>();
		this.schemas.add(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
		this.coreSchema = persistSchema;
	}
	
	public Date getLastSyncDate() {
		DateValue val = (DateValue) getValue(syncDateAttr);
		return val.getDateValue();
	}
	
	public String getLastSync() {
		DateValue val = (DateValue) getValue(syncDateAttr);
		return val.toString();
	}	
	
	public int getResTypeCnt() {
		IntegerValue val = (IntegerValue) getValue(rTypeCntAttr);
		return val.getValueArray();
	}
	
	public int getSchemaCnt() {
		IntegerValue val = (IntegerValue) getValue(sCntAttr);

		return val.getValueArray();
	}


	public void parseJson(JsonNode node, SchemaManager schemaManager) throws ParseException, ScimException {

		initSchemas();

		JsonNode item = node.get("id");
		if (item != null) 
			this.id = item.asText();
		
		item = node.get("externalId");
		if (item != null)
			this.externalId = item.asText();
		
		item = node.get(FIELD_LAST_SYNC);
		if (item != null) {
			addValue(new DateValue(syncDateAttr,item));
		}

	
		item = node.get(FIELD_RTYPE_CNT);
		if (item != null)
			addValue(new IntegerValue(rTypeCntAttr,item));

		item = node.get(FIELD_SCHEMA_CNT);
		if (item != null)
			addValue(new IntegerValue(sCntAttr,item));

		JsonNode meta = node.get("meta");
		if (meta != null)
			this.meta = new Meta(meta);
	}

	@Override
	public String toString() {
		return this.toJsonString();

	}



}
