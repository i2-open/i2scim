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

package com.independentid.scim.protocol;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
	public final static String HEADER_IFMODSINCE = "If-Modified-Since";
	public final static String HEADER_IFRANGE = "If-Range";
	public final static String HEADER_LOCATION = "Location";

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
