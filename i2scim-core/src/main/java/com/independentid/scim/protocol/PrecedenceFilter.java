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
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;

import javax.validation.constraints.NotNull;
import java.util.Set;

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
		StringBuilder buf = new StringBuilder();
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
		StringBuilder buf = new StringBuilder();
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

	@Override
	protected void filterAttributes(@NotNull Set<Attribute> attrSet) {
		filter.filterAttributes(attrSet);
	}



}
