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

package com.independentid.scim.protocol;

public class ScimParams {

	public final static String QUERY_attributes = "attributes";
	public final static String QUERY_excludedattributes = "excludedAttributes";
	public final static String QUERY_filter = "filter";
	public final static String QUERY_sortby = "sortBy";
	public final static String QUERY_sortorder = "sortOrder";
	public final static String QUERY_startindex = "startIndex";
	public final static String QUERY_count = "count";
	
	public final static String HEADER_ETAG = "ETag";
	public final static String HEADER_LASTMOD = "Last-Modified";
	public final static String HEADER_IFMATCH = "If-Match";
	public final static String HEADER_IFNONEMATCH = "If-None-Match";
	public final static String HEADER_IFUNMODSINCE = "If-Unmodified-Since";
	public final static String HEADER_IFRANGE = "If-Range";

	public final static String SCHEMA_API_ListResponse = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
	public final static String SCHEMA_API_SearchRequest = "urn:ietf:params:scim:api:messages:2.0:SearchRequest";
	public final static String SCHEMA_API_PatchOp = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
	public final static String SCHEMA_API_BulkRequest = "urn:ietf:params:scim:api:messages:2.0:BulkOps";
	public final static String SCHEMA_API_BulkResponse = "urn:ietf:params:scim:api:messages:2.0:BulkResponse";
	public final static String SCHEMA_API_Error = "urn:ietf:params:scim:api:messages:2.0:Error";

	public final static String SCHEMA_SCHEMA_User = "urn:ietf:params:scim:schemas:core:2.0:User";
	public final static String SCHEMA_SCHEMA_Ent_User = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
	public final static String SCHEMA_SCHEMA_Group = "urn:ietf:params:scim:schemas:core:2.0:Group";
	public final static String SCHEMA_SCHEMA_ServiceProviderConfig = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";
	public final static String SCHEMA_SCHEMA_ResourceType = "urn:ietf:params:scim:schemas:core:2.0:ResourceType";
	public final static String SCHEMA_SCHEMA_Schema = "urn:ietf:params:scim:schemas:core:2.0:Schema";
	public final static String SCHEMA_SCHEMA_Common = "urn:ietf:params:scim:schemas:core:2.0:Common";

	public final static String SCHEMA_SCHEMA_PERSISTEDSTATE = "ConfigState";
	public final static String SCHEMA_SCHEMA_SYNCREC = "SyncRec";

	public final static String PATH_TYPE_ME = "Me";

	public final static String PATH_TYPE_SCHEMAS = "Schemas";
	public final static String TYPE_SCHEMA = "Schema";
	
	public final static String PATH_TYPE_RESOURCETYPE = "ResourceTypes";
	public final static String TYPE_RESOURCETYPE = "ResourceType";
	
	public final static String PATH_SERV_PROV_CFG = "ServiceProviderConfig";
	public final static String TYPE_SERV_PROV_CFG = "ServiceProviderConfig";
	
	public final static String PATH_GLOBAL_SEARCH = "/.search";
	public final static String PATH_SUBSEARCH = ".search";
	public final static String PATH_BULK = "/Bulk";
	
	public final static String SCIM_MIME_TYPE = "application/scim+json";

	public static final String ATTR_SCHEMAS = "schemas";
	public static final String ATTR_ID = "id";
	public static final String ATTR_META = "meta";
	public static final String ATTR_EXTID = "externalId";
	public static final String ATTR_PATCH_OPS = "Operations";


}
