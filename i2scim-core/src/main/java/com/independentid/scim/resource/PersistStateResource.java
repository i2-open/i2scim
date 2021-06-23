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
		mainSchema = smgr.getSchemaById(ScimParams.SCHEMA_SCHEMA_PERSISTEDSTATE);
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
