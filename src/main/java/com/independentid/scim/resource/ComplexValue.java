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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ComplexValue extends Value {

	private final LinkedHashMap<Attribute, Value> vals;
	private final IBulkIdResolver resolver;

	public ComplexValue() {
		this.vals = new LinkedHashMap<>();
		this.resolver = null;
		
	}

	public ComplexValue(@NotNull Attribute attrDef, JsonNode node) throws ConflictException,
			SchemaException, ParseException {
		this(attrDef, node, null);
	}

	public ComplexValue(@NotNull Attribute attrDef, JsonNode node, IBulkIdResolver resolver)
			throws ConflictException, SchemaException, ParseException {
		super(attrDef, node);
		if (attrDef == null)
			throw new SchemaException("Attribute schema is null");
		this.vals = new LinkedHashMap<>();
		this.resolver = resolver;

		this.parseJson(node);

	}
	
	public ComplexValue(@NotNull Attribute attr, Map<Attribute,Value> vals) throws SchemaException {
		super.jtype = JsonNodeType.OBJECT;
		if (attr == null)
			throw new SchemaException("Attribute schema is null");
		this.resolver = null;
		this.vals = new LinkedHashMap<>();
		if (vals != null)
			this.vals.putAll(vals);
		this.attr = attr;
	}

	public void addValue(Attribute attr, Value val) {
		this.vals.put(attr, val);
	}

	public void removeValue(String name) {
		Iterator<Attribute> aiter = vals.keySet().iterator();
		while (aiter.hasNext()) {
			if (aiter.next().getName().equals(name)) {
				aiter.remove();
				break;
			}
		}
	}

	public void removeValue(Attribute attr) {
		if (attr != null)
			vals.remove(attr);
	}

	public int valueSize() {
		return vals.size();
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
		gen.writeStartObject();
		
		//boolean parentRequested = ctx == null || ctx.isAttrRequested(attr);
		// --> whether the parent is returnable should be resolved by the caller (scimresource)

		for (Attribute sAttr : this.vals.keySet()) {


			// if parent is returnable then return the client by normal defaults
			// Check if the sub attribute should be returned based on request ctx
			//if (ValueUtil.isReturnable(sAttr, (parentRequested) ? null : ctx)) {
			if (ValueUtil.isReturnable(sAttr,  ctx)) {
				Value val = this.vals.get(sAttr);
				if (ctx != null && ctx.useEncodedExtensions()) {
					if (sAttr.getName().equalsIgnoreCase("$ref"))
						gen.writeFieldName("href");
				} else
				gen.writeFieldName(sAttr.getName());

				val.serialize(gen, ctx);
			}
		}
		gen.writeEndObject();

	}

	@Override
	public JsonNode toJsonNode(ObjectNode parent, String aname) {
		if (parent == null)
			parent = JsonUtil.getMapper().createObjectNode();

		//Create the object to hold the complex value
		ObjectNode node = JsonUtil.getMapper().createObjectNode();

		for (Attribute sAttr : this.vals.keySet()) {
			Value val = this.vals.get(sAttr);
			val.toJsonNode(node,sAttr.getName());
		}
		parent.set(aname,node);
		return parent;
	}

	@Override
	public void parseJson(JsonNode node)
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
				this.vals.put(sattr, val);
			}

		}

	}

	public Value getValue(String subattrname) {
		Attribute attr = this.attr.getSubAttribute(subattrname);
		if (attr == null) return null;
		return this.vals.get(attr);
	}

	public Value getValue(Attribute attr) {
		if (attr == null) return null;
		return vals.get(attr);
	}

	@Override
	public HashMap<Attribute, Value> getRawValue() {
		return this.vals;
	}

	public boolean isPrimary() {
		Value val = this.getValue("primary");
		if (val == null)
			return false;

		if (val instanceof BooleanValue) {
			BooleanValue bval = (BooleanValue) val;
			return bval.getRawValue();
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
		for (Attribute sname : val.vals.keySet()) {
			this.vals.put(sname, val.getValue(sname.getName()));
		}
	}

	@Override
	public int hashCode() {
		int res = 0;
		for (Value val : this.vals.values())
			res = res + val.hashCode();
		return res;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ComplexValue) {
			ComplexValue obVal = (ComplexValue) obj;
			return (this.hashCode() == obVal.hashCode());
		}
		return false;
	}

	@Override
	public int compareTo(Value o) {
		if (o instanceof ComplexValue) {
			ComplexValue obVal = (ComplexValue) o;
			return (this.toString().compareTo(obVal.toString()));
		}
		throw new ClassCastException("Unable to compare Value types");
	}
}
