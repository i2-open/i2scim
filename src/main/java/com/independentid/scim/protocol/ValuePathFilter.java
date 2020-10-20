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

package com.independentid.scim.protocol;

import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.server.BadFilterException;

public class ValuePathFilter extends Filter {

	private Attribute attr;
	private Filter filter;
	
	public ValuePathFilter(String attr, String filterStr) throws BadFilterException {
		super(filterStr);
		this.filter = Filter.parseFilter(filterStr, attr, null);
		this.attr = cfg.findAttribute(attr, null);
	}

	public String getAttributeName() {
		return this.attr.getName();
	}
	
	public Attribute getAttribute() {
		return this.attr;
	}
	
	public Filter getValueFilter() {
		return this.filter;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(this.attr.getName());
		buf.append('[');
		buf.append(this.filter.toValuePathString());
		buf.append(']');
		return buf.toString();
	}
	
	public String toPathString() {
		StringBuffer buf = new StringBuffer();
		
		/*
		if (this.parentAttr == null)
			buf.append(this.attr.getPath());
		else 
			buf.append(this.attr.getName());
			*/
		buf.append(this.attr.getPath());
		buf.append('[');
		buf.append(this.filter.toValuePathString());
		buf.append(']');
		return buf.toString();
	}
	
	public String toValuePathString() {
		return toString();
	}

	@Override
	public boolean isMatch(ScimResource res) {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean isMatch(Value value) throws BadFilterException {
		// TODO Auto-generated method stub
		return false;
	}

}
