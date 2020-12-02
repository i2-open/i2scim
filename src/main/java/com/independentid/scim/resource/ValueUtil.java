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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.Meta;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Set;

/**
 * ValueUtil is a general utility to parse JsonNode structures and create the correct Value object class.
 * @author pjdhunt
 *
 */
public class ValueUtil {
	
	public final static String TYPE_STRING = "string";
	public final static String TYPE_COMPLEX = "complex";
	public final static String TYPE_BOOLEAN = "boolean";
	public final static String TYPE_DATETIME = "datetime";
	public final static String TYPE_BINARY = "binary";
	public final static String TYPE_REF = "reference";
	public final static String TYPE_INTEGER = "integer";
	public final static String TYPE_DECIMAL = "decimal";

	static ConfigMgr cfg = ConfigMgr.getConfig();
	
	/**
	 * Static method used to parse a <JsonNode> object for its appropriate SCIM Value type based on the declared Attribute.
	 * @param attr The SCIM <Attribute> type for the value being parsed
	 * @param fnode A <JsonNode> representing the attribute/value node.
	 * @param bulkIdResolver This resolver is used for bulk operations where an Identifier may be temporary.
	 * @return The parsed <Value> instance. See com.independentid.scim.resource package for sub-classes (Complex, String,
	 * Boolean, DateTime, Binary, Reference).
	 * @throws ConflictException May be thrown by ValueUtil parser.
	 * @throws SchemaException May be thrown by ValueUtil parser.
	 * @throws ParseException May be thrown by ValueUtil parser.
	 */
	public static Value parseJson(Attribute attr, JsonNode fnode, IBulkIdResolver bulkIdResolver)
			throws ConflictException, SchemaException, ParseException {
		// TODO Should we treat as string by default when parsing unknown
		// schema?
		if (attr == null)
			throw new SchemaException("Unable to parse Value. Missing Attribute type: "
					+ fnode.toPrettyString());

		Value val = null;
		//logger.debug(attr.toString());
		//logger.debug("Attr: "+attr.getName()+", aType: "+attr.getType()+", nType: "+fnode.getNodeType()+", aMulti: "+attr.isMultiValued());

		if (attr.isMultiValued() && fnode.getNodeType().equals(JsonNodeType.ARRAY)) {
			val = new MultiValue(attr, fnode, bulkIdResolver);
		} else {
			switch (attr.getType().toLowerCase()) {
			case TYPE_STRING:
				val = new StringValue(attr, fnode);
				break;
			case TYPE_COMPLEX:
				val = new ComplexValue(attr, fnode, null);
				break;
			case TYPE_BOOLEAN:
				val = new BooleanValue(attr, fnode);
				break;
			case TYPE_DATETIME:
				val = new DateValue(attr, fnode);
				break;
			case TYPE_BINARY:
				val = new BinaryValue(attr, fnode);
				break;
			case TYPE_INTEGER:
				val = new IntegerValue(attr,fnode);
				break;
			case TYPE_REF:
				val = new ReferenceValue(attr, fnode, bulkIdResolver);
				break;
			case TYPE_DECIMAL:
				val = new DecimalValue(attr,fnode);
			}
		}
		if (val == null)
			throw new SchemaException("No value mapped for attribute: "
					+ attr.getPath() + ", type: " + attr.getType());

		return val;
	}
	
	/**
	 * Takes an attribute name and value and attempts to detect the
	 * data type specified in the value. This is used when a SCIM filter is specified with
	 * attribute name not defined in the configured schema. While not declared in the SCIM 
	 * schema, the attribute may still be valid within the backend provider. Will not 
	 * detect BINARY (base64 encoded) data.
	 * @param name A <String> containing the name of the attribute (e.g. "id")
	 * @param value A <String> (e.g. from a URL filter) containing a data value
	 * @return Returns the SCIM Value type constant (e.g. {@link #TYPE_STRING}) detected. 
	 */
	public static String parseValueType(String name, String value) {
		if (value == null)
			return null;
	
		if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
			return TYPE_BOOLEAN;
	
		try {
			Meta.ScimDateFormat.parse(value);
			return TYPE_DATETIME;
		} catch (ParseException e) {
			// Was not a date
		}
			
		try {
			Integer.parseInt(value);
			return TYPE_INTEGER;
		} catch (NumberFormatException e) {
			// was not integer.
		}
		
		try {
			
			if (value.startsWith("/"))
				new URL("http",null,value);
			else
				new URL(value);
			return TYPE_REF;
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		
		return TYPE_STRING;
	}
	
	public static boolean isReturnable(Attribute attr,RequestCtx ctx) {
		return attr.isReturnable(ctx);
	}
	
	/**
	 * This checks to see if an attribute name is returnable. This is a *last resort* method
	 * as it searches the configuration to locate a match. Where possible use the variant
	 * of this method that includews <Schema>.
	 * @param name A String containing the name of the attribute to be checked. Can be a short id, partial path (e.g. User) or full path with schema id.
	 * @param ctx The request context that specifies included/excluded attributes
	 * @return true if the attribute is returnable.
	 */
	public static boolean isReturnable(String name, RequestCtx ctx) {
		    
		Attribute attr = ValueUtil.cfg.findAttribute(name, ctx);
		if (attr != null)
			return isReturnable(attr,ctx);
   
		// Most likely it is an undefined or core attribute (which has a fixed definition). 
		// Treat as returned by default. @See <Attribute>#isReturnable.
		if (ctx == null)
			return true;
		
		Set<String> requested = ctx.getAttrNamesReq();
    	Set<String> excluded = ctx.getExcludedAttrNames();
    	
		if (requested.contains(name)) 
			return true;
		if (requested.isEmpty()) {
			// Item is returnable unless excluded
			if (excluded.isEmpty()) return true;
			return !(excluded.contains(name));
		}
		return false;
	}
	
	public static boolean isReturnable(Schema sch, String name, RequestCtx ctx) {
		if (sch == null)
			return isReturnable(name,ctx);
		Attribute attr = sch.getAttribute(name);
		
		if (attr == null) 
			return false;
		
		return attr.isReturnable(ctx);
	}
	
	/**
	 * This method checks to see if any of an extensions valules are returnable in the current request context.
	 * @param ext An <ExtensionValules> object containing the set of <Value>s for a particular extension.
	 * @param ctx The <RequestCtx> containing the returned and excluded attributes params.
	 * @return true if the provided <ExtensionValues> object has at least 1 returnable value.
	 */
	public static boolean isReturnable(ExtensionValues ext, RequestCtx ctx) {
		Schema sch = ext.getSchema();
		
		//loop through the set of values and check if the attribute is returnable
		for (String s : ext.getValueMap().keySet()) {
			if (isReturnable(sch, s, ctx))
				return true;
		}
		return false;
	}

}
