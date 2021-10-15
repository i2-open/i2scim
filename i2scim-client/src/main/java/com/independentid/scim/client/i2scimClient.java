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

package com.independentid.scim.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.*;
import com.independentid.scim.protocol.*;
import com.independentid.scim.resource.ComplexValue;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.StringTokenizer;

/**
 * i2scimClient is a general utility class using the Apache HTTP Client to call a SCIM Server. When instantiated, a
 * {@link CloseableHttpClient} is created and a test call is made to the SCIM "/ServiceProviderConfig" endpoint to
 * discover remote capabilities. Where possible, the local client will throw an error if a feature is not available as
 * indicated by the ServiceProviderConfig.
 */
public class i2scimClient {
    private final static Logger logger = LoggerFactory.getLogger(i2scimClient.class);

    public final static SimpleDateFormat httpDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

    final URL serverRoot;
    final SchemaManager schemaManager;
    final CloseableHttpClient client;

    String authorization;

    boolean schemaMode = false;

    boolean patchSupported = false;
    boolean filterSupported = true;
    int maxResults = -1;
    boolean changePasswordSupport = true;
    boolean sortSupported = false;
    boolean etagSupport = true;
    boolean ignoreSPC = false;
    boolean hasPatch = true;

    /**
     * This constructor establishes a SCIM client using an authorization header value (e.g. Bearer token). The client
     * will contact the server and auto-load the remote schema for use locally. The client will also query the SCIM
     * /ServiceProviderConfig endpoint. THe client then records the server's capabilities for enforcement later if
     * desired. This can be disabled with {@link i2scimClient#setIgnoreServiceProviderConfig(boolean)}.
     * @param serverRootUrl The URL of the SCIM server root to contact.
     * @param authorization The authorization header value to use
     * @throws ScimException      when an SCIM protocol error occurs
     * @throws IOException        due to connection issues
     * @throws URISyntaxException due to an invalid serverRootUrl
     */
    public i2scimClient(String serverRootUrl, String authorization)
            throws ScimException, IOException, URISyntaxException {
        this(serverRootUrl, authorization, null, null);
    }

    /**
     * This constructor establishes a SCIM client using Basic Auth. The client will contact the server and auto-load the
     * remote schema for use locally. The client will also query the SCIM /ServiceProviderConfig endpoint. THe client
     * then records the server's capabilities for enforcement later if desired. This can be disabled with {@link
     * i2scimClient#setIgnoreServiceProviderConfig(boolean)}.
     * @param serverRootUrl The URL of the SCIM server root to contact.
     * @param cred          A {@link UsernamePasswordCredentials} credential containing a username and password
     * @throws ScimException      when an SCIM protocol error occurs
     * @throws IOException        due to connection issues
     * @throws URISyntaxException due to an invalid serverRootUrl
     */
    public i2scimClient(String serverRootUrl, UsernamePasswordCredentials cred)
            throws ScimException, IOException, URISyntaxException {
        this(serverRootUrl,
                encodeAuthorization(cred),
                null,
                null);

    }

    /**
     * This constructor is used only when wanting to use i2ScimClient for access to builders (aka Schema only mode).
     * @param schemaManager A {@link SchemaManager} instance
     */
    public i2scimClient(SchemaManager schemaManager) {
        this.schemaManager = schemaManager;
        this.client = null;
        this.serverRoot = null;
        this.authorization = null;
        this.schemaMode = true;
    }

    /**
     * @return True if client is configured to run in Schema only mode and not configured for Http requests.
     */
    public boolean isSchemaMode() {
        return this.schemaMode;
    }

    /**
     * Changes the security authorization used by the client
     * @param authz A HTTP Authorization header value (e.g. a bearer token)
     */
    public void setAuthorization(String authz) {
        this.authorization = authz;
    }

    /**
     * Changes the security authorization used by the client using Basic Authentication
     * @param cred A {@link UsernamePasswordCredentials} object containing a username and password
     */
    public void setAuthorization(UsernamePasswordCredentials cred) {
        this.authorization = encodeAuthorization(cred);
    }

    private static String encodeAuthorization(UsernamePasswordCredentials cred) {
        return "Basic "
                + Base64.getEncoder().encodeToString((cred.getUserName() + ":" + cred.getPassword()).getBytes(StandardCharsets.UTF_8));

    }

    /**
     * Instantiates an i2scim client context. When instantiated, the client will load schema locally and will check
     * connectivity with the service provider by querying the SCIM /ServiceProviderConfig endpoint. THe client then
     * records the server's capabilities for enforcement later if desired. This can be disabled with {@link
     * i2scimClient#setIgnoreServiceProviderConfig(boolean)}.
     * @param serverRootUrl     A URL representing the root of the server to be contacted
     * @param authorization     A String constituting the authorization header value for requests
     * @param schemaPath        A local file path for a SCIM schema JSON file (what schemas are defined). If NULL,
     *                          schema and resource types will be loaded from service provider.
     * @param resourceTypesPath A local file path for a SCIM Resource Types JSON file or NULL.
     * @throws ScimException      when an SCIM protocol error occurs
     * @throws IOException        due to connection issues
     * @throws URISyntaxException due to an invalid serverRootUrl
     */
    public i2scimClient(String serverRootUrl, String authorization, String schemaPath, String resourceTypesPath)
            throws ScimException, IOException, URISyntaxException {

        serverRoot = new URL(serverRootUrl);
        this.authorization = authorization;
        client = HttpClients.createDefault();

        URI spcUrl = prepareRequestUri("/ServiceProviderConfig", null, null);
        HttpGet get = new HttpGet(spcUrl);
        logger.debug("Connecting to: " + spcUrl.toString());

        prepareHeaders(get, null);
        CloseableHttpResponse resp = client.execute(get);

        if (resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
            switch (resp.getStatusLine().getStatusCode()) {
                case HttpStatus.SC_UNAUTHORIZED:
                    throw new UnauthorizedException("Received unauthorized from: " + spcUrl);
                case HttpStatus.SC_FORBIDDEN:
                    throw new ForbiddenException("Received forbidden exception from: " + spcUrl);
                case HttpStatus.SC_NOT_FOUND:
                    throw new NotFoundException("Received Not found exception loading service provider config from: "
                            + spcUrl);
                default:
                    throw new ScimException("Received unexpected error status: "
                            + resp.getStatusLine().getStatusCode());
            }

        loadServiceProviderConfig(resp.getEntity().getContent());
        resp.close();

        if (schemaPath != null) {
            logger.debug("Loading schema from local file.");
            this.schemaManager = new SchemaManager(schemaPath, resourceTypesPath);

        } else {
            logger.debug("Loading schema from SCIM service provider.");
            logger.debug("\t...Downloading ResourceTypes");
            URI rUri = prepareRequestUri("/" + ScimParams.PATH_TYPE_RESOURCETYPE, null, null);
            get = new HttpGet(rUri);
            prepareHeaders(get, null);
            resp = client.execute(get);

            // Note: because we need both input streams to instantiate SchemaManager, we need to copy one stream in order to
            // re-use the existing client connection.
            if (resp.getStatusLine().getStatusCode() != ScimResponse.ST_OK)
                throw new ScimException("Received unexpected response to /ResourceTypes endpoint request: "
                        + resp.getStatusLine().getReasonPhrase());

            InputStream typeStream = new ByteArrayInputStream(EntityUtils.toString(resp.getEntity()).getBytes(StandardCharsets.UTF_8));
            resp.close();

            logger.debug("\t...Downloading Schemas");
            URI sUri = prepareRequestUri("/" + ScimParams.PATH_TYPE_SCHEMAS, null, null);
            get = new HttpGet(sUri);
            prepareHeaders(get, null);
            resp = client.execute(get);

            if (resp.getStatusLine().getStatusCode() != ScimResponse.ST_OK)
                throw new ScimException("Received unexpected response to /Schemas endpoint request: "
                        + resp.getStatusLine().getReasonPhrase());


            InputStream schemaStream = new ByteArrayInputStream(EntityUtils.toString(resp.getEntity()).getBytes(StandardCharsets.UTF_8));
            JsonNode node = JsonUtil.getJsonTree(schemaStream);
            //System.out.println(node.toPrettyString());
            schemaStream = new ByteArrayInputStream(node.toString().getBytes(StandardCharsets.UTF_8));
            this.schemaManager = new SchemaManager(schemaStream, typeStream);

            resp.close();
        }

    }

    /**
     * Normally the i2scimClient will interrogate the ServiceProviderConfig feature support of remote server and issue
     * exceptions when requests include a feature not supported by the service provider. This method can be used to
     * override enforcement and send requests anyway. This may be used for testing or when ServiceProviderConfig
     * endpoint cannot be processed properly.
     * @param ignore Set to true if requests should always be tried. Default: false)
     */
    public void setIgnoreServiceProviderConfig(boolean ignore) {
        this.ignoreSPC = ignore;
    }

    /**
     * @return true if the connected SCIM endpoint supports search filters.
     */
    public boolean hasSearchSupport() {
        return this.filterSupported;
    }

    /**
     * @return true if the connected SCIM endpoint does NOT support pre-conditions and etags
     */
    public boolean hasNoEtagSupport() {
        return !this.etagSupport;
    }

    /**
     * @return true if the connected SCIM server supports HTTP PATCH
     */
    public boolean hasPatchSupport() {
        return this.patchSupported;
    }

    /**
     * @return true if the connected SCIM server supports sorting results.
     */
    public boolean hasSortSupport() {
        return this.sortSupported;
    }

    /**
     * @return true if the connected SCIM server supports change password
     */
    public boolean hasChangePasswordSupport() {
        return this.changePasswordSupport;
    }

    private void loadServiceProviderConfig(InputStream stream) throws IOException {
        if (stream == null)
            return; // nothing received

        JsonNode node = JsonUtil.getJsonTree(stream);
        JsonNode itemNode = node.get("patch");
        if (itemNode != null)
            patchSupported = itemNode.get("supported").asBoolean(true);

        itemNode = node.get("filter");
        if (itemNode != null) {
            filterSupported = itemNode.get("supported").asBoolean(true);
            maxResults = itemNode.get("maxResults").asInt(-1);
        }

        itemNode = node.get("patch");
        if (itemNode != null)
            patchSupported = itemNode.get("supported").asBoolean(true);

        itemNode = node.get("changePassword");
        if (itemNode != null)
            changePasswordSupport = itemNode.get("supported").asBoolean(true);

        itemNode = node.get("sort");
        if (itemNode != null)
            sortSupported = itemNode.get("supported").asBoolean(true);

        itemNode = node.get("etag");
        if (itemNode != null)
            etagSupport = itemNode.get("supported").asBoolean(true);

        stream.close();
    }

    /**
     * Returns a new resource builder for the specified path. The builder is used to generate a new ScimResource
     * object.
     * @param container The SCIM container (top level path element without slashes). E.g. Users
     * @return ScimResourceBuilder for the specified container/type
     */
    public ResourceBuilder getResourceBuilder(String container) {
        return new ResourceBuilder(this, container);
    }

    /**
     * Returns a builder based on {@link JsonNode} pointer to a SCIM Resource.
     * @param json A {@link JsonNode} containing a JSON object representation of a SCIM Resource
     * @return A {@link ResourceBuilder}.
     * @throws ScimException  Occurs when parsing invalid SCIM JSON
     * @throws ParseException occurs when a JSON parsing error occurs
     */
    public ResourceBuilder getResourceBuilder(JsonNode json) throws ScimException, ParseException {
        return new ResourceBuilder(this, json);
    }

    /**
     * Returns a builder based on an existing {@link ScimResource}.
     * @param resource A {@link ScimResource} to initialize the builder
     * @return A {@link ResourceBuilder}.
     * @throws ScimException  Occurs when coping SCIM resource
     * @throws ParseException occurs when a JSON parsing error occurs
     */
    public ResourceBuilder getResourceBuilder(final ScimResource resource) throws ScimException, ParseException {
        return new ResourceBuilder(this, resource);
    }

    /**
     * Reads and parses a {@link File} containing a JSON formatted representation of a ScimResource
     * @param file A {@link File} containing a JSON object representation of a SCIM Resource
     * @return A {@link ResourceBuilder}.
     * @throws ScimException  Occurs when parsing invalid SCIM JSON
     * @throws ParseException occurs when a JSON parsing error occurs
     * @throws IOException    occurs when attempting to read the file.
     */
    public ResourceBuilder getResourceBuilder(File file) throws ScimException, IOException, ParseException {
        if (file == null)
            throw new IOException("File specified is NULL.");
        if (!file.exists())
            throw new IOException("File (" + file.getPath() + ") does not exist.");
        InputStream stream = new FileInputStream(file);
        return new ResourceBuilder(this, stream);
    }

    /**
     * Reads and parses a {@link InputStream} containing a JSON formatted representation of a ScimResource
     * @param stream A {@link InputStream} containing a JSON object representation of a SCIM Resource
     * @return A {@link ResourceBuilder}.
     * @throws ScimException  Occurs when parsing invalid SCIM JSON
     * @throws ParseException occurs when a JSON parsing error occurs
     * @throws IOException    occurs when attempting to read the stream.
     */
    public ResourceBuilder getResourceBuilder(InputStream stream) throws ScimException, IOException, ParseException {
        return new ResourceBuilder(this, stream);
    }

    /**
     * Creates a builder for ComplexValues (values that contain more than one attribute) using the complex attribute
     * name.
     * @param attributeName the name of the Complex attribute to be created. E.g. (User:name)
     * @return A {@link ComplexValue.Builder} object
     * @throws SchemaException is thrown if the attribute name is not found or is not a Complex Attribute.
     */
    public ComplexValue.Builder getComplexValueBuilder(String attributeName) throws SchemaException {
        Attribute attr = schemaManager.findAttribute(attributeName, null);
        if (attr != null && attr.getType().equalsIgnoreCase(Attribute.TYPE_Complex))
            return ComplexValue.getBuilder(attr);
        if (attr == null)
            throw new SchemaException("Attribute [" + attributeName + "] is not defined.");
        throw new SchemaException("Attribute [" + attr.getSchema() + ":" + attributeName + "] is not a complex attribute. It is: " + attr.getType());
    }

    /**
     * Creates a builder for MultiValue objects (values that contain more than one Value) using the attribute name.
     * @param attributeName the name of the Multi-value attribute to be created. E.g. (Group:member). Note: if using
     *                      {@link ScimResource#addValue(Value)} method, you may add a row value (e.g. ComplexValue,
     *                      StringValue) directly rather than wrapping in a MultiValue container.
     * @return A {@link ComplexValue.Builder} object
     * @throws SchemaException is thrown if the attribute name is not found or is not a multi-valued attribute.
     */
    public MultiValue.Builder getMultiValueBuilder(String attributeName) throws SchemaException {
        Attribute attr = schemaManager.findAttribute(attributeName, null);
        if (attr != null && attr.isMultiValued())
            return MultiValue.getBuilder(attr);
        if (attr == null)
            throw new SchemaException("Attribute " + attributeName + " is not defined.");
        throw new SchemaException("Attribute " + attributeName + " is not a multi-valued attribute. It is: " + attr.getType());
    }

    /**
     * Returns a SCIM Patch request builder. This method is used to construct a JsonPatchRequest used in conjunction
     * with a SCIM PATCH operation.
     * @return A {@link JsonPatchBuilder} object enabling construction of a SCIM Patch request using builder pattern
     */
    public JsonPatchBuilder getPatchRequestBuilder() {
        return JsonPatchRequest.builder();
    }

    /**
     * Validates a username password combination against a SCIM service provider by converting to a GET request with
     * filter.  This does not change the underlying SCIM client credential used for requests. The ability to validate
     * this request (a search against password) must be configured in the service provider access control.
     * @param cred The username password combination to be validated
     * @return true if authenticated, false if no match.
     * @throws ScimException      when an SCIM protocol error occurs. May throw unauthorized if the underlying
     *                            authorization does not have the right to perform the query.
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public boolean authenticateUser(UsernamePasswordCredentials cred) throws ScimException, URISyntaxException, IOException, ParseException {
        String filter = "userName eq \"" + cred.getUserName() + "\" and password eq \"" + cred.getPassword() + "\"";
        ScimReqParams params = new ScimReqParams();
        params.setAttributesRequested("id,username");
        i2scimResponse sresp = searchPost("/Users", filter, params);
        return !sresp.hasError() && sresp.hasNext();
    }

    /**
     * Retrieves the specified resource at the given path. The path must specify a single return object. Filters are NOT
     * supported on this method per RFC7644 indicating any request with a filter must return a List.
     * @param path   The path to a SCIM resource that returns a single object
     * @param params Scim request params {@link ScimReqParams}  (attributes requested, etc)
     * @return A {@link i2scimResponse} containing the object or objects or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public i2scimResponse get(String path, ScimReqParams params)
            throws ScimException, URISyntaxException, IOException, ParseException {
        validateRetrieveParams(params, null);
        URI reqUri = prepareRequestUri(path, null, params);
        HttpGet get = new HttpGet(reqUri);
        prepareHeaders(get, params);
        CloseableHttpResponse resp = client.execute(get);
        return new i2scimResponse(this, resp);
    }

    /**
     * Retrieves the specified resource at the given path and returns a builder. The path must specify a single return
     * object. Filters are NOT supported on this method per RFC7644 indicating any request with a filter must return a
     * List.
     * @param path   The path to a SCIM resource that returns a single object
     * @param params Scim request params {@link ScimReqParams}  (attributes requested, etc)
     * @return a {@link ResourceBuilder} object
     * @throws ScimException      when an SCIM protocol error occurs such as NotFoundException
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public ResourceBuilder retrieveAndBuild(String path, ScimReqParams params) throws ScimException, URISyntaxException, IOException, ParseException {
        i2scimResponse resp = get(path, params);
        if (resp.hasNext()) {
            ScimResource res = resp.next();
            return new ResourceBuilder(this, res);
        }
        return null;
    }

    /**
     * searchGet performs a search to return one or more objects in a SCIM ListResponse. This method invokes the HTTP
     * GET version of the SCIM Protocol See <a href="https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2">RFC7644
     * Sec 3.4.2</a>. Caution: avoid passing sensitive information in filters as they may appear in access logs.
     * @param path   A path within the SCIM server to search. It may be the root "/", a containers (e.g. "/Users") or a
     *               specific resource.
     * @param filter A SCIM filter see <a href="https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2.2">RFC7644
     *               Sec 3.4.2.2</a>
     * @param params Scim request params {@link ScimReqParams}  (attributes requested, etc)
     * @return A {@link i2scimResponse} containing the object or objects or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public i2scimResponse searchGet(String path, String filter, ScimReqParams params)
            throws ScimException, URISyntaxException, IOException, ParseException {
        // Check that the request is valid by attempting to parse path and filter using RequestCtx
        new RequestCtx(path, null, filter, schemaManager);

        validateRetrieveParams(params, filter);

        URI reqUri = prepareRequestUri(path, filter, params);
        HttpGet get = new HttpGet(reqUri);
        prepareHeaders(get, params);
        CloseableHttpResponse resp = client.execute(get);
        return new i2scimResponse(this, resp);
    }

    /**
     * searchPost performs a search to return one or more objects in a SCIM ListResponse. This method invokes the HTTP
     * POST query version of the SCIM Protocol <a href="https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.3">RFC7644
     * Sec 3.4.3</a>. This method may be used to enable passing confidential information in filters.
     * @param path   A path within the SCIM server to search. It may be the root "/", a containers (e.g. "/Users") or a
     *               specific resource.
     * @param filter A SCIM filter <a href="https://datatracker.ietf.org/doc/html/rfc7644#section-3.4.2.2">RFC7644 Sec
     *               3.4.2.2</a>
     * @param params Scim request params {@link ScimReqParams}  (attributes requested, etc)
     * @return A {@link i2scimResponse} containing the object or objects or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public i2scimResponse searchPost(String path, String filter, ScimReqParams params)
            throws ScimException, URISyntaxException, IOException, ParseException {
        validateRetrieveParams(params, filter);

        //The following line is executed to validate the request filter
        new RequestCtx(path, null, filter, schemaManager);

        if (!path.endsWith(ScimParams.PATH_SUBSEARCH))
            if (path.equals("/") || path.equals(""))
                path = ScimParams.PATH_GLOBAL_SEARCH;
            else if (path.endsWith("/"))
                path = path + ScimParams.PATH_SUBSEARCH;
            else
                path = path + ScimParams.PATH_GLOBAL_SEARCH;

        StringWriter writer = new StringWriter();
        JsonGenerator gen = JsonUtil.getGenerator(writer, true);

        gen.writeStartObject();
        gen.writeArrayFieldStart("schemas");
        gen.writeString(ScimParams.SCHEMA_API_SearchRequest);
        gen.writeEndArray();

        gen.writeStringField("filter", filter);

        if (params != null) {
            if (params.attrs != null) {
                StringTokenizer tkn = new StringTokenizer(params.attrs, ", ");
                gen.writeArrayFieldStart("attributes");
                while (tkn.hasMoreTokens())
                    gen.writeString(tkn.nextToken());
                gen.writeEndArray();
            }
            if (params.exclAttrs != null) {
                StringTokenizer tkn = new StringTokenizer(params.exclAttrs, ", ");
                gen.writeArrayFieldStart("excludedAttributes");
                while (tkn.hasMoreTokens())
                    gen.writeString(tkn.nextToken());
                gen.writeEndArray();
            }

            if (params.sortBy != null)
                gen.writeStringField("sortBy", params.sortBy);

            if (params.sortOrder != null)
                gen.writeStringField("sortOrder", params.sortOrder ? "ascending" : "descending");

            if (params.startIndex > -1)
                gen.writeNumberField("startIndex", params.startIndex);

            if (params.count > -1)
                gen.writeNumberField("count", params.count);
        }
        gen.writeEndObject();
        gen.close();
        writer.close();

        // Request params now encoded in JSON payload
        StringEntity sEntity = new StringEntity(writer.toString(), ContentType.create(ScimParams.SCIM_MIME_TYPE));

        URI reqUri = prepareRequestUri(path, null, null); // request params not encoded in URI
        HttpPost post = new HttpPost(reqUri);
        post.setEntity(sEntity);
        prepareHeaders(post, params);
        CloseableHttpResponse resp = client.execute(post);
        return new i2scimResponse(this, resp);
    }

    /**
     * This method creates a SCIM Resource using the HTTP POST command. The URI for the request is constructed from the
     * {@link ScimResource}.
     * @param res    The {@link ScimResource} to be created on the service provider. If the resource includes an "id"
     *               the service provider will usually ignore it.
     * @param params Optional SCIM request modifiers (e.g. attributes)
     * @return A {@link i2scimResponse} containing the object response or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public i2scimResponse create(final ScimResource res, ScimReqParams params)
            throws ScimException, URISyntaxException, IOException, ParseException {
        if (isSchemaMode())
            throw new IOException("i2scimClient currently configured for builder operations only.");
        validateModifyParams(params);
        String name = res.getResourceType();
        ResourceType type = schemaManager.getResourceTypeByName(name);

        URI reqUri = prepareRequestUri(type.getEndpoint().getPath(), null, params);
        HttpPost post = new HttpPost(reqUri);
        prepareHeaders(post, params);

        StringEntity body = new StringEntity(res.toJsonString(), ContentType.create(ScimParams.SCIM_MIME_TYPE));
        body.setChunked(false);
        post.setEntity(body);

        CloseableHttpResponse resp = client.execute(post);
        return new i2scimResponse(this, resp);

    }

    /**
     * This method performs a SCIM PUT request. The URI for the request is constructed from the {@link ScimResource}.
     * @param res    The {@link ScimResource} to be replaced on the service provider
     * @param params Optional SCIM request modifiers (e.g. attributes)
     * @return A {@link i2scimResponse} containing the object response or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public i2scimResponse put(final ScimResource res, ScimReqParams params)
            throws ScimException, URISyntaxException, IOException, ParseException {
        if (isSchemaMode())
            throw new IOException("i2scimClient currently configured for builder operations only.");
        validateModifyParams(params);
        String name = res.getResourceType();
        ResourceType type = schemaManager.getResourceTypeByName(name);

        String path = type.getEndpoint() + "/" + res.getId();

        URI reqUri = prepareRequestUri(path, null, params);
        HttpPut put = new HttpPut(reqUri);
        prepareHeaders(put, params);

        StringEntity body = new StringEntity(res.toJsonString(), ContentType.create(ScimParams.SCIM_MIME_TYPE));
        put.setEntity(body);
        CloseableHttpResponse resp = client.execute(put);
        return new i2scimResponse(this, resp);

    }

    protected void validateRetrieveParams(ScimReqParams params, String filter) throws InvalidSyntaxException, NotImplementedException {
        if (params != null && (params.head_ifMatch != null || params.head_ifUnModSince != null))
            throw new InvalidSyntaxException("Preconditions If-Match & If-Unmodified-Since not appropriate on retrieve requests.");

        if (ignoreSPC)
            return;
        if (params != null) {
            if (hasNoEtagSupport() && (params.head_ifNoMatch != null || params.head_ifModSince != null))
                throw new NotImplementedException("Remote server indicates Etags (preconditions) not supported.");

            if (!hasSearchSupport() && filter != null)
                throw new NotImplementedException("Remote server indicates search filters not supported.");

            if (!hasSortSupport() && params.sortOrder != null)
                throw new NotImplementedException("Remote server indicates sort not supported.");
        }
    }

    protected void validateModifyParams(ScimReqParams params) throws InvalidSyntaxException, NotImplementedException {
        if (params == null)
            return;

        if (params.head_ifNoMatch != null || params.head_ifModSince != null)
            throw new InvalidSyntaxException("Preconditions If-None-Match & If-Modified-Since not appropriate on modify requests.");

        if (ignoreSPC)
            return;

        if (hasNoEtagSupport() && (params.head_ifMatch != null || params.head_ifUnModSince != null))
            throw new NotImplementedException("Remote server indicates Etags (preconditions) not supported.");
    }

    /**
     * Performs a SCIM PATCH request against a specified resource as per <a href="https://datatracker.ietf.org/doc/html/rfc7644#section-3.5.2">RFC7644
     * Sec 3.5.2</a>
     * @param path   The relative path of the object to be modified
     * @param req    A {@link JsonPatchRequest} containing one or more {@link JsonPatchOp} operations.
     * @param params Optional SCIM request parameters or NULL
     * @return A {@link i2scimResponse} containing the object response or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public i2scimResponse patch(String path, JsonPatchRequest req, ScimReqParams params)
            throws ScimException, URISyntaxException, IOException, ParseException {
        if (isSchemaMode())
            throw new IOException("i2scimClient currently configured for builder operations only.");
        validateModifyParams(params);

        URI reqUri = prepareRequestUri(path, null, params);
        HttpPatch patch = new HttpPatch(reqUri);

        prepareHeaders(patch, params);
        StringEntity body = new StringEntity(req.toJsonNode().toString(), ContentType.create(ScimParams.SCIM_MIME_TYPE));
        patch.setEntity(body);
        CloseableHttpResponse resp = client.execute(patch);
        return new i2scimResponse(this, resp);

    }

    /**
     * Performs a SCIM Delete of the resource specified by path.
     * @param path   The relative path of the object to be deleted.
     * @param params Scim Request containing optional pre-conditions for the request
     * @return A {@link i2scimResponse} containing the object response or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws IOException        due to connection issues
     * @throws ParseException     when a response is not formatted correctly
     */
    public i2scimResponse delete(String path, ScimReqParams params) throws ScimException, IOException, ParseException, URISyntaxException {
        if (isSchemaMode())
            throw new IOException("i2scimClient currently configured for builder operations only.");
        validateModifyParams(params);
        URI reqUri = prepareRequestUri(path, null, params);
        HttpDelete del = new HttpDelete(reqUri);
        prepareHeaders(del, params);
        CloseableHttpResponse resp = client.execute(del);
        return new i2scimResponse(this, resp);
    }

    /**
     * Performs an HTTP HEAD request of the resource specified by path. Similar to SCIM GET but without returning the
     * resource.
     * @param path   The relative path of the object to be checked.
     * @param params Scim Request containing optional pre-conditions for the request
     * @return A {@link i2scimResponse} containing the object response or appropriate SCIM error
     * @throws ScimException      when a parsing error occurs parsing the results of a response. SCIM protocol errors
     *                            will be contained in i2scimResponse
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public i2scimResponse headInfo(String path, ScimReqParams params) throws ScimException, IOException, URISyntaxException, ParseException {
        if (isSchemaMode())
            throw new IOException("i2scimClient currently configured for builder operations only.");
        validateRetrieveParams(params, null);
        URI reqUri = prepareRequestUri(path, null, params);
        HttpHead head = new HttpHead(reqUri);
        prepareHeaders(head, params);
        CloseableHttpResponse resp = client.execute(head);
        return new i2scimResponse(this, resp);
    }


    /**
     * Executes an HTTP Request using the embedded HTTP Client. Note this bypassed SCIM protocol validation and allows
     * direct access to i2scimClient's CloseableHttpClient.
     * @param req An Apache HttpUriRequest object to execute.
     * @return A {@link CloseableHttpResponse} response object. Remember to call the close() method before performing
     * another request.
     * @throws IOException If an error occurs communicating with the server.
     */
    public CloseableHttpResponse execute(HttpUriRequest req) throws IOException {

        return client.execute(req);
    }

    protected URI mapPathToReqUrl(String path) throws MalformedURLException {
        try {
            return (new URL(serverRoot, path)).toURI();
        } catch (URISyntaxException e) {
            return null; // should not happen
        }

    }

    protected void prepareHeaders(HttpUriRequest request, ScimReqParams params) throws NotImplementedException {
        if (authorization != null) {
            request.setHeader(HttpHeaders.AUTHORIZATION, this.authorization);
        }
        request.setHeader("Content-type", ScimParams.SCIM_MIME_TYPE);
        //request.setHeader("Accept", ScimParams.SCIM_MIME_TYPE);

        if (params != null) {
            if (!etagSupport &&
                    (params.head_ifMatch != null || params.head_ifNoMatch != null ||
                            params.head_ifModSince != null || params.head_ifUnModSince != null))
                throw new NotImplementedException("SCIM Service provider does not RFC7232 Etag Precondition requests.");

            if (params.head_ifMatch != null)
                request.setHeader(HttpHeaders.IF_MATCH, params.head_ifMatch);
            if (params.head_ifNoMatch != null)
                request.setHeader(HttpHeaders.IF_NONE_MATCH, params.head_ifNoMatch);
            if (params.head_ifUnModSince != null)
                request.setHeader(HttpHeaders.IF_UNMODIFIED_SINCE, encodeHttpDate(params.head_ifUnModSince));
            if (params.head_ifModSince != null)
                request.setHeader(HttpHeaders.IF_MODIFIED_SINCE, encodeHttpDate(params.head_ifModSince));
        }
    }

    protected String encodeHttpDate(Date date) {
        if (date == null) return null;
        return httpDate.format(date);
    }

    protected URI prepareRequestUri(String path, String filter, ScimReqParams params)
            throws NotImplementedException, URISyntaxException {
        URIBuilder build = new URIBuilder(this.serverRoot.toString());
        build.setPath(path);

        // Question: should we validate the filter?
        if (filter != null) {
            if (!filterSupported)
                throw new NotImplementedException("SCIM Service provider does not support filter searches.");
            build.addParameter(ScimParams.QUERY_filter, URLEncoder.encode(filter, StandardCharsets.UTF_8));
        }
        if (params != null) {
            if (params.attrs != null)
                build.addParameter(ScimParams.QUERY_attributes, params.attrs);

            if (params.exclAttrs != null)
                build.addParameter(ScimParams.QUERY_excludedattributes, params.exclAttrs);

            if (params.sortBy != null) {
                if (!sortSupported)
                    throw new NotImplementedException("SCIM Service provider does not support sorting.");
                build.addParameter(ScimParams.QUERY_sortby, params.sortBy);
            }

            if (params.sortOrder != null) {
                if (!sortSupported)
                    throw new NotImplementedException("SCIM Service provider does not support sorting.");
                build.addParameter(ScimParams.QUERY_sortorder, (params.sortOrder ? "ascending" : "descending"));
            }

            if (params.startIndex > -1)
                build.addParameter(ScimParams.QUERY_startindex, Integer.toString(params.startIndex));

            if (params.count > -1)
                build.addParameter(ScimParams.QUERY_count, Integer.toString(params.count));
        }
        return build.build();
    }


    /**
     * @return The {@link SchemaManager} instance used by the i2scimClient. May be used to look up SCIM {@link
     * com.independentid.scim.schema.Schema} and {@link Attribute} definitions.
     */
    public SchemaManager getSchemaManager() {
        return this.schemaManager;
    }

    /**
     * Closes the HTTP Client connection and cleans up (closes SchemaManager).
     * @throws IOException thrown when closing CloseableHttpClient
     */
    public void close() throws IOException {
        if (this.client != null)
            this.client.close();
        if (this.schemaManager != null)
            this.schemaManager.shutdown();

    }
}
