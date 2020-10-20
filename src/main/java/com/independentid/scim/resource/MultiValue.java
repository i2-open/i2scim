/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015 Phillip Hunt, All Rights Reserved                        *
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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.BadFilterException;
import com.independentid.scim.server.ConflictException;
import com.independentid.scim.server.ScimException;

public class MultiValue extends Value {

	private Vector<Value> values;
	private IBulkIdResolver resolver;

	public MultiValue() {
		this.jtype = JsonNodeType.ARRAY;
		this.values = new Vector<Value>();
		this.resolver = null;
	}

	public MultiValue(Attribute cfg, JsonNode node, IBulkIdResolver bulkIdResolver)
			throws ConflictException, SchemaException, ParseException {
		super(cfg, node);
		this.values = new Vector<Value>();
		this.resolver = bulkIdResolver;
		parseJson(cfg, node);
	}
	
	public MultiValue(Attribute attr, List<Value> vals) {
		this.jtype = JsonNodeType.ARRAY;
		this.values = new Vector<Value>();
		this.values.addAll(vals);
		this.resolver = null;
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
		gen.writeStartArray();
		Iterator<Value> iter = this.values.iterator();
		while (iter.hasNext()) {
			Value val = iter.next();
			val.serialize(gen, ctx);
		}
		gen.writeEndArray();

	}

	@Override
	public void parseJson(Attribute attr, JsonNode node)
			throws ConflictException, SchemaException, ParseException {
		Iterator<JsonNode> iter = node.elements();
		while (iter.hasNext()) {
			JsonNode item = iter.next();
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
		return (Value[]) this.values.toArray(new Value[this.values.size()]);
	}

	public void resetPrimary() {
		// check existing values and ensure primary is not set
		Iterator<Value> iter = this.values.iterator();
		while (iter.hasNext()) {
			Value aval = iter.next();
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
		Iterator<Value> iter = this.values.iterator();
		while (iter.hasNext()) {
			Value val = iter.next();
			if (filter.isMatch(val))
				return val;
		}
		return null;
	}

	public Collection<Value> values() {
		return this.values;
	}

}
