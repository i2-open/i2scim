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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.*;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;

/**
 * i2scimResponse takes an HttpResponse and parses response SCIM payloads and error messages for easy access.
 */
public class i2scimResponse extends ScimResponse implements Iterator<ScimResource> {
    CloseableHttpResponse resp;
    private final i2scimClient client;
    private boolean hasMore = true;  // indicates more streaming to parse
    private JsonParser parser;
    private boolean isSingle = true;
    private ScimResource nextRes = null;
    int totalRes = -1;
    int startIndex = -1;
    int itemsPerPage = -1;

    // Headers
    private String lastModification = null;
    private String etag = null;
    private String location = null;

    /**
     * This constructor initiated by i2scimClient
     * @param client   The i2scimClient initiating the response
     * @param response The HttpResponse received.
     * @throws IOException    occurs when pulling data from HttpResponse.getEntity
     * @throws ScimException  is thrown when a SCIM parsing error occurs.  Server errors conveyed through methods
     * @throws ParseException occurs when parsing URL, dates, etc.
     */
    i2scimResponse(i2scimClient client, CloseableHttpResponse response) throws IOException, ScimException, ParseException {
        this.resp = response;
        this.client = client;
        parseResponseHeaders();
        checkErrorResponse();
        if (!hasError())
            initializeIterator();

    }

    private void parseResponseHeaders() {
        Header[] headers = resp.getHeaders(ScimParams.HEADER_LASTMOD);
        if (headers != null && headers.length > 0)
            this.lastModification = headers[0].getValue();

        headers = resp.getHeaders(ScimParams.HEADER_ETAG);
        if (headers != null && headers.length > 0)
            this.etag = headers[0].getValue();

        headers = resp.getHeaders(ScimParams.HEADER_LOCATION);
        if (headers != null && headers.length > 0)
            this.location = headers[0].getValue();
    }

    /**
     * @return The value of the HTTP Etag header or null if not present.
     */
    public String getEtag() {
        return this.etag;
    }

    /**
     * @return The lastModification HTTP Header.
     */
    public String getLastModification() {
        return this.lastModification;
    }

    /**
     * @return The lastModification header HttpHeader in Date form
     * @throws ParseException Thrown when the String date cannot be parsed into a Date
     */
    public Date getLastModificationDate() throws ParseException {
        return i2scimClient.httpDate.parse(this.lastModification);
    }

    /**
     * Indicates i2scimResponse detected an error response from the server
     * @return true of HttpStatus greater than or equal to 400
     */
    public boolean hasError() {
        return (getStatus() >= 400);
    }

    /**
     * @return For single entry responses, the location is the URL of the entity returned. For all List Responses, it is
     * the location of the query.
     */
    public String getLocation() {
        return this.location;
    }

    /**
     * Initializes the parsing stream and determines if the returned stream is a single resource, a ListResponse. Note:
     * not called if there is an error.
     * @throws IOException    May occur when reading the HttpEntity.getContent() stream
     * @throws ScimException  May occur parsing a SCIM result
     * @throws ParseException May occur parsing strings, dates, etc.
     */
    private void initializeIterator() throws IOException, ScimException, ParseException {
        JsonFactory jfactory = new JsonFactory();
        InputStream stream = getRawStream();
        if (stream == null) {
            hasMore = false;
            return;
        }

        parser = jfactory.createParser(getRawStream());
        hasMore = true;

        ObjectNode root = JsonUtil.getMapper().createObjectNode();  // this is used to convert in the case of a simple response (single resource)

        JsonToken rootToken = parser.nextToken();
        if (!rootToken.equals(JsonToken.START_OBJECT))
            throw new ScimException("Expecting JSON start object in SCIM Resoponse");

        while (parser.nextToken() != JsonToken.END_OBJECT && nextRes == null) {
            String name = parser.currentName();
            switch (name) {
                case ScimParams.ATTR_SCHEMAS:
                    parser.nextToken(); // begin array
                    ArrayNode array = root.putArray(ScimParams.ATTR_SCHEMAS);
                    while (!parser.nextToken().equals(JsonToken.END_ARRAY)) {
                        String schemaVal = parser.getValueAsString();
                        if (schemaVal.equalsIgnoreCase(ScimParams.SCHEMA_API_ListResponse)) {
                            isSingle = false;
                        }
                        array.add(schemaVal);
                    }
                    continue;
                case ListResponse.ATTR_TOTRES:
                    parser.nextToken();
                    this.totalRes = parser.getIntValue();
                    continue;
                case ListResponse.ATTR_STARTINDEX:
                    parser.nextToken();
                    this.startIndex = parser.getIntValue();
                    continue;
                case ListResponse.ATTR_ITEMPERPAGE:
                    parser.nextToken();
                    this.itemsPerPage = parser.getIntValue();
                    continue;
                case ListResponse.ATTR_RESOURCES:
                    parser.nextToken(); // start array
                    if (parser.nextToken() != JsonToken.END_ARRAY) {
                        // should be at first object
                        loadNextResource();  // load the first resource and return;
                        return;
                    } else { // if END_ARRAY occurs immediately there are no results.
                        hasMore = false;
                    }
                    continue;
                default:
                    parser.nextToken();
                    JsonNode node = JsonUtil.getMapper().readTree(parser);
                    root.set(name, node);

            }
        }
        if (isSingle) {
            hasMore = false; // no more stream to process
            nextRes = new ScimResource(client.getSchemaManager(), root, null);
        }
        parser.close();
    }

    /**
     * Obtains the raw result as a stream (uses HttpEntity.getContent()).
     * @return An {@link InputStream} containing the unprocessed result.
     * @throws IOException errors related to processing the HTTP Response
     */
    private InputStream getRawStream() throws IOException {
        HttpEntity entity = resp.getEntity();
        if (entity == null) return null;
        return entity.getContent();
    }

    /**
     * Parses the next resource in the stream.
     * @throws IOException    May occur when reading the HttpEntity.getContent() stream
     * @throws ScimException  May occur parsing a SCIM result
     * @throws ParseException May occur parsing strings, dates, etc.
     */
    private void loadNextResource() throws IOException, ScimException, ParseException {
        if (!hasMore)
            return;

        JsonNode node = JsonUtil.getMapper().readTree(parser);
        this.nextRes = new ScimResource(client.getSchemaManager(), node, null);
        if (parser.nextToken().equals(JsonToken.END_ARRAY)) {
            hasMore = false;
            parser.close(); // close the stream
        }
    }

    /**
     * Check HttpStatus and headers to determine what type of error has occurred if any. If HttpStatus 400, the response
     * body is parsed for the SCIM error details.
     * @throws IOException Thrown when reading the HttpEntity.
     */
    private void checkErrorResponse() throws IOException {

        if (resp == null) return;
        int status = resp.getStatusLine().getStatusCode();
        setStatus(status);
        switch (status) {
            case HttpStatus.SC_ACCEPTED:
            case HttpStatus.SC_OK:
                return; // Not an error

            case HttpStatus.SC_BAD_REQUEST:
                HttpEntity entity = resp.getEntity();
                String msg;
                if (entity != null) {

                    msg = EntityUtils.toString(entity);
                    JsonNode node = JsonUtil.getJsonTree(msg);
                    JsonNode typNode = node.get("scimType");
                    String type = "";
                    String det = null;
                    if (typNode != null) {
                        type = typNode.asText();
                        JsonNode detNode = node.get("detail");
                        if (detNode != null)
                            det = detNode.asText();
                    }
                    switch (type) {
                        case ScimResponse.ERR_TYPE_BADVAL:
                            setError(new InvalidValueException(det == null ?
                                    "A required value was missing or invalid" : det));
                            return;
                        case ScimResponse.ERR_TYPE_FILTER:
                            setError(new BadFilterException(det == null ?
                                    "The specified filter was invalid" : det));
                            return;
                        case ScimResponse.ERR_TYPE_TOOMANY:
                            setError(new TooManyException(det == null ?
                                    "The specified filter yields more results than the server is willing to calculate" : det));
                            return;
                        case ScimResponse.ERR_TYPE_UNIQUENESS:
                            setError(new ScimException(det == null ?
                                    "One or more unique attribute values in use." : det, ScimResponse.ERR_TYPE_UNIQUENESS));
                            return;
                        case ScimResponse.ERR_TYPE_MUTABILITY:
                            setError(new ScimException(det == null ?
                                    "An attempted modification was not compatible with an attributes mutability." : det, ScimResponse.ERR_TYPE_MUTABILITY));
                            return;
                        case ScimResponse.ERR_TYPE_SYNTAX:
                            setError(new InvalidSyntaxException(det == null ?
                                    "The request body structure was invalid." : det));
                            return;
                        // TODO: invalidPath implemented in i2scim server
                        case ScimResponse.ERR_TYPE_PATH:
                            setError(new ScimException(det == null ?
                                    "The attribute supplied was undefined, malformed, or invalid." : det, ScimResponse.ERR_TYPE_PATH));
                            return;
                        case ScimResponse.ERR_TYPE_TARGET:
                            setError(new NoTargetException(det == null ?
                                    "The path attribute did not yield an attribute that could be operated on." : det));
                            return;
                        case ScimResponse.ERR_TYPE_BADVERS:
                            setError(new ScimException(det == null ?
                                    "The specified SCIM protocol version is not supported" : det, ScimResponse.ERR_TYPE_BADVERS));
                            return;
                        // TODO: i2scim currently does not report sensitive errors!
                        case "sensitive":
                            setError(new ScimException(det == null ?
                                    "The specified request cannot be completed due to passing of sensitive information in a URI. Use POST." : det, "sensitive"));
                            return;
                        default:
                            setError(new ScimException(det, type));
                            return;
                    }

                }
                return;

            case HttpStatus.SC_UNAUTHORIZED:
                setError(new UnauthorizedException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
                return;
            case HttpStatus.SC_FORBIDDEN:
                setError(new ForbiddenException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
                return;
            case HttpStatus.SC_NOT_FOUND:
                setError(new NotFoundException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
                return;
            case HttpStatus.SC_CONFLICT:
                setError(new ConflictException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
                return;
            case HttpStatus.SC_PRECONDITION_FAILED:
                setError(new PreconditionFailException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
                return;
            case HttpStatus.SC_REQUEST_TOO_LONG:
                setError(new TooLargeException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
                return;
            case HttpStatus.SC_INTERNAL_SERVER_ERROR:
                setError(new InternalException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
                return;
            case HttpStatus.SC_NOT_IMPLEMENTED:
                setError(new NotImplementedException("Server responded with " + resp.getStatusLine().getReasonPhrase()));
        }
    }

    /**
     * Closes the response and any associated streams
     * @throws IOException errors related to processing the HTTP Response
     */
    public void close() throws IOException {
        this.resp.close();
    }

    /**
     * @return True if the response was a single resource response
     */
    public boolean isSingle() {
        return this.isSingle;
    }

    /**
     * @return True if next() has another ScimResource available
     */
    @Override
    public boolean hasNext() {
        return !hasError() && (hasMore || nextRes != null);
    }

    /**
     * @return A {@link ScimResource} parsed resourced
     */
    @Override
    public ScimResource next() {
        ScimResource next = nextRes;
        nextRes = null;
        try {
            loadNextResource();
        } catch (IOException | ScimException | ParseException e) {
            e.printStackTrace();
        }
        return next;

    }

    /**
     * Returns the total number of results. NOTE: because this client uses a streaming parser, the value may not be set
     * if the service provider put the "totalResults" attribute after "Resources" in the JSON response document.
     * @return The value of ListResponse totalResults attribute
     */
    public int getTotalResults() {
        return this.totalRes;
    }

    /**
     * Returns the total number of items per page in the ListResponse. NOTE: because this client uses a streaming
     * parser, the value may not be set if the service provider put the "itermsPerPage" attribute after "Resources" in
     * the JSON response document.
     * @return The value of ListResponse itermsPerPage attribute
     */
    public int getItemsPerPage() {
        return this.itemsPerPage;
    }

    /**
     * Returns the startIndex value when using paged results. NOTE: because this client uses a streaming parser, the
     * value may not be set if the service provider put the "startIndex" attribute after "Resources" in the JSON
     * response document.
     * @return The value of ListResponse startIndex attribute
     */
    public int getStartIndex() {
        return this.startIndex;
    }
}
