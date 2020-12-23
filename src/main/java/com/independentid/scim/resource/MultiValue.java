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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import org.mvel2.optimizers.impl.refl.nodes.ArrayAccessorNest;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

public class MultiValue extends Value {

	private final Vector<Value> values;
	private final IBulkIdResolver resolver;
	private final Attribute attr;

	public MultiValue() {
		this.jtype = JsonNodeType.ARRAY;
		this.values = new Vector<>();
		this.resolver = null;
		this.attr = null;
	}

	public MultiValue(@NotNull Attribute attr, JsonNode node, IBulkIdResolver bulkIdResolver)
			throws ConflictException, SchemaException, ParseException {
		super(attr, node);
		if (attr == null)
			throw new SchemaException("Attribute schema is null");
		this.values = new Vector<>();
		this.resolver = bulkIdResolver;
		parseJson(attr, node);
		this.attr = attr;
	}
	
	public MultiValue(@NotNull Attribute attr, List<Value> vals) throws SchemaException {
		if (attr == null)
			throw new SchemaException("Attribute schema is null");
		this.jtype = JsonNodeType.ARRAY;
		this.values = new Vector<>();
		this.values.addAll(vals);
		this.resolver = null;
		this.attr = attr;
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
		gen.writeStartArray();
		for (Value val : this.values) {
			val.serialize(gen, ctx);
		}
		gen.writeEndArray();

	}

	@Override
	public JsonNode toJsonNode(ObjectNode parent, String aname) {
		ArrayNode anode = null;
		if (parent == null)
			parent = JsonUtil.getMapper().createObjectNode();
		anode = parent.putArray(aname);
		switch (attr.getType()) {
			case ValueUtil.TYPE_BOOLEAN:
				for(Value val: values)
					anode.add((Boolean) val.getValueArray());
				break;

			case ValueUtil.TYPE_BINARY:
			case ValueUtil.TYPE_DATETIME:
			case ValueUtil.TYPE_REF:
				for(Value val: values)
					anode.add(val.toString());
				break;

			case ValueUtil.TYPE_STRING:
				for(Value val: values)
					anode.add((String) val.getValueArray());
				break;

			case ValueUtil.TYPE_DECIMAL:
				for(Value val: values)
					anode.add((BigDecimal) val.getValueArray());
				break;
			case ValueUtil.TYPE_INTEGER:
				for(Value val: values)
					anode.add((Integer) val.getValueArray());
				break;
			case ValueUtil.TYPE_COMPLEX:
				for(Value val: values) {
					anode.add(val.toJsonNode(null,aname).get(aname));
				}
				break;
		}

		return parent;
	}

	@Override
	public void parseJson(Attribute attr, JsonNode node)
			throws ConflictException, SchemaException, ParseException {

		for (JsonNode item : node) {
			if (item.isObject()) {
				ComplexValue val = new ComplexValue(attr, item, this.resolver);
				if (val.isPrimary())
					this.resetPrimary();
				this.values.add(val);
			}
		}

	}

	@Override
	public Value[] getValueArray() {
		// TODO Auto-generated method stub
		return this.values.toArray(new Value[0]);
	}

	public void resetPrimary() {
		// check existing values and ensure primary is not set
		for (Value aval : this.values) {
			if (aval instanceof ComplexValue) {
				((ComplexValue) aval).resetPrimary();
			}
		}

	}

	public void addValue(Value val) {
		if (val instanceof ComplexValue) {
			ComplexValue cval = (ComplexValue) val;
			if (cval.isPrimary())
				this.resetPrimary();
		}
		this.values.add(val);
	}

	public void addAll(Collection<Value> vals) {
		this.values.addAll(vals);
	}

	public void removeValueAt(int index) {
		this.values.remove(index);
	}

	public void removeValue(Value val) {
		this.values.remove(val);
	}

	public void replaceValueAt(int index, Value val) {
		if (val instanceof ComplexValue) {
			if (((ComplexValue) val).isPrimary())
				this.resetPrimary();
		}
		this.values.set(index, val);
	}

	public Value getMatchValue(Filter filter) throws BadFilterException {
		for (Value val : this.values) {
			if (filter.isMatch(val))
				return val;
		}
		return null;
	}

	public Collection<Value> values() {
		return this.values;
	}

}
