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
package com.independentid.scim.resource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.*;

public class MultiValue extends Value {

	private final HashSet<Value> values;
	private final IBulkIdResolver resolver;

	public MultiValue() {
		this.jtype = JsonNodeType.ARRAY;
		this.values = new HashSet<>();
		this.resolver = null;
		this.attr = null;
	}

	public MultiValue(@NotNull Attribute attr, JsonNode node, IBulkIdResolver bulkIdResolver)
			throws SchemaException, ParseException {
		super(attr, node);
		if (attr == null)
			throw new SchemaException("Attribute schema is null");
		this.values = new HashSet<>();
		this.resolver = bulkIdResolver;
		parseJson(node);
	}
	
	public MultiValue(@NotNull Attribute attr, List<Value> vals) throws SchemaException {
		if (attr == null)
			throw new SchemaException("Attribute schema is null");
		this.jtype = JsonNodeType.ARRAY;
		this.values = new HashSet<>();
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

	public int size() {
		return this.values.size();
	}

	@Override
	public JsonNode toJsonNode(ObjectNode parent, String aname) {
		if (parent == null)
			parent = JsonUtil.getMapper().createObjectNode();
		ArrayNode anode = parent.putArray(aname);
		switch (attr.getType()) {
			case Attribute.TYPE_String:
				for(Value val: values)
					anode.add((String) val.getRawValue());
				break;

			case Attribute.TYPE_Complex:
				for(Value val: values) {
					anode.add(val.toJsonNode(null,aname).get(aname));
				}
				break;

			case Attribute.TYPE_Boolean:
				for(Value val: values)
					anode.add((Boolean) val.getRawValue());
				break;

			case Attribute.TYPE_Binary:
			case Attribute.TYPE_Date:
			case Attribute.TYPE_Reference:
				for(Value val: values)
					anode.add(val.toString());
				break;

			case Attribute.TYPE_Decimal:
				for(Value val: values)
					anode.add((BigDecimal) val.getRawValue());
				break;
			case Attribute.TYPE_Integer:
				for(Value val: values)
					anode.add((Integer) val.getRawValue());
				break;
		}

		return parent;
	}

	@Override
	public void parseJson(JsonNode node)
			throws SchemaException, ParseException {
		if (node == null)
			return;  //Create an empty attribute.
		if (node.isArray())
			for (JsonNode item : node) {
				if (item.isObject()) {
					parseJsonObject(item);
				}
			}
		else
			parseJsonObject(node);
	}

	protected void parseJsonObject(JsonNode node) throws SchemaException, ParseException {
		Value val = null;
		switch (this.attr.getType().toLowerCase()) {
			case Attribute.TYPE_String:
				val = new StringValue(attr, node);
				break;
			case Attribute.TYPE_Complex:
				val = new ComplexValue(attr, node, null);
				break;
			case Attribute.TYPE_Boolean:
				val = new BooleanValue(attr, node);
				break;
			case Attribute.TYPE_Date:
				val = new DateValue(attr, node);
				break;
			case Attribute.TYPE_Binary:
				val = new BinaryValue(attr, node);
				break;
			case Attribute.TYPE_Integer:
				val = new IntegerValue(attr, node);
				break;
			case Attribute.TYPE_Reference:
				val = new ReferenceValue(attr, node, null);
				break;
			case Attribute.TYPE_Decimal:
				val = new DecimalValue(attr, node);
		}
		if (val != null)
			this.values.add(val);
	}

	@Override
	public Value[] getRawValue() {
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

	public void removeValue(Value val) {
		int hash = val.hashCode();
		Iterator<Value> iter = this.values.iterator();
		while (iter.hasNext()) {
			Value ival = iter.next();
			int ihash = ival.hashCode();
			if (hash == ihash) {
				iter.remove();
				break;
			}
		}
		// For some reason, HashSet.remove wasn't working.
		//this.values.remove(val);
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

	@Override
	public int hashCode() {
		int res = 0;
		for (Value val : values)
			res = res + val.hashCode();
		return res;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MultiValue) {
			MultiValue obVal = (MultiValue) obj;
			return (this.hashCode() == obVal.hashCode());
		}
		return false;
	}

	@Override
	public int compareTo(Value o) {
		if (o instanceof MultiValue) {
			MultiValue obVal = (MultiValue) o;
			return (this.toString().compareTo(obVal.toString()));
		}
		throw new ClassCastException("Unable to compare Value types");
	}

	/**
	 * Constructs a builder for a MultiValue. Normally called from i2scimClient or ResourceBuilder. Note:  often a MultiValue
	 * object is not needed when adding a new Value to an existing MultiValue object (e.g. using withXXXvalue in ResourceBuilder)
	 * @param schemaManager A handle to {@link SchemaManager}
	 * @param attributeName The name of the complex attribute to be constructed
	 * @return a builder for MultiValue
	 * @throws SchemaException thrown if attribute is not found or is not a Complex attribute
	 */
	public static MultiValue.Builder getBuilder(SchemaManager schemaManager, String attributeName) throws SchemaException {
		Attribute attr = schemaManager.findAttribute(attributeName,null);
		if (attr == null)
			throw new SchemaException("Undefined attribute: "+attributeName);
		return getBuilder(attr);
	}

	/**
	 * Constructs a builder for a MultiValue. Normally called from i2scimClient or ResourceBuilder
	 * @param mvAttribute An {@link Attribute} which defines the complex attribute to be constructed
	 * @return a builder for MultiValue
	 */
	public static Builder getBuilder(Attribute mvAttribute) {
		return new MultiValue.Builder(mvAttribute);
	}

	/**
	 * Builder to enable construction of MultiValue Values. May be invoked using i2scimClient or MultiValue.getBuilder
	 */
	public static class Builder {
		private MultiValue val;
		private Attribute attr;

		Builder (Attribute attr) {
			try {
				this.val = new MultiValue(attr, null,null);
				this.attr = attr;
			} catch (SchemaException | ParseException ignore) {
				// won't happen since passing null JsonNode
			}
		}

		/**
		 * Adds a StringValue to the multi-value
		 * @param value a String value
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong type
		 */
		public Builder withStringValue(String value) throws SchemaException {

            if (attr.getType().equalsIgnoreCase(Attribute.TYPE_String)) {
				StringValue val = new StringValue(attr, value);
				this.val.addValue(val);
				return this;
			}
            throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
        }

		/**
		 * Adds a boolean value to the multi-value
		 * @param value a boolean value
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong type
		 */
        public Builder withBooleanAttribute(boolean value) throws SchemaException {
			if (attr.getType().equalsIgnoreCase(Attribute.TYPE_Boolean)) {
				BooleanValue val = new BooleanValue(attr, value);
				this.val.addValue(val);
				return this;
			}
			throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
        }

		/**
		 * Adds a date value to the multi-value
		 * @param value a Date value
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong type
		 */
        public Builder withDateAttribute(Date value) throws SchemaException {
			if (attr.getType().equalsIgnoreCase(Attribute.TYPE_Date)) {
				DateValue val = new DateValue(attr, value);
				this.val.addValue(val);
				return this;
			}
			throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
        }

		/**
		 * Adds a decimal value to the  multi-value
		 * @param value a BigDecimal value
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong type
		 */
        public Builder withDecimalAttribute(BigDecimal value) throws SchemaException {
			if (attr.getType().equalsIgnoreCase(Attribute.TYPE_Decimal)) {
				DecimalValue val = new DecimalValue(attr, value);
				this.val.addValue(val);
				return this;
			}
			throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
        }

		/**
		 * Adds a Integer value to the multi-value
		 * @param value an int value
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong type
		 */
        public Builder withIntegerAttribute(int value) throws SchemaException {
			if (attr.getType().equalsIgnoreCase(Attribute.TYPE_Integer)) {
				IntegerValue val = new IntegerValue(attr, value);
				this.val.addValue(val);
				return this;
			}
			throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
        }

		/**
		 * Adds a binary value to the  multi-value
		 * @param value a byte[] array containing the binary value
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong type
		 */
        public Builder withBinaryAttribute(byte[] value) throws SchemaException {
			if (attr.getType().equalsIgnoreCase(Attribute.TYPE_Binary)) {
				BinaryValue val = new BinaryValue(attr, value);
				this.val.addValue(val);
				return this;
			}
			throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
        }

		/**
		 * Adds a binary value to the multi-value
		 * @param b64value a Base64 encoded binary string value
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong type
		 */
        public Builder withBinaryAttribute(String b64value) throws SchemaException {
			if (attr.getType().equalsIgnoreCase(Attribute.TYPE_Binary)) {
				BinaryValue val = new BinaryValue(attr, b64value);
				this.val.addValue(val);
				return this;
			}
			throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
        }

		/**
		 * Allows a ComplexValue to be added to the multi-value
		 * @param val A {@link ComplexValue} object containing 1 or more attributes
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if contained attributes not part of the current Complex parent attribute
		 */
        public Builder withComplexValue(ComplexValue val ) throws SchemaException {
			if (attr.getType().equalsIgnoreCase(Attribute.TYPE_Complex)) {
				this.val.addValue(val);
				return this;
			}
			throw new SchemaException("Invalid type requested. Attribute "+attr.getName()+" is of type: "+attr.getType());
		}

		/**
		 * Allows a Json object containing one or more attributes to be added to the complex value
		 * @param jsonObject A {@link JsonNode} object containing 1 or more attributes
		 * @return the  Multi-Value builder
		 * @throws SchemaException thrown if contained attributes not part of the current Complex parent attribute
		 * @throws ParseException if a JSON parsing error occurs
		 */
		public Builder withJson(JsonNode jsonObject) throws SchemaException, ParseException {

			// to allow withJson to be called multiple times, we will add one to the existing
			MultiValue parsedVals = new MultiValue(attr,jsonObject,null);
			this.val.values.addAll(parsedVals.values);
			return this;
		}

		/**
		 * Allows a Json String object containing one or more attributes to be added to the multi-value
		 * @param jsonString A {@link String} representing a JSON object containing 1 or more attributes
		 * @return the MultiValue builder
		 * @throws SchemaException thrown if contained attributes not part of the current Complex parent attribute
		 * @throws ParseException if a JSON parsing error occurs
		 */
		public Builder withJsonString(String jsonString) throws JsonProcessingException, SchemaException, ParseException {
			JsonNode node = JsonUtil.getJsonTree(jsonString);
			return withJson(node);
		}

		/**
		 * @return Builds and returns a MultiValue value instance
		 */
		public MultiValue buildMultiValue() { return this.val; }
	}

}
