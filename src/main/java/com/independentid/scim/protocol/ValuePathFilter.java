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
import com.independentid.scim.resource.ComplexValue;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;

public class ValuePathFilter extends Filter {

	private final Attribute attr;
	private final Filter filter;
	
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
		return this.attr.getName() +
				'[' +
				this.filter.toValuePathString() +
				']';
	}
	
	public String toPathString() {
		return this.attr.getPath() +
				'[' +
				this.filter.toValuePathString() +
				']';
	}
	
	public String toValuePathString() {
		return toString();
	}

	@Override
	public boolean isMatch(ScimResource res) throws BadFilterException {
		// TODO: In general the backend provider maps this. This will likely be needed for PATCH operation
		Value val = res.getValue(attr);
		return isMatch(val);
	}

	@Override
	public boolean isMatch(Value value) throws BadFilterException {
		// TODO: In general the backend provider maps this. Test code not written. Likely needed for PATCH
		// If multi-valued, then filter must be applied for each value.
		if (value instanceof ComplexValue)
			return isMatch((ComplexValue) value);
		if (value instanceof MultiValue) {
			MultiValue vals = (MultiValue) value;
			// get match value should apply the filter across a single value.
			return vals.getMatchValue(filter) != null;
		}
		return filter.isMatch(value);

	}

	private boolean isMatch(ComplexValue val) throws BadFilterException {
		return filter.isMatch(val);
	}



}
