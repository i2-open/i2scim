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

package com.independentid.scim.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.resource.*;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;

/**
 * This builder class is used to construct and manipulate and build {@link ScimResource} objects
 */
public class ResourceBuilder {
    private final ScimResource resource;
    private final i2scimClient client;

    ResourceBuilder(i2scimClient client, String type) {
        this.resource = new ScimResource(client.getSchemaManager());
        this.resource.setResourceType(type);
        this.client = client;
    }

    ResourceBuilder(i2scimClient client, final ScimResource resource) throws ScimException, ParseException {
        this.resource = resource.copy(null);
        this.client = client;
    }

    ResourceBuilder(i2scimClient client, InputStream stream) throws IOException, ScimException, ParseException {
        JsonNode node = JsonUtil.getJsonTree(stream);
        this.client = client;
        this.resource = new ScimResource(client.getSchemaManager(), node, null);
    }

    ResourceBuilder(i2scimClient client, JsonNode jsonDoc) throws ScimException, ParseException {
        this.client = client;

        this.resource = new ScimResource(client.getSchemaManager(), jsonDoc, null);
    }

    public ResourceBuilder withId(String identifier) {
        this.resource.setId(identifier);
        return this;
    }

    /**
     * Adds a String value to the named attribute. Note: if Attribute is multi-valued, the value is added to the set of
     * values, otherwise the existing value is replaced.
     * @param name  The name of the attribute to be added.
     * @param value A String to be added as a value
     * @return The resource builder.
     * @throws SchemaException is thrown when the attribute could not be located or is of the wrong type.
     */
    public ResourceBuilder addStringAttribute(String name, String value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        StringValue val = new StringValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    /**
     * Adds a Boolean value to the named attribute. Note: if Attribute is multi-valued, the value is added to the set of
     * values, otherwise the existing value is replaced.
     * @param name  The name of a Boolean Attribute
     * @param value A boolean used to set its value
     * @return The resource builder.
     * @throws SchemaException is thrown when the attribute could not be located or is of the wrong type.
     */
    public ResourceBuilder addBooleanAttribute(String name, boolean value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        BooleanValue val = new BooleanValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    /**
     * Adds a Date value to the named attribute. Note: if Attribute is multi-valued, the value is added to the set of
     * values, otherwise the existing value is replaced.
     * @param name  The name of a Date Attribute
     * @param value A Date used to set its value
     * @return The resource builder.
     * @throws SchemaException is thrown when the attribute could not be located or is of the wrong type.
     */
    public ResourceBuilder addDateAttribute(String name, Date value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        DateValue val = new DateValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    /**
     * Adds a Decimal value to the named attribute. Note: if Attribute is multi-valued, the value is added to the set of
     * values, otherwise the existing value is replaced.
     * @param name  The name of a Decimal Attribute
     * @param value A BigDecimal used to set its value
     * @return The resource builder.
     * @throws SchemaException is thrown when the attribute could not be located or is of the wrong type.
     */
    public ResourceBuilder addDecimalAttribute(String name, BigDecimal value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        DecimalValue val = new DecimalValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    /**
     * Adds an integer value to the named attribute. Note: if Attribute is multi-valued, the value is added to the set
     * of values, otherwise the existing value is replaced.
     * @param name  The name of an Integer Attribute
     * @param value An integer used to set its value
     * @return The resource builder.
     * @throws SchemaException is thrown when the attribute could not be located or is of the wrong type.
     */
    public ResourceBuilder addIntegerAttribute(String name, int value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        IntegerValue val = new IntegerValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    /**
     * Adds a Binary value to the named attribute. Note: if Attribute is multi-valued, the value is added to the set of
     * values, otherwise the existing value is replaced.
     * @param name  The name of a Binary Attribute
     * @param value An array of bytes (byte[]) used to set its value
     * @return The resource builder.
     * @throws SchemaException is thrown when the attribute could not be located or is of the wrong type.
     */
    public ResourceBuilder addBinaryAttribute(String name, byte[] value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        BinaryValue val = new BinaryValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    /**
     * Adds a Base64 encoded binary value to the named attribute. Note: if Attribute is multi-valued, the value is added
     * to the set of values, otherwise the existing value is replaced.
     * @param name     The name of a Binary Attribute
     * @param b64value A String containing a Base64 encoded value used to set its value
     * @return The resource builder.
     * @throws SchemaException is thrown when the attribute could not be located or is of the wrong type.
     */
    public ResourceBuilder addBinaryAttribute(String name, String b64value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        BinaryValue val = new BinaryValue(attr, b64value);
        this.resource.addValue(val);
        return this;
    }

    /**
     * If the attribute is MultiValued, the ComplexAttribute will be added to the set of values. Otherwise, the complex
     * value replaces the current value.
     * @param val The {@link ComplexValue} to be added
     * @return The current builder context
     * @throws SchemaException if values are not compatible
     */
    public ResourceBuilder addComplexAttribute(ComplexValue val) throws SchemaException {
        this.resource.addValue(val);
        return this;
    }

    /**
     * Adds a MultiValue object to the current resource. Values will be merged if current builder already has values.
     * @param val A {@link MultiValue} object containing one or more values.
     * @return The current builder context.
     * @throws SchemaException If values cannot be merged
     */
    public ResourceBuilder addMultiValueAttribute(MultiValue val) throws SchemaException {
        this.resource.addValue(val);
        return this;
    }

    /**
     * emoves all values of a particular attribute type by name
     * @param name The name of the attribute. May include the full Schema URN or the simple schema name plus colon and
     *             the attribute name (e.g. User:name) to disambiguate names across multiple schemas
     * @return The current builder context.
     * @throws SchemaException If the attribute name cannot be located
     */
    public ResourceBuilder removeAttribute(String name) throws SchemaException {
        Attribute attr = client.getSchemaManager().findAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined.");
        return removeAttribute(attr);
    }

    /**
     * Removes values of a particular attribute type
     * @param attr The {@link Attribute} whose values are to be deleted
     * @return The current builder context.
     */
    public ResourceBuilder removeAttribute(Attribute attr) {
        this.resource.removeValue(attr);
        return this;
    }


    /**
     * Completes the build and returns the {@link ScimResource}
     * @return The constructed {@link ScimResource}
     */
    public ScimResource build() {
        return this.resource;
    }

    /**
     * @return Returns the built {@link ScimResource} as a JSON {@link String}
     */
    public String buildString() {
        return this.resource.toJsonString();
    }

    /**
     * @return A {@link StringEntity} containing the constructed SCIM Resource.
     */
    public HttpEntity buildHttpEntity() {
        return new StringEntity(this.resource.toJsonString(), ScimParams.SCIM_MIME_TYPE);
    }

    /**
     * This method creates a SCIM Resource using the HTTP POST command. The URI for the request is constructed from the
     * {@link ScimResource}.
     * @param params Optional SCIM request modifiers (e.g. attributes)
     * @return A {@link ScimResource} representation of the result returned by the server.
     * @throws ScimException      when an SCIM protocol error occurs
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public ScimResource buildAndCreate(ScimReqParams params) throws IOException, ScimException, URISyntaxException, ParseException {
        if (this.client == null)
            throw new IOException("SCIM client not defined.");
        i2scimResponse resp = client.create(this.resource, params);
        if (resp.hasError()) {
            ScimException e = resp.getException();
            if (e != null)
                throw e;
            else
                throw new ScimException("Message: " + resp.getDetail() + ", type=" + resp.getScimErrorType());
        }

        ScimResource res = resp.next();
        resp.close();
        return res;
    }

    /**
     * This method modifies a SCIM Resource using the HTTP PUT command as per RFC7644. The URI for the request is
     * constructed from the {@link ScimResource}.
     * @param params Optional SCIM request modifiers (e.g. attributes)
     * @return A {@link ScimResource} representation of the result returned by the server.
     * @throws ScimException      when an SCIM protocol error occurs
     * @throws URISyntaxException when an invalid request URL is provided
     * @throws ParseException     when a response is not formatted correctly
     * @throws IOException        due to connection issues
     */
    public ScimResource buildAndPut(ScimReqParams params) throws IOException, ScimException, URISyntaxException, ParseException {
        if (this.client == null)
            throw new IOException("SCIM client not defined.");

        i2scimResponse resp = client.put(this.resource, params);
        if (resp.hasError()) {
            ScimException e = resp.getException();
            if (e != null)
                throw e;
            else
                throw new ScimException("Message: " + resp.getDetail() + ", type=" + resp.getScimErrorType());
        }

        ScimResource res = resp.next();
        resp.close();
        return res;
    }

    /**
     * Returns a builder for ComplexAttribute construction.
     * @param name The attribute name of the ComplexValue to be constructed.
     * @return A ComplexValue.Builder is returned
     */
    public ComplexValue.Builder getComplexBuilder(String name) {
        Attribute attr = client.schemaManager.findAttribute(name, null);
        return ComplexValue.getBuilder(attr);
    }

}
