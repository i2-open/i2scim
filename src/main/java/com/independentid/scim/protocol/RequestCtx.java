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

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.InvalidValueException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author pjdhunt
 * POJO RequestCtx is use to pass the SCIM request parameters between components
 */
@SuppressWarnings("FieldCanBeLocal")
public class RequestCtx {

	ConfigMgr cmgr;

	//private static final Logger logger = LoggerFactory.getLogger(RequestCtx.class);

	final static SimpleDateFormat headDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
	
	//protected HttpServletRequest req;
	
	//protected ServletContext sctx;
	
	protected String path;
	
	// the top level path element
	protected String endpoint = null;
	// the {id} element (2nd element)
	protected String id = null; 
	
	protected String bulkId = null;
	protected String bulkMethod = null;	
	
	protected String child = null;
	
	protected TreeMap<String,Attribute> attrs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);  // attributes requested

	protected TreeMap<String,Attribute> excluded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // attributes to be excluded
	
	protected Filter filter = null; // request filter

	protected String sortBy = null; // sort order attribute

	protected String sortOrder = null; // ascending or descending
	
	protected int startIndex = 1; // start with result n

	protected int count = 100; // number of results to return
	
	protected String etag = null;
	
	protected String match = null;
	protected String nmatch = null;
	protected String since = null;
		
	// This can be used by a provider to request URL encoded extension names be used
	// Used to address field name and length restrictions in some dbs
	protected boolean encodeFields = false;
	
	HttpServletResponse hresp = null;
	
	ServletContext sctx;
	
	private final boolean hasSecAuth = false;
	private final String secSubject = null;
	private final ArrayList<String> secRoles = null;

	/**
	 * Used to create a request context that exists within a single SCIM Bulk operation
	 * @param bulkReqOp A parsed JSON structure representing a single SCIM bulk operation
	 * @param cmgr A pointer to the server ConfigMgr object (for schema)
	 * @throws SchemaException thrown when the bulk request JSON structure is invalid
	 * @throws ScimException thrown due to a schema or other Scim related issue
	 */
	public RequestCtx(JsonNode bulkReqOp, ConfigMgr cmgr) throws ScimException, SchemaException {
		if (!bulkReqOp.isObject()) {
			throw new SchemaException(
					"Expecting an object element of 'Operations' array.");
		}
		this.cmgr = cmgr;
		this.sctx = null;
	

		JsonNode item = bulkReqOp.get("method");
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

		item = bulkReqOp.get("bulkId");
		this.bulkId = (item == null) ? null : item.asText();

		item = bulkReqOp.get("version");
		this.etag = (item == null) ? null : item.asText();

		
		// Parse the path, resource, and id if present
		//this.req = req;
		
		
		item = bulkReqOp.get("path");
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
		
		
		//if (logger.isDebugEnabled())
		//	logger.debug("Bulk RequestCtx parsed with path: "+this.path);

	}
	
	/**
	 * Constructor typically used to generate internal requests for resources
	 * @param resEndpoint A String containing the top level container or resource type path element
	 * @param id A String that is the resource identifier (typically a GUID).
	 * @param filter A String representation of a SCIM query filter or null.
	 * @param configMgr A pointer to the server ConfigMgr object (for schema)
	 * @throws ScimException for invalid filter, invalid parameters etc.
	 */
	public RequestCtx(String resEndpoint, String id, String filter, ConfigMgr configMgr) throws ScimException {
		this.cmgr = configMgr;
		this.endpoint = resEndpoint;
		this.id = id;
		StringBuilder buf = new StringBuilder();
		
		if (resEndpoint != null) {
			if (!resEndpoint.startsWith("/"))
				buf.append('/');
			buf.append(resEndpoint);
			if (!resEndpoint.endsWith("/"))
				buf.append('/');
			if (id !=null)
				buf.append(id);
		}
		this.path = buf.toString();
		parsePath();
		if (filter != null)
			this.filter = Filter.parseFilter(filter, this);
		this.sctx = null;
	}
	
	/**
	 * Constructor typically used in Spring Web Controller using annotated values from mapping, requestparam and requestheader annotations.
	 @ @param sctx The ServletContext handling the request (@ServletContext). Used mainly for getRealPath
	 * @param resType A String containing the top level container or resource type path element
	 * @param id A String that is the resource identifier (typically a GUID).
	 * @param allParams All parameters after "?" e.g. as provided by @RequestParam Map<String,String> allParams
	 * @param allHeaders All HTTP request headers e,g, as orovided by @RequestHeader MultiValueMap<String, String> headers
	 * @param body Optional, the request body (e.g. from PATCH or PUT)
	 * @param configMgr A pointer to the server ConfigMgr object (for schema)
	 * @throws ScimException for invalid filter, invalid parameters etc.
	 */
	public RequestCtx(ServletContext sctx, String resType, String id,
					  Map<String, String> allParams, Map<String, String> allHeaders, String body, ConfigMgr configMgr) throws ScimException {
		this.cmgr = configMgr;
		this.endpoint = resType;
		this.id = id;
		StringBuilder buf = new StringBuilder("/");
		if (resType != null) {
			buf.append(resType).append('/');
			if (id !=null)
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
		}
		
		this.sortBy = allParams.get(ScimParams.QUERY_sortby);
		this.sortOrder = allParams.get(ScimParams.QUERY_sortorder);
		
		setStartIndex(allParams.get(ScimParams.QUERY_startindex));
		setCount(allParams.get(ScimParams.QUERY_count));
		
		//Preconditions
		this.etag = allHeaders.get(ScimParams.HEADER_ETAG);
				
		this.match = allHeaders.get(ScimParams.HEADER_IFMATCH);
		this.nmatch = allHeaders.get(ScimParams.HEADER_IFNONEMATCH);
		this.since = allHeaders.get(ScimParams.HEADER_IFUNMODSINCE);
		
		if (body != null) {
			parseSearchBody(body);
			
		}
		
		if (this.sortOrder != null && !(this.sortOrder.equals("ascending")
				|| this.sortOrder.equals("descending")))
			throw new InvalidValueException("Invalid value for 'sortOrder' specified. Must be 'ascending' or 'descending'.");

	}

	/**
	 * Constructor uses HTTPSevletRequest to capture the relevant SCIM Parameters from the URL and optionally from the request body.
	 * @param req The {@link HttpServletRequest} provided by the Scim Servlet
	 * @param resp The HTTPServletResponse object (used to set status and headers)
	 * @param configMgr A pointer to the server ConfigMgr object (for schema)
	 * @throws ScimException for invalid filter, invalid parameters etc.
	 */
	public RequestCtx(HttpServletRequest req, HttpServletResponse resp, ConfigMgr configMgr) throws ScimException {
		//this.req = request;
		//sctx = request.getServletContext();
		this.cmgr = configMgr;
		path = req.getPathInfo();
		if (path == null)
			path = req.getRequestURI();   // This seems to be needed for Quarkus
		
		// This added because SpringBoot MVC does not seem to use PathInfo in the same way.
		if (path == null)
			path = req.getServletPath();
		
		this.sctx = req.getServletContext();

		parseSecurityContext(req);
		
		//parseSecurityContext();
		
		
		//.forEach(role -> logger.debug("User: "+curUser+", Role: "+role.getAuthority()));
		//boolean hasUserRole = authentication.getAuthorities().stream()
		//          .anyMatch(r -> r.getAuthority().equals("ROLE_USER"));
		
		//Object pobj = req.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
		//if (pobj != null) logger.debug("path withing mapping:\t"+pobj);
		//logger.debug("Ctx.ContextPath:\t"+this.sctx.getContextPath()); 
		//logger.debug("ServletPath:\t"+req.getServletPath());
		//logger.debug("ContextPath:\t"+req.getContextPath());
		//logger.debug("Path Trans:\t"+req.getPathTranslated());
		//logger.debug("PathInfo:\t"+req.getPathInfo());
		
		//String path = request.getRequestURI().substring(request.getContextPath().length());
		this.hresp = resp;
		
		this.bulkId = null;
		parsePath();
		
		// Check the request based parameters. If body is provided they will overrride.
		String attrs = req.getParameter(ScimParams.QUERY_attributes);
		setAttributes(attrs);
		
		attrs = req.getParameter(ScimParams.QUERY_excludedattributes);
		setExcludedAttrs(attrs);
		
		String filt = req.getParameter(ScimParams.QUERY_filter);
		if (filt == null) filter=null;
		else
			filter = Filter.parseFilter(filt, this);
		
		this.sortBy = req.getParameter(ScimParams.QUERY_sortby);
		
		this.sortOrder = req.getParameter(ScimParams.QUERY_sortorder);
		
		String ind = req.getParameter(ScimParams.QUERY_startindex);
		setStartIndex(ind);
		
		ind = req.getParameter(ScimParams.QUERY_count);
		setCount(ind);
		
		//Preconditions
		this.etag = req.getParameter(ScimParams.HEADER_ETAG);

		this.match = req.getParameter(ScimParams.HEADER_IFMATCH);
		this.nmatch = req.getParameter(ScimParams.HEADER_IFNONEMATCH);
		this.since = req.getParameter(ScimParams.HEADER_IFUNMODSINCE);
		
		// If this was a POST search request, parse the body for parameters
		//* this can't be invoked here becaues cmgr will not be ready. Moved to SearchOp doPreOperation
		/*
		if (parseBody) {
			ServletInputStream input = req.getInputStream();
			parseSearchBody(input);
		}
		 */
		
		if (this.sortOrder != null && !(this.sortOrder.equals("ascending")
				|| this.sortOrder.equals("descending")))
			throw new InvalidValueException("Invalid value for 'sortOrder' specified. Must be 'ascending' or 'descending'.");

		//if (logger.isDebugEnabled())
		//	logger.debug((parseBody?"Body ":"URL ")+"RequestCtx parsed path: "+this.getPath());

	}

	protected void parseSecurityContext(HttpServletRequest req) {
		//TODO to be implemented.
	}
	/*
	protected void parseSecurityContext() {
		secAuth = SecurityContextHolder.getContext().getAuthentication();
		if (secAuth != null) {
			hasSecAuth = true;
			secSubject = secAuth.getName();
		
		} else
			return;
		secRoles = new ArrayList<String>();
		
		
		Collection<? extends GrantedAuthority> authorities = secAuth.getAuthorities();
		if (authorities == null) throw new IllegalStateException("No user currently logged in");

		   
		for (GrantedAuthority grantedAuthority : authorities) {
			secRoles.add(grantedAuthority.getAuthority().toLowerCase());
		}
	
	}
	*/

	public void parseSearchBody(Object body) throws ScimException {
		
		JsonNode node;
		try {
			if (body instanceof String)
				node = JsonUtil.getJsonTree((String) body);
			else
				node = JsonUtil.getJsonTree((InputStream) body);
		} catch (Exception e) {
			throw new InvalidSyntaxException("JSON Parsing error found parsing HTTP POST Body: "+e.getLocalizedMessage(),e);
		}
		
		if (node.isArray()) {
			throw new InvalidSyntaxException("Detected array, expecting JSON object for SCIM POST Search request.");
		}
		
		JsonNode vnode = node.get("schemas");
		if (vnode == null) throw new InvalidSyntaxException("JSON missing 'schemas' attribute.");
		
		boolean invalidSchema = true;
		if (vnode.isArray()) {
			Iterator<JsonNode> jiter = vnode.elements();
			while (jiter.hasNext() && invalidSchema){
				JsonNode anode = jiter.next();
				if (anode.asText().equalsIgnoreCase(ScimParams.SCHEMA_API_SearchRequest))
					invalidSchema = false;
			}
		} else
			if (vnode.asText().equalsIgnoreCase(ScimParams.SCHEMA_API_SearchRequest))
				invalidSchema = false;
		
		if (invalidSchema)
			throw new InvalidValueException("Expecting JSON with schemas attribute of: "+ScimParams.SCHEMA_API_PatchOp);
		
		vnode = node.get("attributes");
		if (vnode != null) {
			this.attrs.clear();
			if (vnode.isArray()) {
				Iterator<JsonNode> iter = vnode.elements();
				while (iter.hasNext()) {
					String name = iter.next().asText();
					Attribute attr = cmgr.findAttribute(name, this);
					if (attr != null)
						this.attrs.put(attr.getPath(),attr);
				}
			} else {
				String name = vnode.asText();
				Attribute attr = cmgr.findAttribute(name, this);
				if (attr != null)
					this.attrs.put(attr.getPath(),attr);
				
			}
		}
		
		vnode = node.get("excludedAttributes");
		if (vnode != null) {
			this.excluded.clear();
			if (vnode.isArray()) {
				Iterator<JsonNode> iter = vnode.elements();
				while (iter.hasNext()) {
					String name = iter.next().asText();
					Attribute attr = cmgr.findAttribute(name, this);
					if (attr != null)
						this.excluded.put(attr.getPath(),attr);
				}
			} else {
				String name = vnode.asText();
				Attribute attr = cmgr.findAttribute(name, this);
				if (attr != null)
					this.excluded.put(attr.getPath(),attr);
			}
		}			
		vnode = node.get("filter");
		if (vnode != null) {
			this.filter = Filter.parseFilter(vnode.asText(), this);					
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
			this.startIndex = vnode.asInt();
		
		vnode = node.get("count");
		if (vnode != null)
			this.count = vnode.asInt();
		
	}	
	
	public void setAttributes(String attrList) {
		this.attrs.clear();
		if (attrList == null) return;
		
		StringTokenizer tkn = new StringTokenizer(attrList,",");
		while (tkn.hasMoreTokens()) {
			String name = tkn.nextToken();
			Attribute attr = cmgr.findAttribute(name, this);
			if (attr != null)
				this.attrs.put(attr.getPath(),attr);
		}
	}
	
	public void setExcludedAttrs(String exclList) {
		this.excluded.clear();
		if (exclList == null) return;
		
		StringTokenizer tkn = new StringTokenizer(exclList,",");
		while (tkn.hasMoreTokens()) {
			String name = tkn.nextToken();
			Attribute attr = cmgr.findAttribute(name, this);
			if (attr != null)
				this.excluded.put(attr.getPath(),attr);	
		}
	}
	
	public void setStartIndex(String ind) {
		if (ind != null)
			startIndex = Integer.parseInt(ind);
		else
			startIndex = 1;
		
	}
	
	public void setCount(String ind) {
		if (ind != null)
			count = Integer.parseInt(ind);
		else
			count = 100;
		
	}
	
	public void parsePath() {
		if (!path.contains("/")) {
			if (path.isEmpty())
				return;
			endpoint = path;
			return;
		}
		if (path.equals(ScimParams.PATH_SEARCH))
			return;
		String[] elems = path.split("/");
		//System.out.println("\nPath=["+path+"]\n");
		if (elems.length == 1)
			endpoint = elems[0];
		else
			endpoint = elems[1];
		
		if (elems.length > 2) {
			if (elems[2].equals(ScimParams.PATH_SUBSEARCH))
				id = null;
			else
				id = elems[2];
		}
		if (elems.length > 3) {
			if (elems[3].equals(ScimParams.PATH_SUBSEARCH))
				child= null;
			else
				child = elems[3];
		}

	}
	
	/**
	 * @return The first level path within the Scim server.  E.g. "Users" or "Groups". For root level, a "/" is returned.
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
	 * @param attr The <Attribute> object from ConfigMgr to match.
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
		return (this.endpoint == null);
	}
	
	public String getIfMatch() {
		return this.match;
	}
	
	public String getIfNoneMatch() {
		return this.nmatch;
	}
	
	public String getUnmodSince() {
		return this.since;
	}
	
	public Date getUnmodSinceDate() throws ParseException {
		if (this.since == null) return null;
		
		return headDate.parse(this.since);
		
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
	
	/*
	public HttpServletRequest getHttpRequest() {
		return this.req;
	}
	*/
	public ServletContext getServletContext() {
		return this.sctx;
	}
	
	public HttpServletResponse getHttpServletResponse() {
		return this.hresp;
	}
	
	
	public String toString() {
		return this.path;
	}
	
	public ConfigMgr getConfigMgr() {
		return cmgr;
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
	public String getSecSubject() {
		return secSubject;
	}

	/**
	 * @return the secRoles
	 */
	public List<String> getSecRoles() {
		return secRoles;
	}
	
	public boolean hasRole(String role) {
		if (secRoles == null)
			return true;
		
		return secRoles.contains(role.toLowerCase());
	}
	
}
