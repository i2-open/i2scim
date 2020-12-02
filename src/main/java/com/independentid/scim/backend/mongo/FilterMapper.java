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

import org.bson.Document;

import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.AttributeFilter;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.LogicFilter;
import com.independentid.scim.protocol.PrecedenceFilter;
import com.independentid.scim.protocol.ValuePathFilter;
import com.independentid.scim.schema.Attribute;
import com.mongodb.BasicDBList;


public class FilterMapper {

    public static Document mapFilter(Filter filter) throws ScimException,
            BackendException {
        return mapFilter(filter, false);
    }

    public static Document mapFilter(Filter filter, boolean negate)
            throws ScimException, BackendException {

        if (filter instanceof AttributeFilter)
            return FilterMapper.mapFilter((AttributeFilter) filter, negate);

        if (filter instanceof LogicFilter)
            return FilterMapper.mapFilter((LogicFilter) filter, negate);

        if (filter instanceof ValuePathFilter)
            return FilterMapper.mapFilter((ValuePathFilter) filter, negate);

        if (filter instanceof PrecedenceFilter)
            return FilterMapper.mapFilter((PrecedenceFilter) filter, negate);

        throw new BackendException(Messages.getString("FilterMapper.0") //$NON-NLS-1$
                + filter.getClass().getCanonicalName());
    }

    public static Document mapFilter(AttributeFilter filter)
            throws BadFilterException {
        return mapFilter(filter, false);
    }

    public static Document mapFilter(AttributeFilter filter, boolean negate)
            throws BadFilterException {
        Document obj = new Document();
        Attribute attr = filter.getAttribute();
        String aname = attr.getName();
        if (aname.equalsIgnoreCase("$ref"))
            aname = "href";
        //String aname = attr.getRelativePath();

        Document regEx;
        switch (attr.getType().toLowerCase()) {

            case Attribute.TYPE_String: {
                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        if (!filter.getAttribute().getCaseExact()) {
                            // do case inexact regex
					/*
					Pattern reg = Pattern.compile(
							"^" + filter.asString() + "$", //$NON-NLS-1$ //$NON-NLS-2$
							Pattern.CASE_INSENSITIVE);
					*/
                            regEx = new Document("$regex", '^' + filter.asString() + '$');
                            regEx = regEx.append("$options", "i");

                            //obj = com.mongodb.client.model.Filters.regex(aname, "^" + filter.asString() + "$", "i");
                            if (negate) {
                                Document not = new Document("$not", regEx); //$NON-NLS-1$
                                obj.put(aname, not);
                            } else
                                obj.put(aname, regEx);
                            break;
                        } else if (negate)
                            obj.put(aname, new Document("$not", filter.getValue()));
                        else
                            obj.put(aname, filter.getValue());
                        break;

                    }

                    case AttributeFilter.FILTEROP_NE:
                        if (!filter.getAttribute().getCaseExact()) {
                            regEx = new Document("$regex", "^" + filter.asString() + "$");
                            regEx = regEx.append("$options", "i");
                            if (negate) {
                                obj.put(aname, regEx);
                            } else
                                obj.put(aname, new Document("$ne", regEx)); //$NON-NLS-1$
                        } else {
                            // case sensistive
                            if (negate) {
                                obj.put(aname, filter.asString());
                            } else
                                obj.put(aname, new Document("$ne", filter.asString())); //$NON-NLS-1$
                        }
                        break;

                    case AttributeFilter.FILTEROP_CONTAINS: {

                        if (filter.getAttribute().getCaseExact())
                            regEx = new Document("$regex", filter.asString());
                        else {
                            regEx = new Document("$regex", filter.asString());
                            regEx = regEx.append("$options", "i");
                        }
                        if (negate) {
                            Document not = new Document("$not", regEx); //$NON-NLS-1$
                            obj.put(aname, not);
                        } else
                            obj.put(aname, regEx);

                        break;
                    }

                    case AttributeFilter.FILTEROP_STARTSWITH: {

                        if (filter.getAttribute().getCaseExact())
                            regEx = new Document("$regex", "^" + filter.asString());
                        else {
                            regEx = new Document("$regex", "^" + filter.asString());
                            regEx = regEx.append("$options", "i");
                        }

                        if (negate) {
                            Document not = new Document("$not", regEx); //$NON-NLS-1$
                            obj.put(aname, not);
                        } else
                            obj.put(aname, regEx);

                        break;
                    }

                    case AttributeFilter.FILTEROP_ENDSWITH: {

                        if (filter.getAttribute().getCaseExact())
                            regEx = new Document("$regex", filter.asString() + "$");
                        else {
                            regEx = new Document("$regex", filter.asString() + "$");
                            regEx = regEx.append("$options", "i");
                        }

                        if (negate) {
                            Document not = new Document("$not", regEx); //$NON-NLS-1$
                            obj.put(aname, not);
                        } else
                            obj.put(aname, regEx);
                        break;
                    }

                    case AttributeFilter.FILTEROP_PRESENCE:
                        if (negate) {
                            obj.put(aname, new Document("$exists", false)); //$NON-NLS-1$
                        } else
                            obj.put(aname, new Document("$exists", true)); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_GREATER:
                        if (negate)
                            obj.put(aname, new Document("$le", filter.getValue())); //$NON-NLS-1$
                        else
                            obj.put(aname, new Document("$gt", filter.getValue())); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_LESS:
                        if (negate)
                            obj.put(aname, new Document("$ge", filter.getValue())); //$NON-NLS-1$
                        else
                            obj.put(aname, new Document("$lt", filter.getValue())); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:
                        if (negate)
                            obj.put(aname, new Document("$lt", filter.getValue())); //$NON-NLS-1$
                        else
                            obj.put(aname, new Document("$ge", filter.getValue())); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        if (negate)
                            obj.put(aname, new Document("$gt", filter.getValue())); //$NON-NLS-1$
                        else
                            obj.put(aname, new Document("$le", filter.getValue())); //$NON-NLS-1$
                        break;

                }
                return obj;
            }

            case Attribute.TYPE_Boolean: {
                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        if (negate)
                            obj.put(aname, !filter.getBoolean());
                        else
                            obj.put(aname, filter.getBoolean());
                        break;
                    }

                    case AttributeFilter.FILTEROP_NE:
                        if (negate)
                            obj.put(aname,
                                    new Document("$ne", !filter.getBoolean())); //$NON-NLS-1$
                        else
                            obj.put(aname,
                                    new Document("$ne", filter.getBoolean())); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_CONTAINS:

                    case AttributeFilter.FILTEROP_STARTSWITH:

                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.1")); //$NON-NLS-1$

                    }

                    case AttributeFilter.FILTEROP_PRESENCE:
                        if (negate)
                            obj.put(aname, new Document("$exists", false)); //$NON-NLS-1$
                        else
                            obj.put(aname, new Document("$exists", true)); //$NON-NLS-1$
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
                throw new BadFilterException(
                        Messages.getString("FilterMapper.35")); //$NON-NLS-1$
            }

            case Attribute.TYPE_Date: {
                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        if (negate)
                            obj.put(aname, new Document("$ne", filter.getDate())); //$NON-NLS-1$
                        else
                            obj.put(aname, filter.getDate());
                        break;
                    }

                    case AttributeFilter.FILTEROP_NE:
                        if (negate)
                            obj.put(aname, filter.getDate());
                        else
                            obj.put(aname, new Document("$ne", filter.getDate())); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_CONTAINS:

                    case AttributeFilter.FILTEROP_STARTSWITH:

                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.38")); //$NON-NLS-1$
                    }

                    case AttributeFilter.FILTEROP_PRESENCE:
                        if (negate)
                            obj.put(aname, new Document("$exists", false)); //$NON-NLS-1$
                        else
                            obj.put(aname, new Document("$exists", true)); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_GREATER:

                    case AttributeFilter.FILTEROP_LESS:

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.43")); //$NON-NLS-1$

                }
                return obj;
            }

            case Attribute.TYPE_Number: {

                switch (filter.getOperator()) {

                    case AttributeFilter.FILTEROP_EQ: {
                        obj.put(aname, filter.getInt());
                        break;
                    }

                    case AttributeFilter.FILTEROP_NE:
                        obj.put(aname, new Document("$ne", filter.getInt())); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_CONTAINS:

                    case AttributeFilter.FILTEROP_STARTSWITH:

                    case AttributeFilter.FILTEROP_ENDSWITH: {
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.48")); //$NON-NLS-1$
                    }

                    case AttributeFilter.FILTEROP_PRESENCE:
                        if (negate)
                            obj.put(aname, new Document("$exists", false)); //$NON-NLS-1$
                        else
                            obj.put(aname, new Document("$exists", true)); //$NON-NLS-1$
                        break;

                    case AttributeFilter.FILTEROP_GREATER:

                    case AttributeFilter.FILTEROP_LESS:

                    case AttributeFilter.FILTEROP_GREATEROREQUAL:

                    case AttributeFilter.FILTEROP_LESSOREQUAL:
                        throw new BadFilterException(
                                Messages.getString("FilterMapper.53")); //$NON-NLS-1$

                }
                return obj;
            }
        }

        return null;
    }

    public static Document mapFilter(PrecedenceFilter filter)
            throws ScimException, BackendException {
        return mapFilter(filter, false);
    }

    public static Document mapFilter(PrecedenceFilter filter, boolean negate)
            throws ScimException, BackendException {

        return FilterMapper.mapFilter(filter.getChildFilter(),
                (negate != filter.isNot()));

    }

    public static Document mapFilter(LogicFilter filter) throws ScimException,
            BackendException {
        return mapFilter(filter, false);
    }

    public static Document mapFilter(LogicFilter filter, boolean negate)
            throws ScimException, BackendException {
        Document obj = new Document();

        Document obj1 = FilterMapper.mapFilter(filter.getValue1(), negate);
        Document obj2 = FilterMapper.mapFilter(filter.getValue2(), negate);

        BasicDBList list = new BasicDBList();
        list.add(obj1);
        list.add(obj2);

        if (negate)
            obj.put("$not", new Document(filter.isAnd() ? "$and" : "$or", list)); //$NON-NLS-1$ //$NON-NLS-2$
        else
            obj.put(filter.isAnd() ? "$and" : "$or", list); //$NON-NLS-1$ //$NON-NLS-2$
        return obj;

    }

    public static Document mapFilter(ValuePathFilter filter)
            throws ScimException, BackendException {
        return mapFilter(filter, false);
    }

    public static Document mapFilter(ValuePathFilter filter, boolean invert)
            throws ScimException, BackendException {

        if (invert)
            return new Document("$not", new Document(filter.getAttributeName(), new Document("$elemMatch", FilterMapper.mapFilter(filter.getValueFilter(), false))));
        else
            return new Document(filter.getAttributeName(), new Document("$elemMatch", FilterMapper.mapFilter(filter.getValueFilter(), false)));


    }

}
