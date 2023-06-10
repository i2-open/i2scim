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

package com.independentid.scim.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.DuplicateTxnException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.PersistStateResource;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.TransactionRecord;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;

/**
 * @author pjdhunt This interface defines an extension point which may be used to define a new data source for SCIM
 * server.
 */
public interface IScimProvider {

    String getGeneratorClass();

    ScimResponse create(RequestCtx ctx, final ScimResource res) throws ScimException, BackendException;

    /**
     * Get performs a search and can return 1 or more results.
     * @param ctx The SCIM processed HTTP context
     * @return A {@link ScimResponse} containing the results of the get request
     * @throws ScimException    when SCIM protocol level error is detected
     * @throws BackendException when the storage handler returns an error not related to SCIM
     */
    ScimResponse get(RequestCtx ctx) throws ScimException, BackendException;

    /**
     * Get Resource returns a single document and does NOT process a filter.
     * @param ctx The SCIM request context (includes HTTP Context). Defines the search filter (if any) along with other
     *            search parameters like attributes requested
     * @return The found ScimResource resource or NULL.
     * @throws ScimException    when SCIM protocol level error is detected
     * @throws BackendException when the storage handler returns an error not related to SCIM
     */
    ScimResource getResource(RequestCtx ctx) throws ScimException, BackendException;

    /**
     * Performs a SCIM PUT request as per RFC7644, Section 3.5.1
     * @param ctx             The RequestCtx containing the path, filter, attributes and other request modifiers
     * @param replaceResource A {@link ScimResource} object containing the claims to replace the existing resource.
     * @return ScimResponse containing the final representation of the replaced resource.
     * @throws ScimException    when SCIM protocol level error is detected
     * @throws BackendException when the storage handler returns an error not related to SCIM
     */
    ScimResponse put(RequestCtx ctx, final ScimResource replaceResource) throws ScimException, BackendException;

    /**
     * Performs a SCIM PATCH request as per RFC7644, Section 3.5.2 based on RFC6902 JSON Patch Specification
     * @param ctx The RequestCtx containing the path, filter, attributes and other request modifiers
     * @param req A {@link JsonPatchRequest} object containing the claims to replace the existing resource.
     * @return A {@link ScimResponse} containing the final representation of the replaced resource.
     * @throws ScimException    when SCIM protocol level error is detected
     * @throws BackendException when the storage handler returns an error not related to SCIM
     */
    ScimResponse patch(RequestCtx ctx, final JsonPatchRequest req) throws ScimException, BackendException;

    /**
     * Performs a SCIM Bulk Operation request as per RFC7644, Section 3.7
     * @param ctx  The RequestCtx containing the path, filter, attributes and other request modifiers
     * @param node A {@link JsonNode} object containing SCIM formated bulk request
     * @return A {@link ScimResponse} containing the results of the bulk request per Sec 3.7.3
     * @throws ScimException    when SCIM protocol level error is detected. May throw circular reference and other
     *                          identifier errors.
     * @throws BackendException when the storage handler returns an error not related to SCIM
     */
    ScimResponse bulkRequest(RequestCtx ctx, final JsonNode node) throws ScimException, BackendException;

    /**
     * Performs a SCIM DELETE resource request as per RFC7644, Section 3.6
     * @param ctx The RequestCtx containing the path of the resource to be removed.
     * @return A {@link ScimResponse} containing confirmation of success per Sec 3.6
     * @throws ScimException    when SCIM protocol level error is detected
     * @throws BackendException when the storage handler returns an error not related to SCIM
     */
    ScimResponse delete(RequestCtx ctx) throws ScimException, BackendException;


    /**
     * Called to request the provider initialize itself and complete startup. Upon successful startup, the ready()
     * method should return true. Providers should avoid doing substantial work during construction and should do all
     * work during init.
     * @throws BackendException May be thrown when the provider cannot be initialized or connection established. This
     *                          will cause the server to fail startup.
     */
    void init() throws BackendException;

    /**
     * @return Returns true if the provider is fully initialized and ready.
     */
    boolean ready();

    /**
     * Called when the server is in the process of shutdown to enable a graceful shut down. Control should not be
     * returned until shutdown is safe.
     */
    void shutdown();

    /**
     * Used to return the current configuration state of the backend provider. In particular it is used for
     * co-ordinating persisted schema and resource types and co-ordinating synchronization. May also be used to test
     * database live-ness.
     * @return The PersistStateResoruce that holds the current schema sync state of the server.
     * @throws ScimException  occurs when a general scim server occurs
     * @throws IOException    occurs when attempting to read/write data
     * @throws ParseException occurs when invalid JSON data is encountered
     */
    PersistStateResource getConfigState() throws ScimException, IOException, ParseException;

    /**
     * This method is typically called by ConfigMgr to load the system SCIM Schema definitions. This method checks the
     * existing database for schema definitions, and if not defined, loads the schema from the default file path
     * provided.
     * @return A LinkedHashMap containing the Schema definitions loaded. If none are available, the map is empty.
     * @throws ScimException Occurs when an invalid state or configuration occurs
     */
    Collection<Schema> loadSchemas() throws ScimException;

    /**
     * This method is typically called by ConfigMgr to load the system SCIM ResourceType definitions. This method checks
     * the existing database for ResourceType end points, and if not defined, loads the ResourceTypes from the default
     * file path provided.
     * @return A LinkedHashMap containing the ResourceType definitions loaded. If none are available, the map is empty.
     * @throws ScimException Occurs when an invalid state or configuration occurs
     */
    Collection<ResourceType> loadResourceTypes() throws ScimException;

    /**
     * This method allows the provider to persist/update current configuration (schema defs and resource types). This
     * method may be called on-the-fly due to a configuration modification or prior to shutdown.
     * @param schemaCol  A collection of {@link Schema}s to be synchronized in the provider
     * @param resTypeCol A collection of {@link ResourceType}s to be synchronized in the provider
     * @throws IOException May occur when attempting to read/write data from the underlying provider
     */
    void syncConfig(Collection<Schema> schemaCol, Collection<ResourceType> resTypeCol) throws IOException;

    /**
     * Used to obtain information about the transaction previously committed in the provider. The value in
     * Meta.revisions can be used as the query term.
     * @param transid The transaction id (e.g. from Meta.revisions.value or RequestCtx.getTranID().
     * @return The TransactionRecord in the form of {@link ScimResource} containing information about the transaction (a
     * ScimResource).
     * @throws BackendException when an error occurs in the underlying provider such as a mapping error due to
     *                          corruption
     */
    ScimResource getTransactionRecord(String transid) throws BackendException;

    /**
     * Used by the replication event processing system to detect if the cluster has already processed a transaction in
     * the case of 1 or more cluster members receiving the same event.
     * @param transid The transaction UUID string value to be checked (from {@link RequestCtx#getTranId()}).
     * @return true if present in the transaction store of the provider.
     * @throws BackendException when an error occurs in the underlying provider such as a mapping error due to
     *                          corruption
     */
    boolean isTransactionPresent(String transid) throws BackendException;

    /**
     * This method is typically called by a CreateOp, DeleteOp, Put or Patch Op, after any write transaction.
     * @param record A {@link TransactionRecord} containing information about the update.
     * @throws DuplicateTxnException if the transactionId already exists in the provider, an exception is thrown.
     */
    void storeTransactionRecord(TransactionRecord record) throws DuplicateTxnException;


}
