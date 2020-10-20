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
import com.independentid.scim.server.BadFilterException;

public class PrecedenceFilter extends Filter {

	private boolean isNot = false;
	private Filter filter;
	
	public PrecedenceFilter(String filterStr) {
		super(filterStr);
		
	}
	
	public PrecedenceFilter(Filter subfilter, boolean isNot) {
		super(null);
		this.isNot = isNot;
		this.filter = subfilter;
		
	}
	
	public boolean isNot() {
		return this.isNot;
	}
	
	public Filter getChildFilter() {
		return this.filter;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		if (isNot)
			buf.append("not");
		buf.append("(");
		if (filter == null)
			buf.append("<<UNDEFINED>>");
		else
			buf.append(filter.toString());
		buf.append(")");
		return buf.toString();
	}
	
	public String toValuePathString() {
		StringBuffer buf = new StringBuffer();
		if (isNot)
			buf.append("not");
		buf.append("(");
		if (filter == null)
			buf.append("<<UNDEFINED>>");
		else
			buf.append(filter.toValuePathString());
		buf.append(")");
		return buf.toString();
	}

	@Override
	public boolean isMatch(ScimResource res) throws BadFilterException {
		if (isNot)
			return !filter.isMatch(res);
		return filter.isMatch(res);
		
	}

	@Override
	public boolean isMatch(Value value) throws BadFilterException {
		if (isNot)
			return !filter.isMatch(value);
		return filter.isMatch(value);
	}

}
