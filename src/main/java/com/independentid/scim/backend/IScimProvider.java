/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015,2020 Phillip Hunt, All Rights Reserved                   *
 *                                                                    *
 *  Confidential and Proprietary                                      *
 *                                                                    *
 *  This unpublished source code may not be distributed outside       *
 *  “Independent Identity Org”. without express written permission of *
 *  Phillip Hunt.                                                     *
 *                                                                    *
 *  People at companies that have signed necessary non-disclosure     *
 *  agreements may only distribute to others in the company that are  *
 *  bound by the same confidentiality agreement and distribution is   *
 *  subject to the terms of such agreement.                           *
 **********************************************************************/

package com.independentid.scim.backend;

import java.io.IOException;
import java.util.Collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */
public interface IScimProvider {


	public ScimResponse create(RequestCtx ctx, final ScimResource res) throws ScimException, BackendException;
	
	/**
	 * Get performs a search and can return 1 or more results.
	 * @param ctx The SCIM processed HTTP context
	 * @return
	 * @throws ScimException
	 * @throws BackendException
	 */
	public ScimResponse get(RequestCtx ctx) throws ScimException, BackendException;
	
	/**
	 * Get Resource returns a single document and does NOT process a filter.
	 * @param ctx The SCIM request context (includes HTTP Context). Defines the search filter (if any)
	 * along with other search parameters like attributes requested
	 * @return The found ScimResource resource or NULL.
	 * @throws ScimException
	 * @throws BackendException
	 */
	public ScimResource getResource(RequestCtx ctx) throws ScimException, BackendException;
	
	public ScimResponse replace(RequestCtx ctx, final ScimResource replaceResource) throws ScimException, BackendException;
	
	public ScimResponse patch(RequestCtx ctx, final JsonPatchRequest req) throws ScimException, BackendException;
	
	public ScimResponse bulkRequest(RequestCtx ctx, final JsonNode node) throws ScimException, BackendException;
	
	public ScimResponse delete(RequestCtx ctx) throws ScimException, BackendException;
	
	public void init() throws BackendException;
	
	public boolean ready();
	
	public void shutdown();
	
	/**
	 * This method is typically called by ConfigMgr to load the system SCIM Schema definitions. This
	 * method checks the existing database for schema definitions, and if not defined, loads the schema
	 * from the default file path provided.
	 * @return A LinkedHashMap containing the Schema definitions loaded. If none are available, the map is empty.
	 */
	public Collection<Schema> loadSchemas() throws ScimException;
	
	/**
	 * This method is typically called by ConfigMgr to load the system SCIM ResourceType definitions. This
	 * method checks the existing database for ResourceType end points, and if not defined, loads the ResourceTypes
	 * from the default file path provided.
	 * @return A LinkedHashMap containing the ResourceType definitions loaded. If none are available, the map is empty.
	 */	
	public Collection<ResourceType> loadResourceTypes() throws ScimException;
	
	/**
	 * This method allows the provider to persist/update current configuration (schema defs and resource types).
	 * This method may be called on-the-fly due to a configuration modification or prior to shutdown.
	 * @param schemaCol TODO
	 * @param resTypeCol TODO
	 */
	public void syncConfig(Collection<Schema> schemaCol, Collection<ResourceType> resTypeCol) throws IOException;
}
