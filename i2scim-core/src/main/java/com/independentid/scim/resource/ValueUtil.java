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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;

/**
 * ValueUtil is a general utility to parse JsonNode structures and create the correct Value object class.
 * @author pjdhunt
 */
public class ValueUtil {
    private final static Logger logger = LoggerFactory.getLogger(ValueUtil.class);

    static ConfigMgr cfg = null;
    static SchemaManager smgr = null;

    public static void initialize(ConfigMgr cmgr) {
        cfg = cmgr;
        smgr = cfg.getSchemaManager();
    }

    /**
     * Static method used to parse a {@link JsonNode} object for its appropriate SCIM Value type based on the declared
     * Attribute.
     * @param res            The id of the scim resource being mapped (if available or null)
     * @param attr           The SCIM {@link Attribute} type for the value being parsed
     * @param fnode          A {@link JsonNode} representing the attribute/value node.
     * @param bulkIdResolver This resolver is used for bulk operations where an Identifier may be temporary.
     * @return The parsed {@link Value} instance. See com.independentid.scim.resource package for sub-classes (Complex,
     * String, Boolean, DateTime, Binary, Reference).
     * @throws SchemaException May be thrown by ValueUtil parser.
     * @throws ParseException  May be thrown by ValueUtil parser.
     */
    public static Value parseJson(@NotNull ScimResource res, Attribute attr, JsonNode fnode, IBulkIdResolver bulkIdResolver)
            throws SchemaException, ParseException {
        // TODO Should we treat as string by default when parsing unknown
        // schema?
        if (attr == null)
            throw new SchemaException("Unable to parse Value. Missing Attribute type: "
                    + fnode.toPrettyString());

        Value val = null;
        //logger.debug(attr.toString());
        //logger.debug("Attr: "+attr.getName()+", aType: "+attr.getType()+", nType: "+fnode.getNodeType()+", aMulti: "+attr.isMultiValued());

        if (attr.isMultiValued() && fnode.getNodeType().equals(JsonNodeType.ARRAY)) {
            if (smgr.isVirtualAttr(attr)) {
                // This enables multi-valued virtual attributes. These attributes should extend MultiValue.
                try {
                    val = smgr.constructValue(res, attr, fnode);
                    if (val == null)
                        val = smgr.constructValue(res, attr);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    logger.error("Error mapping virtual attribute " + attr.getName() + ": " + e.getMessage(), e);
                }
            } else
                val = new MultiValue(attr, fnode, bulkIdResolver);
        } else {
            if (smgr.isVirtualAttr(attr)) {
                try {
                    val = smgr.constructValue(res, attr, fnode);
                    if (val == null)
                        val = smgr.constructValue(res, attr);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    logger.error("Error mapping virtual attribute " + attr.getName() + ": " + e.getMessage(), e);
                }
            } else {
                switch (attr.getType().toLowerCase()) {
                    case Attribute.TYPE_String:
                        val = new StringValue(attr, fnode);
                        break;
                    case Attribute.TYPE_Complex:
                        val = new ComplexValue(attr, fnode, null);
                        break;
                    case Attribute.TYPE_Boolean:
                        val = new BooleanValue(attr, fnode);
                        break;
                    case Attribute.TYPE_Date:
                        val = new DateValue(attr, fnode);
                        break;
                    case Attribute.TYPE_Binary:
                        val = new BinaryValue(attr, fnode);
                        break;
                    case Attribute.TYPE_Integer:
                        val = new IntegerValue(attr, fnode);
                        break;
                    case Attribute.TYPE_Reference:
                        val = new ReferenceValue(attr, fnode, bulkIdResolver);
                        break;
                    case Attribute.TYPE_Decimal:
                        val = new DecimalValue(attr, fnode);
                }
            }
        }
        if (val == null)
            throw new SchemaException("No value mapped for attribute: "
                    + attr.getPath() + ", type: " + attr.getType());

        return val;
    }

    /**
     * Takes an attribute name and value and attempts to detect the data type specified in the value. This is used when
     * a SCIM filter is specified with attribute name not defined in the configured schema. While not declared in the
     * SCIM schema, the attribute may still be valid within the backend provider. Will not detect BINARY (base64
     * encoded) data.
     * @param name  A String containing the name of the attribute (e.g. "id")
     * @param value A String (e.g. from a URL filter) containing a data value
     * @return Returns the SCIM Value type constant (e.g. {@link Attribute#TYPE_String}) detected.
     */
    public static String parseValueType(String name, String value) {
        if (value == null)
            return null;

        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
            return Attribute.TYPE_Boolean;

        try {
            Meta.ScimDateFormat.parse(value);
            return Attribute.TYPE_Date;
        } catch (ParseException e) {
            // Was not a date
        }

        try {
            Integer.parseInt(value);
            return Attribute.TYPE_Integer;
        } catch (NumberFormatException e) {
            // was not integer.
        }

        try {
            BigDecimal bval = new BigDecimal(value);
            return Attribute.TYPE_Decimal;
        } catch (NumberFormatException e) {
            // was not decimal
        }

        try {

            if (value.startsWith("/"))
                new URL("http", null, value);
            else
                new URL(value);
            return Attribute.TYPE_Reference;
        } catch (MalformedURLException e) {
            // Not a URL
        }

        // Default to String
        return Attribute.TYPE_String;
    }

    public static boolean isReturnable(Attribute attr, RequestCtx ctx) {
        return attr.isReturnable(ctx);
    }

    /**
     * This checks to see if an attribute name is returnable. This is a *last resort* method as it searches the
     * configuration to locate a match. Where possible use the variant of this method that includews {@link Schema}.
     * @param name A String containing the name of the attribute to be checked. Can be a short id, partial path (e.g.
     *             User) or full path with schema id.
     * @param ctx  The request context that specifies included/excluded attributes
     * @return true if the attribute is returnable.
     */
    public static boolean isReturnable(String name, RequestCtx ctx) {

        Attribute attr = smgr.findAttribute(name, ctx);
        if (attr != null)
            return isReturnable(attr, ctx);

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
            return isReturnable(name, ctx);
        Attribute attr = sch.getAttribute(name);

        if (attr == null)
            return false;

        return attr.isReturnable(ctx);
    }

    /**
     * This method checks to see if any of an extensions values are returnable in the current request context.
     * @param ext An {@link ExtensionValues} object containing the set of {@link Value}s for a particular extension.
     * @param ctx The {@link RequestCtx} containing the returned and excluded attributes params.
     * @return true if the provided ExtensionValues object has at least 1 returnable value.
     */
    public static boolean isReturnable(ExtensionValues ext, RequestCtx ctx) {
        Schema sch = ext.getSchema();

        //loop through the set of values and check if the attribute is returnable
        for (Attribute attr : ext.getValueMap().keySet()) {
            if (attr.isReturnable(ctx))
                return true;  // true if one attribute is returnable
//			if (isReturnable(sch, s, ctx)) return true;
        }
        return false;
    }

    public static void mapVirtualVals(ScimResource resource, Schema schema, Map<Attribute, Value> vals) {
        for (Attribute attr : schema.getAttributes()) {
            Value val = vals.get(attr);
            Value res = mapVirtualValue(resource, attr, val);
            if (res != null)
                vals.put(attr, val);
        }
    }

    public static Value mapVirtualValue(ScimResource resource, Attribute attr, Value val) {
        if (!smgr.isVirtualAttr(attr))
            return null;
        try {
            if (val == null)
                return smgr.constructValue(resource, attr);
            return smgr.constructValue(resource, attr, val);
        } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
            logger.error("Error mapping virtual attribute for " + attr.getName() + ":" + e.getMessage(), e);
        }
        return null;
    }
}
