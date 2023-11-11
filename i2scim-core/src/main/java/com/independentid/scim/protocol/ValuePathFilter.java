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

package com.independentid.scim.protocol;

import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.resource.ComplexValue;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public class ValuePathFilter extends Filter {

	private final Attribute attr;
	private final Filter filter;
	
	public ValuePathFilter(String attr, String filterStr, @NotNull RequestCtx ctx) throws BadFilterException {
		super(filterStr);
        schemaManager = ctx.getSchemaMgr();
        this.filter = Filter.parseFilter(filterStr, attr, ctx);
        this.attr = schemaManager.findAttribute(attr, ctx);
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

	@Override
	protected void filterAttributes(@NotNull Set<Attribute> attrSet) {
		attrSet.add(this.attr);
		filter.filterAttributes(attrSet);
	}
}
