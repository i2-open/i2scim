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

public class LogicFilter extends Filter {

	boolean isAnd = false;
	Filter value1, value2;
	
	public LogicFilter(boolean isAnd, Filter filter1,Filter filter2) {
		super();
		this.isAnd = isAnd;
		this.value1 = filter1;
		this.value2 = filter2;
	}
	
	public boolean isAnd() {
		return this.isAnd;
	}
	
	public Filter getValue1() {
		return this.value1;
	}
	
	public Filter getValue2() {
		return this.value2;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		if (value1 != null)
			buf.append(value1.toString());
		if (isAnd)
			buf.append(" and ");
		else
			buf.append(" or ");
		if (value2 != null)
			buf.append(value2.toString());
		return buf.toString();
	}
	
	public String toValuePathString() {
		StringBuffer buf = new StringBuffer();
		if (value1 != null)
			buf.append(value1.toValuePathString());
		if (isAnd)
			buf.append(" and ");
		else
			buf.append(" or ");
		if (value2 != null)
			buf.append(value2.toValuePathString());
		return buf.toString();
	}


	@Override
	public boolean isMatch(ScimResource res) throws BadFilterException {
		if (this.isAnd)
			return value1.isMatch(res) && value2.isMatch(res);
		return value1.isMatch(res) || value2.isMatch(res);
	}

	@Override
	public boolean isMatch(Value value) throws BadFilterException {
		if (this.isAnd)
			return value1.isMatch(value) && value2.isMatch(value);
		return value1.isMatch(value) || value2.isMatch(value);
	}

}
