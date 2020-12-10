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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.SchemaException;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

/**
 * @author pjdhunt
 * Class to track the synchronization state of system schema and resource type definitions. This provides
 * the ability to detect whether initial provisioning of default schema has been fully sync'd to database
 * as well as to detect changes in memory that may not have been perissted.
 */
public class PersistStateResource extends ScimResource {

	Date lastSyncDate = new Date(System.currentTimeMillis());
	int rTypeCnt;
	int schemaCnt;
	
	public static String FIELD_LAST_SYNC = "lastSyncDate";
	public static String FIELD_RTYPE_CNT = "rTypeCnt";
	public static String FIELD_SCHEMA_CNT = "schemaCnt";
	
	public static String RESTYPE_CONFIG = ScimParams.PATH_SERV_PROV_CFG;
	public static String CONFIG_ID = "ConfigState";
			

	public PersistStateResource(ConfigMgr cfg, JsonNode resourceNode, IBulkIdResolver bulkIdResolver, String container)
			throws SchemaException, ParseException, ScimException {
		super(cfg, resourceNode, bulkIdResolver, container);
		setId(CONFIG_ID);
		
	}
	
	public PersistStateResource(ConfigMgr cfg, int rCnt, int sCnt) {
		super();
		this.cfg = cfg;
		setId(CONFIG_ID);
		
		this.rTypeCnt = rCnt;
		this.schemaCnt = sCnt;
		// last sync date will be defaulted to current time
	}
	
	public Date getLastSyncDate() {
		return this.lastSyncDate;
	}
	
	public String getLastSync() {
		return Meta.ScimDateFormat.format(this.lastSyncDate);
	}	
	
	public int getResTypeCnt() {
		return this.rTypeCnt;
	}
	
	public int getSchemaCnt() {
		return this.schemaCnt;
	}

	@Override
	public void parseJson(ConfigMgr cfg, JsonNode node) throws SchemaException, ParseException, ScimException {
		// TODO Auto-generated method stub
		
		JsonNode item = node.get("id");
		if (item != null) 
			this.id = item.asText();
		
		item = node.get("externalId");
		if (item != null)
			this.externalId = item.asText();
		
		item = node.get(FIELD_LAST_SYNC);
		if (item != null)
			this.lastSyncDate = Meta.ScimDateFormat.parse(item.asText());
	
		item = node.get(FIELD_RTYPE_CNT);
		if (item != null)
			this.rTypeCnt = item.asInt();
		
		item = node.get(FIELD_SCHEMA_CNT);
		if (item != null)
			this.schemaCnt = item.asInt();
		
		JsonNode meta = node.get("meta");
		if (meta != null)
			this.meta = new Meta(meta);
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException, ScimException {
		gen.writeStartObject();


		// Write out the id and externalId
		if (this.id != null)
			gen.writeStringField("id", this.id);

		if (this.externalId != null)
			gen.writeStringField("externalId", this.externalId);

		

		gen.writeStringField(FIELD_LAST_SYNC, getLastSync());
		
		gen.writeNumberField(FIELD_RTYPE_CNT, cfg.getResourceTypeCnt());
		
		gen.writeNumberField(FIELD_SCHEMA_CNT, cfg.getSchemaCnt());
		
		// Write out the meta information
		// Meta will not be used for hash calculations.
		if (this.meta != null && !forHash) {
			gen.writeFieldName("meta");
			this.meta.serialize(gen, ctx, forHash);
		}

		// Write out the end of object for the resource
		gen.writeEndObject();
	}

	@Override
	public String toString() {
		try {
			return this.toJsonString();
		} catch (ScimException e) {
			e.printStackTrace();
		}
		return null;
	}
}
