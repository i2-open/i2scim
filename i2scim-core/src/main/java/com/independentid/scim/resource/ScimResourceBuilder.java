/*
 * Copyright (c) 2021.
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
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.ScimParams;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Date;

public class ScimResourceBuilder {
    private final ScimResource resource;

    ScimResourceBuilder(SchemaManager schemaManager, String type) {
        this.resource = new ScimResource(schemaManager);
        this.resource.setResourceType(type);
    }

    ScimResourceBuilder(SchemaManager schemaManager, InputStream stream) throws IOException, ScimException, ParseException {
        JsonNode node = JsonUtil.getJsonTree(stream);
        this.resource = new ScimResource(schemaManager, node, null);
    }

    public ScimResourceBuilder withId(String identifier) {
        this.resource.setId(identifier);
        return this;
    }

    public ScimResourceBuilder withStringAttribute(String name, String value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        StringValue val = new StringValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    public ScimResourceBuilder withBooleanAttribute(String name, boolean value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        BooleanValue val = new BooleanValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    public ScimResourceBuilder withDateAttribute(String name, Date value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        DateValue val = new DateValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    public ScimResourceBuilder withDecimalAttribute(String name, BigDecimal value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        DecimalValue val = new DecimalValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    public ScimResourceBuilder withIntegerAttribute(String name, int value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        IntegerValue val = new IntegerValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    public ScimResourceBuilder withBinaryAttribute(String name, byte[] value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        BinaryValue val = new BinaryValue(attr, value);
        this.resource.addValue(val);
        return this;
    }

    public ScimResourceBuilder withBinaryAttribute(String name, String b64value) throws SchemaException {
        Attribute attr = this.resource.getAttribute(name, null);
        if (attr == null)
            throw new SchemaException("Attribute " + name + " is not defined");
        BinaryValue val = new BinaryValue(attr, b64value);
        this.resource.addValue(val);
        return this;
    }

    public ScimResourceBuilder withComplexAttribute(ComplexValue val) throws SchemaException {

        this.resource.addValue(val);
        return this;
    }

    public ScimResource build() {
        return this.resource;
    }

    public String buildString() { return this.resource.toJsonString(); }

    public HttpEntity buildHttpEntity() {
        return new StringEntity(this.resource.toJsonString(), ScimParams.SCIM_MIME_TYPE);

    }

}
