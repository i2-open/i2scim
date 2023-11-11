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
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public class LogicFilter extends Filter {

	boolean isAnd;
	Filter value1;
	Filter value2;
	
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
		StringBuilder buf = new StringBuilder();
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
		StringBuilder buf = new StringBuilder();
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

	@Override
	protected void filterAttributes(@NotNull Set<Attribute> attrSet) {
		value1.filterAttributes(attrSet);
		value2.filterAttributes(attrSet);
	}
}
