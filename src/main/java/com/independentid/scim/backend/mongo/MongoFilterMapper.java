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

package com.independentid.scim.backend.mongo;

import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.mongodb.client.model.Filters;

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.AttributeFilter;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.LogicFilter;
import com.independentid.scim.protocol.PrecedenceFilter;
import com.independentid.scim.protocol.ValuePathFilter;
import com.independentid.scim.schema.Attribute;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;

import java.math.BigDecimal;


public class MongoFilterMapper {

    public static Bson mapFilter(Filter filter, boolean negate, boolean isValPath)
            throws ScimException, BackendException {

        if (filter instanceof AttributeFilter)
            return MongoFilterMapper.mapFilter((AttributeFilter) filter, negate, isValPath);

        if (filter instanceof LogicFilter)
            return MongoFilterMapper.mapFilter((LogicFilter) filter, negate, isValPath);

        if (filter instanceof ValuePathFilter)
            return MongoFilterMapper.mapFilter((ValuePathFilter) filter, negate);

        if (filter instanceof PrecedenceFilter)
            return MongoFilterMapper.mapFilter((PrecedenceFilter) filter, negate, isValPath);

        throw new BackendException(Messages.getString("FilterMapper.0") //$NON-NLS-1$
                + filter.getClass().getCanonicalName());
    }

    private static Bson mapStringType(String aname, AttributeFilter filter,boolean negate) {
        Bson obj = null;
        switch (filter.getOperator()) {

            case AttributeFilter.FILTEROP_EQ:
                if (filter.getAttribute().getCaseExact())
                    obj = Filters.eq(aname, filter.asString());
                else
                    obj = Filters.regex(aname,"^" + filter.asQuotedString() + "$","i");

                if (negate)
                    obj = Filters.not(obj);
                break;

            case AttributeFilter.FILTEROP_NE:
                if (filter.getAttribute().getCaseExact())
                    obj = Filters.eq(aname, filter.asString());
                else
                    obj = Filters.regex(aname,"^" + filter.asQuotedString() + "$","i");

                if (!negate)
                    obj = Filters.not(obj);
                break;

            case AttributeFilter.FILTEROP_CONTAINS:

                if (filter.getAttribute().getCaseExact())
                    obj = Filters.regex(aname,".*"+filter.asQuotedString()+".*");
                else
                    obj = Filters.regex(aname,".*"+filter.asQuotedString()+".*","i");
                if (negate)
                    obj = Filters.not(obj);
                break;

            case AttributeFilter.FILTEROP_STARTSWITH:

                if (filter.getAttribute().getCaseExact())
                    obj = Filters.regex(aname,"^" + filter.asQuotedString()+".*");
                else
                    obj = Filters.regex(aname,"^" + filter.asQuotedString()+".*","i");

                if (negate)
                    obj = Filters.not(obj);
                break;


            case AttributeFilter.FILTEROP_ENDSWITH:

                if (filter.getAttribute().getCaseExact())
                    obj = Filters.regex(aname,".*"+filter.asQuotedString()+"$");
                else
                    obj = Filters.regex(aname,".*"+filter.asQuotedString()+"$","i");

                if (negate)
                    obj = Filters.not(obj);
                break;


            case AttributeFilter.FILTEROP_PRESENCE:
                obj = Filters.exists(aname);
                if (negate)
                    obj = Filters.not(obj);
                break;

            case AttributeFilter.FILTEROP_GREATER:

                if (negate)
                    obj = Filters.lte(aname, filter.asString());
                else
                    obj = Filters.gt(aname,filter.asString());
                break;

            case AttributeFilter.FILTEROP_LESS:
                if (negate)
                    obj = Filters.gte(aname,filter.asString());
                else
                    obj = Filters.lt(aname,filter.asString());
                break;

            case AttributeFilter.FILTEROP_GREATEROREQUAL:
                if (negate)
                    obj = Filters.lt(aname,filter.asString());
                else
                    obj = Filters.gte(aname,filter.asString());
                break;

            case AttributeFilter.FILTEROP_LESSOREQUAL:
                if (negate)
                    obj = Filters.gt(aname,filter.asString());
                else
                    obj = Filters.lte(aname,filter.asString());
                break;
        }
        return obj;
    }

    public static Bson mapFilter(AttributeFilter filter, boolean negate, boolean isValPath)
            throws BadFilterException {
        Bson obj = null;
        Attribute attr = filter.getAttribute();
        String aname;
        if (isValPath)
            aname = attr.getName();
        else
            aname = attr.getRelativePath();
        if (aname.contains("$ref"))
            aname = aname.replace("$ref","href");

        if (filter.isExtensionAttribute()) {
            // In order for the mongo query to work, the extensionId object has to be added to the path.
            String extensionIdPrefix = MongoMapUtil.mapExtensionId(attr.getSchema());
            aname = extensionIdPrefix + "." + aname;
        }

        //String aname = attr.getRelativePath();

        switch (attr.getType()) {

            case Attribute.TYPE_Reference:
            case Attribute.TYPE_String:
                return mapStringType(aname,filter,negate);


            case Attribute.TYPE_Binary:
               // Because we can only compare the encoded value, we can treat a filter as string
                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ:
                        obj = Filters.eq(aname, filter.getBinary());
                        if (negate)
                            obj = Filters.not(obj);
                        break;

                    case AttributeFilter.FILTEROP_NE:

                            obj = Filters.eq(aname, filter.getBinary());

                        if (!negate)
                            obj = Filters.not(obj);
                        break;

                    case AttributeFilter.FILTEROP_PRESENCE:
                        obj = Filters.exists(aname);
                        if (negate)
                            obj = Filters.not(obj);
                        break;

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH:
                    case AttributeFilter.FILTEROP_GREATER:
                    case AttributeFilter.FILTEROP_LESS:
                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.1")); //$NON-NLS-1$
                }
                return obj;

            case Attribute.TYPE_Boolean: {
                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        if (negate)
                            obj = Filters.eq(aname,!filter.getBoolean());
                        else
                            obj = Filters.eq(aname,filter.getBoolean());
                        break;
                    }

                    case AttributeFilter.FILTEROP_NE:
                        if (negate)
                            obj = Filters.ne(aname,!filter.getBoolean());
                        else
                            obj = Filters.ne(aname,filter.getBoolean());

                        break;

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.1")); //$NON-NLS-1$
                    }

                    case AttributeFilter.FILTEROP_PRESENCE:
                        obj = Filters.exists(aname);
                        if (negate)
                            obj = Filters.not(obj);
                        break;

                    case AttributeFilter.FILTEROP_GREATER:
                    case AttributeFilter.FILTEROP_LESS:
                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.31")); //$NON-NLS-1$
                }
                return obj;
            }

            case Attribute.TYPE_Complex: {
                // without a sub attribute specified, use the default "value" sub-attribute
                aname = aname + ".value";
                return mapStringType(aname,filter,negate);
            }

            case Attribute.TYPE_Date: {

                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        if (negate)
                            obj = Filters.ne(aname,filter.getDate());

                        else
                            obj = Filters.eq(aname, filter.getDate());
                        break;
                    }

                    case AttributeFilter.FILTEROP_NE:
                        if (negate)
                            obj = Filters.eq(aname, filter.getDate());
                        else
                            obj = Filters.ne(aname, filter.getDate()); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.38")); //$NON-NLS-1$
                    }

                    case AttributeFilter.FILTEROP_PRESENCE:
                        if (negate)
                            obj = Filters.not(Filters.exists(aname));
                        else
                            obj = Filters.exists(aname);
                        break;

                    case AttributeFilter.FILTEROP_GREATER:
                        if (negate)
                            obj = Filters.lte(aname,filter.getDate());
                        else
                           obj = Filters.gt(aname,filter.getDate());
                        break;

                    case AttributeFilter.FILTEROP_LESS:
                        if (negate)
                            obj = Filters.gte(aname,filter.getDate());
                        else
                            obj = Filters.lt(aname,filter.getDate());
                        break;

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                        if (negate)
                            obj = Filters.lt(aname,filter.getDate());
                        else
                            obj = Filters.gte(aname,filter.getDate());
                        break;

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        if (negate)
                            obj = Filters.gt(aname,filter.getDate());
                        else
                            obj = Filters.lte(aname,filter.getDate());
                        break;
                }
                return obj;
            }

            case Attribute.TYPE_Decimal:
                switch (filter.getOperator()) {
                    case AttributeFilter.FILTEROP_EQ:
                        try {
                            if (negate)
                                obj = Filters.ne(aname, new Decimal128(filter.getDecimal()));
                            else
                                obj = Filters.eq(aname, new Decimal128(filter.getDecimal()));
                        } catch (NumberFormatException e) {
                            throw new BadFilterException("Invalid decimal filter detected: " + e.getLocalizedMessage());
                        }
                        return obj;
                    case AttributeFilter.FILTEROP_NE:
                        try {
                            if (negate)
                                obj = Filters.eq(aname, new Decimal128(filter.getDecimal()));
                            else
                                obj = Filters.ne(aname, new Decimal128(filter.getDecimal()));
                        } catch (NumberFormatException e) {
                            throw new BadFilterException("Invalid decimal filter detected: " + e.getLocalizedMessage());
                        }
                        return obj;

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.48")); //$NON-NLS-1$
                    }
                    case AttributeFilter.FILTEROP_PRESENCE:
                        if (negate)
                            obj = Filters.not(Filters.exists(aname));
                        else
                            obj = Filters.exists(aname);
                        break;

                    case AttributeFilter.FILTEROP_GREATER:
                        if (negate)
                            obj = Filters.lte(aname, new Decimal128(filter.getDecimal()));
                        else
                            obj = Filters.gt(aname, new Decimal128(filter.getDecimal()));
                        break;

                    case AttributeFilter.FILTEROP_LESS:
                        if (negate)
                            obj = Filters.gte(aname, new Decimal128(filter.getDecimal()));
                        else
                            obj = Filters.lt(aname, new Decimal128(filter.getDecimal()));
                        break;

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                        if (negate)
                            obj = Filters.lt(aname, new Decimal128(filter.getDecimal()));
                        else
                            obj = Filters.gte(aname, new Decimal128(filter.getDecimal()));
                        break;

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        if (negate)
                            obj = Filters.gt(aname, new Decimal128(filter.getDecimal()));
                        else
                            obj = Filters.lte(aname, new Decimal128(filter.getDecimal()));
                        break;
                }
                return obj;


            case Attribute.TYPE_Integer: {

                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        if (negate)
                            obj = Filters.ne(aname,filter.getInt());
                        else
                            obj = Filters.eq(aname,filter.getInt());
                        break;
                    }

                    case AttributeFilter.FILTEROP_NE:
                        if (negate)
                            obj = Filters.eq(aname,filter.getInt());
                        else
                            obj = Filters.ne(aname,filter.getInt());
                        break;

                    case AttributeFilter.FILTEROP_CONTAINS:
                    case AttributeFilter.FILTEROP_STARTSWITH:
                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.48")); //$NON-NLS-1$
                    }

                    case AttributeFilter.FILTEROP_PRESENCE:
                        if (negate)
                            obj = Filters.not(Filters.exists(aname));
                        else
                            obj = Filters.exists(aname);
                        break;

                    case AttributeFilter.FILTEROP_GREATER:
                        if (negate)
                            obj = Filters.lte(aname,filter.getInt());
                        else
                            obj = Filters.gt(aname,filter.getInt());
                        break;


                    case AttributeFilter.FILTEROP_LESS:
                        if (negate)
                            obj = Filters.gte(aname,filter.getInt());
                        else
                            obj = Filters.lt(aname,filter.getInt());
                        break;

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                        if (negate)
                            obj = Filters.lt(aname,filter.getInt());
                        else
                            obj = Filters.gte(aname,filter.getInt());
                        break;

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        if (negate)
                            obj = Filters.gt(aname,filter.getInt());
                        else
                            obj = Filters.lte(aname,filter.getInt());
                        break;

                }
                return obj;
            }
        }

        return null;
    }

    public static Bson mapFilter(PrecedenceFilter filter, boolean negate, boolean isValPath)
            throws ScimException, BackendException {

        return MongoFilterMapper.mapFilter(filter.getChildFilter(),
                (negate != filter.isNot()), isValPath);

    }

    public static Bson mapFilter(LogicFilter filter, boolean negate, boolean isValPath)
            throws ScimException, BackendException {
        Bson obj;

        if (filter.isAnd()) {
            if (negate)  // NOT A AND NOT B
                obj = Filters.nor(MongoFilterMapper.mapFilter(filter.getValue1(), false, isValPath),MongoFilterMapper.mapFilter(filter.getValue2(), false, isValPath));
            else
                obj = Filters.and(MongoFilterMapper.mapFilter(filter.getValue1(), false, isValPath),MongoFilterMapper.mapFilter(filter.getValue2(), false, isValPath));
        } else
            if (negate)  // NAND:  NOT A OR NOT B
                obj = Filters.or(MongoFilterMapper.mapFilter(filter.getValue1(), true, isValPath),MongoFilterMapper.mapFilter(filter.getValue2(), true, isValPath));
            else
                obj = Filters.or(MongoFilterMapper.mapFilter(filter.getValue1(), false, isValPath),MongoFilterMapper.mapFilter(filter.getValue2(), false, isValPath));

        return obj;

    }

    public static Bson mapFilter(ValuePathFilter filter)
            throws ScimException, BackendException {

        String item = filter.getAttribute().getRelativePath();
        Bson mfilter = MongoFilterMapper.mapFilter(filter.getValueFilter(),false,true);
        return Filters.elemMatch(item,mfilter);
    }

    public static Bson mapFilter(ValuePathFilter filter, boolean invert)
            throws ScimException, BackendException {

        if (invert)
            return Filters.not(mapFilter(filter));
        else
            return mapFilter(filter);

    }

}
