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

package com.independentid.scim.resource;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.ConflictException;
import com.independentid.scim.server.ScimException;

public class ComplexValue extends Value {

	public LinkedHashMap<String, Value> vals;
	private IBulkIdResolver resolver;
	private Attribute attr;

	public ComplexValue() {
		this.vals = new LinkedHashMap<String, Value>();
		this.resolver = null;
		
	}

	public ComplexValue(Attribute attrDef, JsonNode node) throws ConflictException,
			SchemaException, ParseException {
		this(attrDef, node, null);
		this.attr = attrDef;
	}

	public ComplexValue(Attribute attrDef, JsonNode node, IBulkIdResolver resolver)
			throws ConflictException, SchemaException, ParseException {
		super(attrDef, node);
		this.vals = new LinkedHashMap<String, Value>();
		this.resolver = resolver;
		this.attr = attrDef;
		this.parseJson(attrDef, node);

	}
	
	public ComplexValue(Attribute attr, Map<String,Value> vals) {
		super.jtype = JsonNodeType.OBJECT;
		this.vals = new LinkedHashMap<String,Value>(vals);
		this.attr = attr;
	}

	public void addValue(String name, Value val) {
		this.vals.put(name, val);
	}

	public void removeValue(String name) {
		this.vals.remove(name);
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
		gen.writeStartObject();
		
		boolean parentRequested = (ctx == null)?true:ctx.isAttrRequested(attr);
		
		Iterator<String> iter = this.vals.keySet().iterator();
		while (iter.hasNext()) {
			String field = iter.next();
			Attribute sAttr = this.attr.getSubAttribute(field);

			// if parent is returnable then return the client by normal defaults
			// Check if the sub attribute should be returned based on request ctx
			if (ValueUtil.isReturnable(sAttr, (parentRequested)?null:ctx))
			{
				Value val = this.vals.get(field); 
				if(ctx != null && ctx.useEncodedExtensions()) {
					if(field.equalsIgnoreCase("$ref"))
						field = "href";
				}
				gen.writeFieldName(field);
	
				val.serialize(gen, ctx);
			}
		}
		gen.writeEndObject();

	}

	@Override
	public void parseJson(Attribute attr, JsonNode node)
			throws ConflictException, SchemaException, ParseException {
		Iterator<String> niter = node.fieldNames();
		while (niter.hasNext()) {
			String field = niter.next();
			JsonNode fnode = node.get(field);
			Map<String, Attribute> map = attr.getSubAttributesMap();

			if (map.containsKey(field)) {
				Attribute sattr = map.get(field);
				Value val = ValueUtil
						.parseJson(sattr, fnode, this.resolver);
				this.vals.put(field, val);
			}

		}

	}

	public Value getValue(String subattrname) {
		return this.vals.get(subattrname);
	}

	@Override
	public HashMap<String, Value> getValueArray() {
		return this.vals;
	}

	public boolean isPrimary() {
		Value val = this.getValue("primary");
		if (val == null)
			return false;

		if (val instanceof BooleanValue) {
			BooleanValue bval = (BooleanValue) val;
			return bval.getValueArray().booleanValue();
		}
		return false;
	}

	/**
	 * If the "primary" attribute is set, the value is removed (reset)
	 */
	public void resetPrimary() {
		Value val = this.getValue("primary");
		if (val == null)
			return;

		this.removeValue("primary");
	}

	public void replaceValues(ComplexValue val) {
		this.vals.clear();
		mergeValues(val);
	}

	public void mergeValues(ComplexValue val) {
		Iterator<String> iter = val.vals.keySet().iterator();
		while (iter.hasNext()) {
			String sname = iter.next();
			this.vals.put(sname, val.getValue(sname));
		}
	}

}
