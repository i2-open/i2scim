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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.serializer.ScimSerializer;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;

public class ExtensionValues implements ScimSerializer, IBulkIdTarget {

	private final Schema eSchema;
	//private ConfigMgr cfg;
	private final String sname;
	private final IBulkIdResolver resolver;

	private final LinkedHashMap<Attribute, Value> attrs = new LinkedHashMap<>();
	protected HashSet<Attribute> blockedAttrs = new HashSet<>();

	/**
	 * Creates a value container for Extension Schema Attributes
	 * @param extensionSchema A Schema object containing the definitions for the Extension Schema
	 * @param extNode         A JsonNode object holding the extension attributes
	 * @param bulkIdResolver  A resolver used to resolve temporary IDs in bulkoperations
	 * @throws ConflictException may be thrown by ValueUtil parser
	 * @throws SchemaException   may be thrown by ValueUtil parser
	 * @throws ParseException    may be thrown by ValueUtil parser
	 */
	public ExtensionValues(Schema extensionSchema, JsonNode extNode, IBulkIdResolver bulkIdResolver) throws ConflictException, SchemaException, ParseException {
		this.sname = extensionSchema.getId();
		this.eSchema = extensionSchema;
		this.resolver = bulkIdResolver;
		parseJson(extNode);

	}

	public ExtensionValues(Schema extensionSchema, Map<Attribute, Value> valMap) {
		this.sname = extensionSchema.getId();
		this.eSchema = extensionSchema;
		this.resolver = null;
		this.attrs.putAll(valMap);

	}


	public void parseJson(JsonNode node) throws ConflictException, SchemaException, ParseException {

		//this.eSchema = this.cfg.getSchemaByName(this.sname);

		Attribute[] attrs = this.eSchema.getAttributes();
		for (Attribute attr : attrs) {
			processAttribute(attr, node);
		}

	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException, ScimException {
		serialize(gen, ctx, false);
	}


	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx,
						  boolean forHash) throws IOException, ScimException {

		if (getSize() == 0)
			return;

		// SchemaAlias is used when persistence provider (e.g. Mongo) can't handle urns
		if (ctx != null && ctx.useEncodedExtensions())
			gen.writeFieldName(ScimResource.SCHEMA_EXT_PREFIX + Base64.getEncoder().encodeToString(eSchema.getId().getBytes()));
		else
			gen.writeFieldName(eSchema.getId());
		gen.writeStartObject();

		for (Attribute attr : attrs.keySet()) {
			if (blockedAttrs.contains(attr))
				continue;
			if (ValueUtil.isReturnable(attr, ctx)) {
				gen.writeFieldName(attr.getName());
				Value val = attrs.get(attr);
				val.serialize(gen, ctx);
			}
		}
		gen.writeEndObject();

	}

	@Override
	public JsonNode toJsonNode() {
		ObjectNode parent = JsonUtil.getMapper().createObjectNode();

		for (Attribute attr : attrs.keySet()) {
			Value val = getValue(attr);
			val.toJsonNode(parent, attr.getName());
		}
		return parent;
	}

	public Value getValue(String name) {
		Attribute attr = eSchema.getAttribute(name);
		return getValue(attr);
	}

	public Value getValue(Attribute attr) {
		return this.attrs.get(attr);
	}

	public Map<Attribute, Value> getValueMap() {
		return this.attrs;
	}

	public void removeValue(String name) {
		Attribute attr = eSchema.getAttribute(name);
		removeValue(attr);
	}

	public void removeValue(Attribute attr) {
		this.attrs.remove(attr);
	}

	/**
	 * @return The number of attribute values in the extension
	 */
	public int getSize() {
		return this.attrs.size();
	}

	/**
	 * Adds a value to the extension object. If the attribute is a multi-value, it will add the value.
	 * @param attribute The name of the attribute or sub-attribute to add
	 * @param val       The Value to add
	 * @throws SchemaException occurs when adding an incompatible value.
	 */
	public void addValue(@NotNull Attribute attribute, @NotNull Value val) throws SchemaException {
		Attribute rootAttribute = attribute;
		if (attribute.isChild()) {
			rootAttribute = attribute.getParent();
		}

		if (attribute.isChild()) {
			Value rval = getValue(rootAttribute.getName());
			// If parent was undefined, add it.
			if (rval == null) {
				ComplexValue cval = new ComplexValue();
				cval.addValue(attribute, val);
				attrs.put(rootAttribute, cval);
			}
			if (rval instanceof ComplexValue) {
				// Add the sub attribute value to the parent
				ComplexValue cval = (ComplexValue) rval;
				cval.addValue(attribute, val);
				return;
			}
		}

		// Not a complex sub attribute, just add it.
		if (rootAttribute.isMultiValued()) {
			MultiValue mval = (MultiValue) getValue(rootAttribute);
			if (mval == null) {
				mval = new MultiValue(rootAttribute, new LinkedList<>());
				attrs.put(attribute, mval);
			}
			mval.addValue(val);
			return;
		}

		// Just add the regular attribute
		attrs.put(attribute, val);
	}

	/**
	 * Replaces the existing value object with the new one. If MultiValue, replaces the entire set of values.
	 * @param attribute The definition of the attribute value to be added
	 * @param val       The Value to be added
	 * @throws SchemaException is thrown when attempting to replace an incompatible Value type.
	 */
	public void putValue(@NotNull Attribute attribute, @NotNull Value val) throws SchemaException {
		Attribute rootAttribute = attribute;
		if (attribute.isChild()) {
			rootAttribute = attribute.getParent();
		}

		if (attribute.isChild()) {
			Value rval = getValue(rootAttribute.getName());
			// If parent was undefined, add it.
			if (rval == null) {
				ComplexValue cval = new ComplexValue();
				cval.addValue(attribute, val);
				putValue(rootAttribute, cval);
			}
			if (rval instanceof ComplexValue) {
				// Add the sub attribute value to the parent
				ComplexValue cval = (ComplexValue) rval;
				cval.addValue(attribute, val);
				return;
			}
		}

		// Not a complex sub attribute, just add it.
		if (rootAttribute.isMultiValued()) {
			MultiValue mval = new MultiValue(rootAttribute, new LinkedList<>());

			mval.addValue(val);
			this.attrs.put(attribute, mval);
			return;
		}

		// Just add the regular attribute
		this.attrs.put(attribute, val);
	}

	public Set<Attribute> getAttributeSet() {
		return this.attrs.keySet();
	}

	private void processAttribute(Attribute attr, JsonNode node) throws SchemaException, ParseException, ConflictException {
		JsonNode attrNode = node.get(attr.getName());
		Value val;
		if (attrNode != null) {
			val = ValueUtil.parseJson(null, attr, attrNode, this.resolver);
			this.attrs.put(attr, val);
		}
	}

	public String getSchemaName() {
		return this.sname;
	}

	public Schema getSchema() {
		return this.eSchema;
	}


	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#hasBulkIds()
	 */
	@Override
	public boolean hasBulkIds() {
		for (Value attribute : attrs.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				if (bAttr.hasBulkIds())
					return true;
			}
		}
		return false;
	}


	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getBulkIdsRequired(java.util.List)
	 */
	@Override
	public void getBulkIdsRequired(List<String> bulkList) {
		for (Value attribute : attrs.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getBulkIdsRequired(bulkList);
			}
		}

	}


	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getAttributesWithBulkIdValues(java.util.List)
	 */
	@Override
	public void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs) {
		for (Value attribute : attrs.values()) {
			if (attribute instanceof IBulkIdTarget) {
				IBulkIdTarget bAttr = (IBulkIdTarget) attribute;
				bAttr.getAttributesWithBulkIdValues(bulkIdAttrs);
			}
		}

	}

	public void setBlockedAttrs(HashSet<Attribute> blocked) {
		this.blockedAttrs = blocked;
	}

}
