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
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.IBulkIdResolver;
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

public class ComplexValue extends Value {

    private final LinkedHashMap<Attribute, Value> vals;
    private final IBulkIdResolver resolver;

    public ComplexValue() {
        this.vals = new LinkedHashMap<>();
        this.resolver = null;

    }

    public ComplexValue(@NotNull Attribute attrDef, JsonNode node) throws
            SchemaException, ParseException {
        this(attrDef, node, null);
    }

    public ComplexValue(@NotNull Attribute attrDef, JsonNode node, IBulkIdResolver resolver)
            throws SchemaException, ParseException {
        super(attrDef, node);
        if (attrDef == null)
            throw new SchemaException("Attribute schema is null");
        this.vals = new LinkedHashMap<>();
        this.resolver = resolver;

        this.parseJson(node);

    }

    public ComplexValue(@NotNull Attribute attr, Map<Attribute, Value> vals) throws SchemaException {
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

        /*
        boolean parentRequested = ctx == null || ctx.isAttrRequested(attr);
        // --> whether the parent is returnable should be resolved by the caller (scimresource)
         */

        for (Attribute sAttr : this.vals.keySet()) {
            // if parent is returnable then return the client by normal defaults
            // Check if the sub attribute should be returned based on request ctx
            //if (ValueUtil.isReturnable(sAttr, (parentRequested) ? null : ctx)) {
            if (ValueUtil.isReturnable(sAttr, ctx)) {
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
            val.toJsonNode(node, sAttr.getName());
        }
        parent.set(aname, node);
        return parent;
    }

    @Override
    public void parseJson(JsonNode node)
            throws SchemaException, ParseException {
        if (node == null)
            return; //nothing to do
        Iterator<String> niter = node.fieldNames();
        while (niter.hasNext()) {
            String field = niter.next();
            JsonNode fnode = node.get(field);
            Map<String, Attribute> map = attr.getSubAttributesMap();

            if (map.containsKey(field)) {
                Attribute sattr = map.get(field);
                Value val = ValueUtil
                        .parseJson(null, sattr, fnode, this.resolver);
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

    /**
     * Constructs a builder for a ComplexValue. Normally called from i2scimClient or ResourceBuilder
     * @param schemaManager A handle to {@link SchemaManager}
     * @param attributeName The name of the complex attribute to be constructed
     * @return a builder for ComplexValue
     * @throws SchemaException thrown if attribute is not found or is not a Complex attribute
     */
    public static Builder getBuilder(SchemaManager schemaManager, String attributeName) throws SchemaException {
        Attribute attr = schemaManager.findAttribute(attributeName, null);
        if (attr == null)
            throw new SchemaException("Undefined attribute: " + attributeName);
        return getBuilder(attr);
    }

    /**
     * Constructs a builder for a ComplexValue. Normally called from i2scimClient or ResourceBuilder
     * @param complexAttribute An {@link Attribute} which defines the complex attribute to be constructed
     * @return a builder for ComplexValue
     */
    public static Builder getBuilder(Attribute complexAttribute) {
        return new ComplexValue.Builder(complexAttribute);
    }

    /**
     * Builder to enable construction of Complex attribute Values. May be invoked using i2scimClient or
     * ComplexValue.getBuilder
     */
    public static class Builder {
        private ComplexValue val;
        Attribute attr;

        Builder(@NotNull Attribute complexAttribute) {

            try {
                this.val = new ComplexValue(complexAttribute, (JsonNode) null);
                this.attr = complexAttribute;
            } catch (ParseException | SchemaException ignore) {
                // only happens with JsonNode data
            }
        }

        /**
         * Adds a StringValue to the complex attribute value
         * @param name  the name of the sub-attribute to be added
         * @param value a String value
         * @return the Complex Value builder
         * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong
         *                         type
         */
        public Builder withStringAttribute(String name, String value) throws SchemaException {
            Attribute attr = this.val.getAttribute().getSubAttribute(name);
            if (attr == null)
                throw new SchemaException("Attribute " + name + " is not defined");
            StringValue val = new StringValue(attr, value);
            this.val.addValue(attr, val);
            return this;
        }

        /**
         * Adds a boolean value to the complex attribute value
         * @param name  the name of the sub-attribute to be added
         * @param value a boolean value
         * @return the Complex Value builder
         * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong
         *                         type
         */
        public Builder withBooleanAttribute(String name, boolean value) throws SchemaException {
            Attribute attr = this.val.getAttribute().getSubAttribute(name);
            if (attr == null)
                throw new SchemaException("Attribute " + name + " is not defined");
            BooleanValue val = new BooleanValue(attr, value);
            this.val.addValue(attr, val);
            return this;
        }

        /**
         * Adds a date value to the complex attribute value
         * @param name  the name of the sub-attribute to be added
         * @param value a Date value
         * @return the Complex Value builder
         * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong
         *                         type
         */
        public Builder withDateAttribute(String name, Date value) throws SchemaException {
            Attribute attr = this.val.getAttribute().getSubAttribute(name);
            if (attr == null)
                throw new SchemaException("Attribute " + name + " is not defined");
            DateValue val = new DateValue(attr, value);
            this.val.addValue(attr, val);
            return this;
        }

        /**
         * Adds a decimal value to the complex attribute value
         * @param name  the name of the sub-attribute to be added
         * @param value a BigDecimal value
         * @return the Complex Value builder
         * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong
         *                         type
         */
        public Builder withDecimalAttribute(String name, BigDecimal value) throws SchemaException {
            Attribute attr = this.val.getAttribute().getSubAttribute(name);
            if (attr == null)
                throw new SchemaException("Attribute " + name + " is not defined");
            DecimalValue val = new DecimalValue(attr, value);
            this.val.addValue(attr, val);
            return this;
        }

        /**
         * Adds a Integer value to the complex attribute value
         * @param name  the name of the sub-attribute to be added
         * @param value an int value
         * @return the Complex Value builder
         * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong
         *                         type
         */
        public Builder withIntegerAttribute(String name, int value) throws SchemaException {
            Attribute attr = this.val.getAttribute().getSubAttribute(name);
            if (attr == null)
                throw new SchemaException("Attribute " + name + " is not defined");
            IntegerValue val = new IntegerValue(attr, value);
            this.val.addValue(attr, val);
            return this;
        }

        /**
         * Adds a binary value to the complex attribute value
         * @param name  the name of the sub-attribute to be added
         * @param value a byte[] array containing the binary value
         * @return the Complex Value builder
         * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong
         *                         type
         */
        public Builder withBinaryAttribute(String name, byte[] value) throws SchemaException {
            Attribute attr = this.val.getAttribute().getSubAttribute(name);
            if (attr == null)
                throw new SchemaException("Attribute " + name + " is not defined");
            BinaryValue val = new BinaryValue(attr, value);
            this.val.addValue(attr, val);
            return this;
        }

        /**
         * Adds a binary value to the complex attribute value
         * @param name     the name of the sub-attribute to be added
         * @param b64value a Base64 encoded binary string value
         * @return the Complex Value builder
         * @throws SchemaException thrown if attribute name is not a sub-attribute of the Complex attribute or is wrong
         *                         type
         */
        public Builder withBinaryAttribute(String name, String b64value) throws SchemaException {
            Attribute attr = this.val.getAttribute().getSubAttribute(name);
            if (attr == null)
                throw new SchemaException("Attribute " + name + " is not defined");
            BinaryValue val = new BinaryValue(attr, b64value);
            this.val.addValue(attr, val);
            return this;
        }

        /**
         * Allows a Json object containing one or more attributes to be added to the complex value
         * @param jsonObject A {@link JsonNode} object containing 1 or more attributes
         * @return the Complex Value builder
         * @throws SchemaException thrown if contained attributes not part of the current Complex parent attribute
         * @throws ParseException  if a JSON parsing error occurs
         */
        public Builder withJsonObject(JsonNode jsonObject) throws SchemaException, ParseException {
            if (jsonObject.isArray())
                throw new SchemaException("Arrays not supported. Use MultiValue builder");
            val = new ComplexValue(attr, jsonObject, null);
            return this;
        }

        /**
         * Allows a Json String object containing one or more attributes to be added to the complex value
         * @param jsonString A {@link String} representing a JSON object containing 1 or more attributes
         * @return the Complex Value builder
         * @throws JsonProcessingException occurs when the JSON content of the input string is not valid
         * @throws SchemaException         thrown if contained attributes not part of the current Complex parent
         *                                 attribute
         * @throws ParseException          if a JSON parsing error occurs
         */
        public Builder withJsonObject(String jsonString) throws JsonProcessingException, SchemaException, ParseException {

            JsonNode node = JsonUtil.getJsonTree(jsonString);

            return withJsonObject(node);
        }

        /**
         * @return Builds and returns a ComplexValue object
         */
        public ComplexValue buildComplexValue() {
            return this.val;
        }

        /**
         * @return A collection of Attributes currently assigned in the ComplexValue
         */
        public Collection<Attribute> getAttributes() {
            return this.val.getAttribute().getSubAttributesMap().values();
        }
    }

}

