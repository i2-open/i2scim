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

package com.independentid.scim.protocol;

import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.resource.*;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import io.smallrye.mutiny.Multi;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.regex.Pattern;

public class AttributeFilter extends Filter {
    public final static String FILTEROP_EQ = "eq";
    public final static String FILTEROP_NE = "ne";
    public final static String FILTEROP_CONTAINS = "co";
    public final static String FILTEROP_STARTSWITH = "sw";
    public final static String FILTEROP_ENDSWITH = "ew";
    public final static String FILTEROP_GREATER = "gt";
    public final static String FILTEROP_LESS = "lt";
    public final static String FILTEROP_GREATEROREQUAL = "ge";
    public final static String FILTEROP_LESSOREQUAL = "le";
    public final static String FILTEROP_PRESENCE = "pr";

    public final static List<String> valid_ops =
            Arrays.asList(FILTEROP_EQ, FILTEROP_NE,
                    FILTEROP_CONTAINS, FILTEROP_PRESENCE,
                    FILTEROP_STARTSWITH, FILTEROP_ENDSWITH,
                    FILTEROP_GREATER, FILTEROP_LESS,
                    FILTEROP_GREATEROREQUAL, FILTEROP_LESSOREQUAL);
    private Attribute parentAttr;

    private Attribute attr;

    private final String compOp;

    private String valString;

    private Object val;

    public AttributeFilter(String attr, String cond, String value, RequestCtx ctx, SchemaManager schemaManager) throws BadFilterException {
        this(attr, cond, value, null,ctx , schemaManager);
    }

    public AttributeFilter(String aname, @NotNull String cond, String value, String parentAttr, RequestCtx ctx, SchemaManager schemaManager) throws BadFilterException {
        super();
        smgr = schemaManager;
        if (parentAttr == null)
            this.parentAttr = null;
        else
            this.parentAttr = smgr.findAttribute(parentAttr, ctx);

        if (this.parentAttr != null) {
            this.attr = this.parentAttr.getSubAttribute(aname);
        } else
            this.attr = smgr.findAttribute(aname, ctx);

        if (this.attr == null) {
            // If no attribute check if it is common schema or just create a placeholder attribute definition
            this.attr = new Attribute(aname);
            this.attr.setType(Attribute.TYPE_String);
            if (aname.equalsIgnoreCase("id")
                    || aname.equalsIgnoreCase("name")
                    || aname.equalsIgnoreCase("description"))
                this.attr.setCaseExact(true);

            else if (aname.equalsIgnoreCase("meta")) {
                this.attr.setType(Attribute.TYPE_Complex);

            } else
                this.attr.setType(ValueUtil.parseValueType(aname, value));

        } else if (this.attr.isChild() && this.parentAttr == null)
            this.parentAttr = this.attr.getParent();
        if (this.attr.getType().equals(Attribute.TYPE_Complex)) {
            // when just a parent attribute is specified, the value attribute is used.
            Attribute vAttr = this.attr.getSubAttribute("value");
            if (vAttr != null) {
                this.parentAttr = this.attr;
                this.attr = vAttr;
            }
        }

        if (this.attr == null)
            throw new BadFilterException("Unable to parse a valid attribute or attribute was null.");

        this.compOp = cond.toLowerCase();

        if (!valid_ops.contains(this.compOp))
            throw new BadFilterException("Invalid comparison operator detected: "+cond);

        //this.val = value;
        this.valString = value;

        if (value == null) {
            // Set null value object for presence filter cases
            this.val = null;
            if (!cond.equalsIgnoreCase(FILTEROP_PRESENCE) &&
                    !(this.attr.isMultiValued() || this.attr.getType().equals(Attribute.TYPE_Complex)))
                throw new BadFilterException("Invalid attribute filter; missing comparison value");
        } else {
            String type = this.attr.getType();
            switch (type.toLowerCase()) {
                case ValueUtil.TYPE_BINARY:
                    this.val = value.getBytes();
                    break;

                case ValueUtil.TYPE_BOOLEAN:
                    this.val = Boolean.parseBoolean(value);
                    break;

                case ValueUtil.TYPE_DATETIME:
                    this.val = value;

                    break;

                case ValueUtil.TYPE_STRING:
                    if (value.startsWith("\"") &&
                            value.endsWith("\""))
                        this.val = value.substring(1, value.length() - 1);
                    else
                        this.val = value;
                    this.valString = (String) this.val;
                    break;

            }
        }
    }

    public Attribute getAttribute() {
        return this.attr;
    }

    public String getOperator() {
        return this.compOp;
    }

    public String getValueType() {
        return this.attr.getType();
    }

    public Object getValue() {
        return this.val;
    }

    @Override
    protected void getFilterAttributes(Set<Attribute> attrSet) {
        attrSet.add(attr);
    }

    public byte[] getBinary() {
        if (this.val instanceof byte[])
            return (byte[]) this.val;

        return null;
    }

    public Date getDate() {
        if (this.val instanceof Date)
            return (Date) this.val;
        return null;
    }

    public String getString() {
        if (this.val instanceof String)
            return (String) this.val;
        return null;
    }

    public Boolean getBoolean() {
        if (this.val instanceof Boolean) {
            return (Boolean) this.val;
        }
        return null;
    }

    public Integer getInt() {
        if (this.val instanceof Integer)
            return (Integer) val;
        return null;
    }

    /**
     * @return The filter excluding the parent attribute as it is not needed in ValuePath filters
     */
    public String toValuePathString() {
        StringBuilder buf = new StringBuilder();
        buf.append(this.attr.getName());
        return addFilterExpr(buf);
    }

    private String addFilterExpr(StringBuilder buf) {
        buf.append(' ').append(this.compOp);
        if (!this.compOp.equals(FILTEROP_PRESENCE)) {
            String sval = this.val.toString();
            if (sval.contains(" "))
                buf.append(" \"").append(sval).append("\"");
            else
                buf.append(' ').append(sval);
        }
        return buf.toString();
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
		
        if (this.parentAttr != null)
            buf.append(this.parentAttr.getName())
                    .append(".").append(this.attr.getName());
        else
            buf.append(this.attr.getName());
        return addFilterExpr(buf);
    }

    public String toPathString() {
        StringBuilder buf = new StringBuilder();
		
		/*
		if (this.parentAttr == null)
			buf.append(this.attr.getPath());
		else
			buf.append(this.attr.getName());
			*/
        buf.append(this.attr.getPath());
        return addFilterExpr(buf);
    }


    /**
     * @return Returns the value as originally specified in the filter (no conversion)
     */
    public String asString() {
        return this.valString;
    }

    /**
     * @return Escapes the string to ensure regex characters are not interpreted
     */
    public String asQuotedString() {
        return Pattern.quote(this.valString);
    }

    public boolean isMatch(Value matchVal) throws BadFilterException {
        Value value = matchVal;

        if (compOp.equals(AttributeFilter.FILTEROP_PRESENCE))
            return value != null;

        if (value instanceof ComplexValue) {
            // locate the sub-attribute value that is to be matched.
            ComplexValue cval = (ComplexValue) value;

            value = cval.getValue(attr);
        }

        switch (attr.getType().toLowerCase()) {

            case Attribute.TYPE_String: {
                if (value == null)
                    value = new StringValue(attr,"");
                if (value instanceof MultiValue) {
                    MultiValue mval = (MultiValue) value;
                    for (Value aval : mval.getValueArray()) {
                        if (isMatch(aval))
                            return true;
                    }
                    return false;
                }
                String val = ((StringValue) value).getValueArray();
                switch (compOp) {

                    case AttributeFilter.FILTEROP_EQ: {
                        if (!attr.getCaseExact()) {
                            // do case inexact regex
                            return val.equalsIgnoreCase(valString);
                        } else
                            return val.equals(valString);
                    }

                    case AttributeFilter.FILTEROP_NE:
                        if (!attr.getCaseExact())
                            return !val.equalsIgnoreCase(valString);
                        else
                            return !val.equals(valString);

                    case AttributeFilter.FILTEROP_CONTAINS: {
                        if (attr.getCaseExact())
                            return val.contains(valString);
                        else
                            return val.toLowerCase().contains(valString.toLowerCase());
                    }

                    case AttributeFilter.FILTEROP_STARTSWITH: {
                        if (attr.getCaseExact())
                            return val.startsWith(valString);
                        else
                            return val.toLowerCase().startsWith(valString.toLowerCase());
                    }

                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        if (attr.getCaseExact())
                            return val.endsWith(valString);
                        else
                            return val.toLowerCase().endsWith(valString.toLowerCase());
                    }

                    case AttributeFilter.FILTEROP_GREATER:
                        if (attr.getCaseExact())
                            return val.compareTo(valString) > 0;
                        return val.compareToIgnoreCase(valString) > 0;

                    case AttributeFilter.FILTEROP_LESS:
                        if (attr.getCaseExact())
                            return val.compareTo(valString) < 0;
                        return val.compareToIgnoreCase(valString) < 0;

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                        if (attr.getCaseExact())
                            return val.compareTo(valString) >= 0;
                        return val.compareToIgnoreCase(valString) >= 0;

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        if (attr.getCaseExact())
                            return val.compareTo(valString) < 1;
                        return val.compareToIgnoreCase(valString) < 1;
                }

            }

            case Attribute.TYPE_Boolean: {
                assert value != null;
                Boolean val = ((BooleanValue) value).getValueArray();
                switch (getOperator()) {

                    case AttributeFilter.FILTEROP_EQ:
                        return val.equals(getBoolean());

                    case AttributeFilter.FILTEROP_NE:
                        return !val.equals(getBoolean());

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH:
                    case AttributeFilter.FILTEROP_GREATER:
                    case AttributeFilter.FILTEROP_LESS:
                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        throw new BadFilterException("Filter operator not supported with boolean attributes.");

                }

            }

            case Attribute.TYPE_Complex: {
                throw new BadFilterException("Complex attributes may not be used in a comparison filter without a sub-attribute");
            }

            case Attribute.TYPE_Date: {
                assert value != null;
                Date val = ((DateValue) value).getDateValue();
                switch (getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        return val.equals(getDate());
                    }

                    case AttributeFilter.FILTEROP_NE:
                        return !val.equals(getDate());

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException("Filter operator not supported with date attributes.");
                    }

                    case AttributeFilter.FILTEROP_GREATER:
                        return val.compareTo(getDate()) > 0;

                    case AttributeFilter.FILTEROP_LESS:
                        return val.compareTo(getDate()) < 0;

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                        return val.compareTo(getDate()) > -1;

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        return val.compareTo(getDate()) < 1;

                }

            }

            case Attribute.TYPE_Number: {
                assert value != null;
                Integer val = ((IntegerValue) value).getValueArray();
                switch (getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        return val.equals(getInt());
                    }

                    case AttributeFilter.FILTEROP_NE:
                        return !val.equals(getInt());

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH:
                        throw new BadFilterException("Filter operator not supported with number attributes.");

                    case AttributeFilter.FILTEROP_GREATER:
                        return val.compareTo(getInt()) > 0;

                    case AttributeFilter.FILTEROP_LESS:
                        return val.compareTo(getInt()) < 0;

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                        return val.compareTo(getInt()) >= 0;

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        return val.compareTo(getInt()) < 1;

                }

            }

        }

        return true;
    }

    public boolean isMatch(ScimResource res) throws BadFilterException {

        Value value = res.getValue(attr);
        return this.isMatch(value);
    }

}
