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

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.InvalidValueException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.BulkOps;
import com.independentid.scim.op.Operation;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AccessControl;
import com.independentid.scim.security.AciSet;
import com.independentid.scim.security.ScimBasicIdentityProvider;
import com.independentid.scim.serializer.JsonUtil;
import io.quarkus.security.identity.SecurityIdentity;
import org.apache.http.HttpHeaders;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;
import java.io.InputStream;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

/**
 * @author pjdhunt POJO RequestCtx is use to pass the SCIM request parameters between components
 */
@SuppressWarnings("FieldCanBeLocal")
public class RequestCtx {
    public static String REQUEST_ATTRIBUTE = "ScimRequest";

    //ConfigMgr cmgr;

    SchemaManager smgr;

    //private static final Logger logger = LoggerFactory.getLogger(RequestCtx.class);

    public final static SimpleDateFormat headDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
	/*
	  This code is more robust but had timezone issues. Keep for future enhancement?
	final static DateTimeFormatter FORMAT_TIME_RFC1123 = DateTimeFormatter.RFC_1123_DATE_TIME;
	final static DateTimeFormatter FORMAT_TIME_RFC1036 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz");
	final static DateTimeFormatter FORMAT_TIME_ANSI = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy");
	final static DateTimeFormatter[] TIME_FORMATS = new DateTimeFormatter[] {
			FORMAT_TIME_RFC1036, FORMAT_TIME_RFC1123, FORMAT_TIME_ANSI
	};

	 */

    protected String path;

    // the top level path element
    protected String endpoint = null;
    // the {id} element (2nd element)
    protected String id = null;

    protected String bulkId = null;
    protected String bulkMethod = null;

    protected String child = null;

    protected TreeMap<String, Attribute> attrs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);  // attributes requested

    protected TreeMap<String, Attribute> excluded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // attributes to be excluded

    protected Filter filter = null; // request filter
    protected boolean clientNoFilterSpecd = true;

    protected String sortBy = null; // sort order attribute

    protected String sortOrder = null; // ascending or descending

    protected int startIndex = 1; // start with result n

    protected int count = 0; // number of results to return

    protected String etag = null;

    protected String match = null;
    protected String nmatch = null;
    protected String modsince = null;
    protected String unmodsince = null;

    protected AccessControl.Rights right;

    // This can be used by a provider to request URL encoded extension names be used
    // Used to address field name and length restrictions in some dbs
    protected boolean encodeFields = false;

    HttpServletRequest req = null;
    HttpServletResponse hresp = null;

    ServletContext sctx;

    private final boolean hasSecAuth = false;
    private SecurityIdentity identity = null;
    private Principal principal = null;
    private ArrayList<String> secRoles = null;
    private AciSet acis = null;

    boolean isMe = false;

    private boolean postSearch = false;

    protected String tid;

    private boolean isReplicaOp = false;

    /**
     * Used to create a request context that exists within a single SCIM Bulk operation
     * @param bulkReqOp     A parsed JSON structure representing a single SCIM bulk operation
     * @param schemaManager A pointer to the server ConfigMgr object (for schema)
     * @param isReplicaOp   A flag indicating if request is from a replication event
     * @throws SchemaException thrown when the bulk request JSON structure is invalid
     * @throws ScimException   thrown due to a schema or other Scim related issue
     */
    public RequestCtx(JsonNode bulkReqOp, SchemaManager schemaManager, boolean isReplicaOp) throws ScimException, SchemaException {
        if (!bulkReqOp.isObject()) {
            throw new SchemaException(
                    "Expecting an object element of 'Operations' array.");
        }
        this.smgr = schemaManager;
        this.sctx = null;
        this.tid = schemaManager.generateTransactionId();
        this.isReplicaOp = isReplicaOp;

        JsonNode item = bulkReqOp.get(BulkOps.PARAM_METHOD);
        if (item == null)
            throw new InvalidValueException(
                    "Bulk request operation missing required attribute 'method'.");
        this.bulkMethod = item.asText().toUpperCase();
        switch (this.bulkMethod) {
            case Operation.Bulk_Method_POST:
            case Operation.Bulk_Method_PUT:
            case Operation.Bulk_Method_PATCH:
            case Operation.Bulk_Method_DELETE:
                break;
            default:
                throw new InvalidValueException(
                        "Unsupported bulk method specified: " + this.bulkMethod);
        }

        item = bulkReqOp.get(BulkOps.PARAM_BULKID);
        this.bulkId = (item == null) ? null : item.asText();

        item = bulkReqOp.get(BulkOps.PARAM_VERSION);
        this.etag = (item == null) ? null : item.asText();


        // Parse the path, resource, and id if present
        //this.req = req;


        item = bulkReqOp.get(BulkOps.PARAM_PATH);
        if (item == null)
            throw new SchemaException(
                    "Bulk request operation missing required attribute 'path'.");
        this.path = item.asText();
        parsePath();

        // The following SCIM parameters not used in Bulk Ops
        setAttributes(null);
        setExcludedAttrs(null);
        filter = null;
        this.sortBy = null;
        this.sortOrder = null;
        setStartIndex(null);
        setCount(null);
    }

    public RequestCtx(String path, SchemaManager schemaManager) {
        this.path = path;
        this.smgr = schemaManager;
    }

    /**
     * Constructor typically used to generate internal requests for resources
     * @param resEndpoint   A String containing the top level container or resource type path element
     * @param id            A String that is the resource identifier (typically a GUID).
     * @param filter        A String representation of a SCIM query filter or null.
     * @param schemaManager A pointer to the server ConfigMgr object (for schema)
     * @throws ScimException for invalid filter, invalid parameters etc.
     */
    public RequestCtx(String resEndpoint, String id, String filter, SchemaManager schemaManager) throws ScimException {
        this.smgr = schemaManager;
        this.endpoint = resEndpoint;
        this.id = id;
        this.tid = schemaManager.generateTransactionId();
        StringBuilder buf = new StringBuilder();
        if (resEndpoint == null)
            resEndpoint = "/";

        if (!resEndpoint.startsWith("/"))
            buf.append('/');
        buf.append(resEndpoint);
        if (!resEndpoint.endsWith("/"))
            buf.append('/');
        if (id != null)
            buf.append(id);

        this.path = buf.toString();
        parsePath();
        if (filter != null) {
            this.filter = Filter.parseFilter(filter, this);
            clientNoFilterSpecd = false;
        }
        this.sctx = null;
    }

    /**
     * Constructor typically used in Spring Web Controller using annotated values from mapping, requestparam and
     * requestheader annotations.
     * @param sctx          The ServletContext handling the request (@ServletContext). Used mainly for getRealPath
     * @param resType       A String containing the top level container or resource type path element
     * @param id            A String that is the resource identifier (typically a GUID).
     * @param allParams     All parameters after "?" e.g. as provided by @RequestParam Map allParams
     * @param allHeaders    All HTTP request headers e,g, as provided by @RequestHeader MultiValueMap headers
     * @param body          Optional, the request body (e.g. from PATCH or PUT)
     * @param schemaManager A pointer to the server ConfigMgr object (for schema)
     * @throws ScimException for invalid filter, invalid parameters etc.
     */
    public RequestCtx(ServletContext sctx, String resType, String id,
                      Map<String, String> allParams, Map<String, String> allHeaders, String body, SchemaManager schemaManager) throws ScimException {
        this.smgr = schemaManager;
        this.endpoint = resType;
        this.id = id;
        this.tid = schemaManager.generateTransactionId();
        StringBuilder buf = new StringBuilder("/");
        if (resType != null) {
            buf.append(resType).append('/');
            if (id != null)
                buf.append(id);
        }
        this.sctx = sctx;

        this.path = buf.toString();
        parsePath();

        String param;
        param = allParams.get(ScimParams.QUERY_attributes);
        if (param != null)
            setAttributes(param);

        param = allParams.get(ScimParams.QUERY_excludedattributes);
        if (param != null)
            setExcludedAttrs(param);

        param = allParams.get(ScimParams.QUERY_filter);

        if (param != null) {
            this.filter = Filter.parseFilter(param, this);
            clientNoFilterSpecd = false;
        }

        this.sortBy = allParams.get(ScimParams.QUERY_sortby);
        this.sortOrder = allParams.get(ScimParams.QUERY_sortorder);

        setStartIndex(allParams.get(ScimParams.QUERY_startindex));
        setCount(allParams.get(ScimParams.QUERY_count));

        //Preconditions
        this.etag = allHeaders.get(ScimParams.HEADER_ETAG);

        this.match = trimQuotes(allHeaders.get(ScimParams.HEADER_IFMATCH));
        this.nmatch = trimQuotes(allHeaders.get(ScimParams.HEADER_IFNONEMATCH));
        this.modsince = trimQuotes(allHeaders.get(ScimParams.HEADER_IFMODSINCE));
        this.unmodsince = trimQuotes(allHeaders.get(ScimParams.HEADER_IFUNMODSINCE));

        if (body != null) {
            parseSearchBody(body);

        }

        if (this.sortOrder != null && !(this.sortOrder.startsWith("a")
                || this.sortOrder.startsWith("d")))
            throw new InvalidValueException("Invalid value for 'sortOrder' specified. Must be 'ascending' or 'descending'.");

    }

    /**
     * Constructor uses HTTPSevletRequest to capture the relevant SCIM Parameters from the URL and optionally from the
     * request body.
     * @param req           The {@link HttpServletRequest} provided by the Scim Servlet
     * @param resp          The HTTPServletResponse object (used to set status and headers)
     * @param schemaManager A pointer to the server ConfigMgr object (for schema)
     * @throws ScimException for invalid filter, invalid parameters etc.
     */
    public RequestCtx(HttpServletRequest req, HttpServletResponse resp, SchemaManager schemaManager) throws ScimException {
        this.req = req;
        this.tid = schemaManager.generateTransactionId();
        this.smgr = schemaManager;
        path = req.getPathInfo();
        if (path == null)
            path = req.getRequestURI();   // This seems to be needed for Quarkus

        // This added because SpringBoot MVC does not seem to use PathInfo in the same way.
        if (path == null)
            path = req.getServletPath();

        this.sctx = req.getServletContext();

        parseSecurityContext(req);

        this.hresp = resp;

        this.bulkId = null;
        parsePath();

        // Check the request based parameters. If body is provided they will overrride.
        String attrs = req.getParameter(ScimParams.QUERY_attributes);
        setAttributes(attrs);

        attrs = req.getParameter(ScimParams.QUERY_excludedattributes);
        setExcludedAttrs(attrs);

        String filt = req.getParameter(ScimParams.QUERY_filter);
        if (filt == null) filter = null;
        else {
            filter = Filter.parseFilter(filt, this);
            clientNoFilterSpecd = false;
        }

        this.sortBy = req.getParameter(ScimParams.QUERY_sortby);

        this.sortOrder = req.getParameter(ScimParams.QUERY_sortorder);

        String ind = req.getParameter(ScimParams.QUERY_startindex);
        setStartIndex(ind);

        ind = req.getParameter(ScimParams.QUERY_count);
        setCount(ind);

        //Preconditions
        this.etag = trimQuotes(req.getHeader(ScimParams.HEADER_ETAG));

        this.match = trimQuotes(req.getHeader(ScimParams.HEADER_IFMATCH));
        this.nmatch = trimQuotes(req.getHeader(ScimParams.HEADER_IFNONEMATCH));
        this.unmodsince = trimQuotes(req.getHeader(ScimParams.HEADER_IFUNMODSINCE));
        this.modsince = trimQuotes(req.getHeader(ScimParams.HEADER_IFMODSINCE));

        if (this.sortOrder != null && !(this.sortOrder.startsWith("a")
                || this.sortOrder.startsWith("d")))
            throw new InvalidValueException("Invalid value for 'sortOrder' specified. Must be 'ascending' or 'descending'.");
    }

    private String trimQuotes(String val) {
        if (val != null && val.startsWith("\""))
            return val.replaceAll("^\"|\"$", "");
        return val;
    }

    protected void parseSecurityContext(HttpServletRequest req) {
        //TODO to be implemented.
    }

    public boolean isPostSearch() {
        return this.postSearch;
    }

    public void parseSearchBody(Object body) throws ScimException {

        JsonNode node;
        try {
            if (body instanceof String)
                node = JsonUtil.getJsonTree((String) body);
            else
                node = JsonUtil.getJsonTree((InputStream) body);
        } catch (Exception e) {
            throw new InvalidSyntaxException("JSON Parsing error found parsing HTTP POST Body: " + e.getLocalizedMessage(), e);
        }

        if (node.isArray()) {
            throw new InvalidSyntaxException("Detected array, expecting JSON object for SCIM POST Search request.");
        }

        JsonNode vnode = node.get("schemas");
        if (vnode == null) throw new InvalidSyntaxException("JSON missing 'schemas' attribute.");

        boolean invalidSchema = true;
        if (vnode.isArray()) {
            Iterator<JsonNode> jiter = vnode.elements();
            while (jiter.hasNext() && invalidSchema) {
                JsonNode anode = jiter.next();
                if (anode.asText().equalsIgnoreCase(ScimParams.SCHEMA_API_SearchRequest))
                    invalidSchema = false;
            }
        } else if (vnode.asText().equalsIgnoreCase(ScimParams.SCHEMA_API_SearchRequest))
            invalidSchema = false;

        if (invalidSchema)
            throw new InvalidValueException("Expecting JSON with schemas attribute of: " + ScimParams.SCHEMA_API_PatchOp);

        vnode = node.get("attributes");
        if (vnode != null) {
            this.attrs.clear();
            if (vnode.isArray()) {
                Iterator<JsonNode> iter = vnode.elements();
                while (iter.hasNext()) {
                    String name = iter.next().asText();
                    Attribute attr = smgr.findAttribute(name, this);
                    if (attr != null)
                        this.attrs.put(attr.getPath(), attr);
                }
            } else {
                String name = vnode.asText();
                Attribute attr = smgr.findAttribute(name, this);
                if (attr != null)
                    this.attrs.put(attr.getPath(), attr);

            }
        }

        vnode = node.get("excludedAttributes");
        if (vnode != null) {
            this.excluded.clear();
            if (vnode.isArray()) {
                Iterator<JsonNode> iter = vnode.elements();
                while (iter.hasNext()) {
                    String name = iter.next().asText();
                    Attribute attr = smgr.findAttribute(name, this);
                    if (attr != null)
                        this.excluded.put(attr.getPath(), attr);
                }
            } else {
                String name = vnode.asText();
                Attribute attr = smgr.findAttribute(name, this);
                if (attr != null)
                    this.excluded.put(attr.getPath(), attr);
            }
        }
        vnode = node.get("filter");
        if (vnode != null) {
            this.filter = Filter.parseFilter(vnode.asText(), this);
            this.clientNoFilterSpecd = false;
        }

        vnode = node.get("sortBy");
        if (vnode != null) {
            this.sortBy = vnode.asText();
        }

        vnode = node.get("sortOrder");
        if (vnode != null)
            this.sortOrder = vnode.asText();

        vnode = node.get("startIndex");
        if (vnode != null)
            setStartIndex(vnode.asText()); // use the setter for consistency with url processing

        vnode = node.get("count");
        if (vnode != null)
            setCount(vnode.asText());  // use the setter for consistency with url processing

        this.postSearch = true;
    }

    public void setAttributes(String attrList) {
        this.attrs.clear();
        if (attrList == null) return;

        StringTokenizer tkn = new StringTokenizer(attrList, ",");
        while (tkn.hasMoreTokens()) {
            String name = tkn.nextToken();
            Attribute attr = smgr.findAttribute(name, this);
            if (attr != null)
                this.attrs.put(attr.getPath(), attr);
        }
    }

    public void addExcludeAttribute(Set<Attribute> excludedAttrs) {
        for (Attribute attr : excludedAttrs) {
            excluded.put(attr.getPath(), attr);
        }

    }

    public void setExcludedAttrs(String exclList) {
        this.excluded.clear();
        if (exclList == null) return;

        StringTokenizer tkn = new StringTokenizer(exclList, ",");
        while (tkn.hasMoreTokens()) {
            String name = tkn.nextToken();
            Attribute attr = smgr.findAttribute(name, this);
            if (attr != null)
                this.excluded.put(attr.getPath(), attr);
        }
    }

    public void setStartIndex(String ind) {
        if (ind != null)
            startIndex = Integer.parseInt(ind);
        else
            startIndex = 1;

    }

    /**
     * @return The number of items per page to be returned. 0 means unlimited.
     */
    public int getCount() {
        return count;
    }

    /**
     * @param ind A String value indicating the requested number of items per page. Null or "0" means unlimited.
     */
    public void setCount(String ind) {
        if (ind != null)
            count = Integer.parseInt(ind);
        else
            count = 0;

    }

    public void parsePath() {
        if (path == null || path.isEmpty()) {
            path = "/";
            endpoint = path;
            id = null;
            return;
        }
        if (path.startsWith("/v2/"))
            path = path.substring(3);

        if (path.equals(ScimParams.PATH_GLOBAL_SEARCH)) {
            this.postSearch = true;
            return;
        }
        String[] elems = path.split("/");

        switch (elems.length) {
            case 0:
                endpoint = "/";
                break;

            case 1:
                endpoint = elems[0];
                break;

            case 2:
                endpoint = elems[1];
                break;

            case 3:
                endpoint = elems[1];
                if (elems[2].equals(ScimParams.PATH_SUBSEARCH)) {
                    id = null;
                    postSearch = true;
                } else
                    id = elems[2];
                break;

            default: //3 or more
                endpoint = elems[1];
                if (elems[3].equals(ScimParams.PATH_SUBSEARCH)) {
                    child = null;
                    postSearch = true;
                } else
                    child = elems[3];
                break;
        }

        if (endpoint.equals(ScimParams.PATH_TYPE_ME)) {
            this.isMe = true;
            SecurityIdentity identity = getSecSubject();
            if (identity != null) {
                id = identity.getAttribute(ScimBasicIdentityProvider.ATTR_SELF_ID);
                if (id != null) endpoint = "Users";
            }
        }

    }

    public boolean isMe() {
        return isMe;
    }


    /**
     * @return The first level path within the Scim server.  E.g. "Users" or "Groups". For root level, a "/" is
     * returned.
     */
    public String getResourceContainer() {
        return this.endpoint;
    }

    /**
     * @param endpoint the endpoint/resource type minus the initial slash. Use "/" to indicate root level.
     */
    public void setResourceContainer(String endpoint) {
        this.endpoint = endpoint;
    }

    public Set<String> getAttrNamesReq() {
        return this.attrs.keySet();
    }

    public boolean isAttrExcluded(Attribute attr) {
        if (this.excluded.isEmpty()) return false;

        return this.excluded.containsValue(attr);
    }

    /**
     * Checks to see if the defined attribute has been requested or excluded from returning.
     * @param attr The {@link Attribute} object from {@link SchemaManager} to match.
     * @return True if the attribute is eligible for returning according to client.
     */
    public boolean isAttrRequested(Attribute attr) {
        switch (attr.getReturned()) {
            case Attribute.RETURNED_always:
                return true;
            case Attribute.RETURNED_never:
                return false;
            case Attribute.RETURNED_request:
                // is only returned when specifically requested
                return (this.attrs.containsValue(attr));
            case Attribute.RETURNED_default:
                if (this.attrs.containsValue(attr))
                    return true;
                if (this.excluded.containsValue(attr))
                    return false;
                return this.attrs.isEmpty();
        }
        return true;
    }

    public Set<String> getExcludedAttrNames() {
        return this.excluded.keySet();
    }


    /**
     * @return true if the request is at the root (does not have an endpoint)
     */
    public boolean isRoot() {
        return (this.endpoint == null || this.endpoint.equals("/"));
    }

    public String getIfMatch() {
        return this.match;
    }

    public String getIfNoneMatch() {
        return this.nmatch;
    }

    public String getUnmodSince() {
        return this.unmodsince;
    }

    public Instant getUnmodSinceDate() {
        return parseHttpDate(this.unmodsince);
    }

    public String getModSince() {
        return this.modsince;
    }

    public Instant getModSinceDate() {
        return parseHttpDate(this.modsince);
    }

    public String getBulkId() {
        return this.bulkId;
    }

    public String getBulkMethod() {
        return this.bulkMethod;
    }

    public String getVersion() {
        return this.etag;
    }

    /**
     * @return A String containing the original http request path
     */
    public String getPath() {
        return this.path;
    }

    /**
     * @return The resource identifier from the request path. E.g. /Users/{id}
     */
    public String getPathId() {
        return this.id;
    }

    public Filter getFilter() {
        return this.filter;
    }

    /**
     * Because ACIs can modify a request filter (tartgetFilter param), it is important to track whether client
     * originally requested a filter as the presence of a client filter is used to determine output format per RFC7644
     * Sec 3.4.2.
     * @return True if no filter was originally requested.
     */
    public boolean hasNoClientFilter() {
        return this.clientNoFilterSpecd;
    }

    public ServletContext getServletContext() {
        return this.sctx;
    }

    public HttpServletResponse getHttpServletResponse() {
        return this.hresp;
    }

    public HttpServletRequest getHttpServletRequest() {
        return this.req;
    }

    public String toString() {
        return this.path;
    }

    public SchemaManager getSchemaMgr() {
        return smgr;
    }

    public void setEncodeExtensions(boolean mode) {
        this.encodeFields = mode;
    }

    public boolean useEncodedExtensions() {
        return this.encodeFields;
    }

    /**
     * @return the hasSecAuth
     */
    public boolean isHasSecAuth() {
        return hasSecAuth;
    }

    /**
     * @return the secSubject
     */
    public SecurityIdentity getSecSubject() {
        return this.identity;
    }

    /**
     * @return the secRoles
     */
    public List<String> getSecRoles() {
        return secRoles;
    }

    public boolean hasRole(String role) {
        if (secRoles == null)
            return false;

        return secRoles.contains(role.toLowerCase());
    }

    public void setSecSubject(@NotNull SecurityIdentity identity) {

        this.identity = identity;
        principal = identity.getPrincipal();
        this.secRoles = new ArrayList<>();
        this.secRoles.addAll(
                identity.getRoles());
    }

    public Principal getPrincipal() {
        return principal;
    }

    public void setAciSet(AciSet acis) {
        this.acis = acis;
    }

    public void setAccessContext(@NotNull SecurityIdentity identity, AciSet acis) {

        this.identity = identity;
        this.secRoles = new ArrayList<>();
        this.secRoles.addAll(
                identity.getRoles());
        this.acis = acis;

    }

    public AciSet getAcis() {
        return this.acis;
    }

    public void setAcis(AciSet acis) {
        this.acis = acis;
    }

    /**
     * Takes the provided filter and adds it to an existing filter using an "and" clause. If there is no existing
     * filter, the provided filter becomes the filter. For example for many SCIM requests, the accesscontrol system may
     * wish to append the request with a targetFilter requirement.
     * @param appendfilter The filter to be added to the requested filter.
     */
    public void appendFilter(@NotNull Filter appendfilter) {
        if (this.filter == null)
            this.filter = appendfilter;
        else
            this.filter = new LogicFilter(true, this.filter, appendfilter);

    }

    /**
     * The existing filter is combined with an and clause with a list of filters provided which are combined in an OR
     * clause. This is done when more than one ACI is active with a targetFilter expression. E.g. ((targetFilter1 OR
     * targetFilter2) and (requestFilter))
     * @param filters A List of filters to be combined with OR and then appended with the original requestFilter.
     */
    public void combineFilters(@NotNull List<Filter> filters) {
        if (filters.isEmpty())
            return;

        if (filters.size() == 1) {
            appendFilter(filters.get(0));
            return;
        }

        Filter orClause;
        Iterator<Filter> iter = filters.iterator();
        orClause = iter.next();
        while (iter.hasNext())
            orClause = new LogicFilter(false, orClause, iter.next());

        // Take the "or" filter terms and combine with an and to the original filter.
        appendFilter(orClause);
    }

    public AccessControl.Rights getRight() {
        return this.right;
    }

    public void setRight(AccessControl.Rights right) {
        this.right = right;
    }

    public String getTranId() {
        return this.tid;
    }

    /**
     * Used to override the transaction identifier. Typically used when executing a replication transaction and the
     * original TID is to be maintained
     * @param tranUuid The transaction id (a UUID) to be set.
     */
    public void setTranId(String tranUuid) {
        this.tid = tranUuid;
    }

    public boolean isReplicaOp() {
        return this.isReplicaOp;
    }

    static Instant parseHttpDate(String httpdate) {
        if (httpdate == null) return null;

        try {
            return headDate.parse(httpdate).toInstant();
        } catch (ParseException ignore) {
        }

        return null;  // If we can't parse, just ignore
    }

    public boolean isAnonymous() {
        if (this.req == null)
            return true;
        return this.req.getHeader(HttpHeaders.AUTHORIZATION) == null;

    }
}
