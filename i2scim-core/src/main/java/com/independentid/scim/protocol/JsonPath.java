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
import com.independentid.scim.core.err.NoTargetException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;

/**
 * Implements the SCIM variant of
 * @author pjdhunt
 *
 */
public class JsonPath {
	protected final String aname;
	protected final Attribute targAttr;

	protected String vpathFilter;
	protected String vpSubAttr;
	protected Filter filter;

	public JsonPath(ScimResource res, JsonPatchOp op, RequestCtx ctx) throws ScimException {
		if (op.path.contains("[")) {
			int vstart = op.path.indexOf('[');
			int vend = op.path.indexOf(']');
			if (vstart >= vend)
				throw new BadFilterException("Invalid valuepath filter detected for "+op.path);
			aname = op.path.substring(0,vstart);
			vpathFilter = op.path.substring(vstart+1,vend);
			if (op.path.length() > vend+1) {
				vpSubAttr = op.path.substring(vend + 1);
				if (vpSubAttr.startsWith("."))
					vpSubAttr = vpSubAttr.substring(1);
			} else
				vpSubAttr = null;
		} else {
			vpathFilter = null;
			vpSubAttr = null;
			aname = op.path;
		}

		targAttr = res.getAttribute(aname, ctx);
		if (targAttr == null)
			throw new NoTargetException("Invalid or undefined attribute: "+aname);

		filter = null;
		if (vpathFilter != null)
			filter = Filter.parseFilter(vpathFilter,aname, ctx);
		
		// check to see if attribute has a multi-value parent
		if (targAttr.isChild()){
			Attribute parent = targAttr.getParent();
			if (parent == null)
				throw new ScimException("Unexpected null parent returned for Attribute definition marked as 'child' attribute.");
            if (parent.isMultiValued() && vpathFilter == null)
                throw new NoTargetException("Target specified is the sub-attribute of a multi-valued attribute. No value filter specified.");
        }
    }

    public JsonPath(JsonPatchOp op, RequestCtx ctx) throws ScimException {
        if (op.path.contains("[")) {
            int vstart = op.path.indexOf('[');
            int vend = op.path.indexOf(']');
            if (vstart >= vend)
                throw new BadFilterException("Invalid valuepath filter detected for " + op.path);
            aname = op.path.substring(0, vstart);
            vpathFilter = op.path.substring(vstart + 1, vend);
            if (op.path.length() > vend + 1) {
                vpSubAttr = op.path.substring(vend + 1);
                if (vpSubAttr.startsWith("."))
                    vpSubAttr = vpSubAttr.substring(1);
            } else
                vpSubAttr = null;
        } else {
            vpathFilter = null;
            vpSubAttr = null;
            aname = op.path;
        }

        targAttr = ctx.getSchemaMgr().findAttribute(aname, ctx);

        if (targAttr == null)
            throw new NoTargetException("Invalid or undefined attribute: " + aname);

        filter = null;
        if (vpathFilter != null)
            filter = Filter.parseFilter(vpathFilter, aname, ctx);

        // check to see if attribute has a multi-value parent
        if (targAttr.isChild()) {
            Attribute parent = targAttr.getParent();
            if (parent == null)
                throw new ScimException("Unexpected null parent returned for Attribute definition marked as 'child' attribute.");
			if (parent.isMultiValued() && vpathFilter == null)
				throw new NoTargetException("Target specified is the sub-attribute of a multi-valued attribute. No value filter specified.");
		}
	}
	
	public boolean isMultiValue() {
		return (targAttr != null && targAttr.isMultiValued());
	}
	
	public boolean hasVpathSubAttr() {
		return (vpSubAttr != null);
	}
	
	public String getTargetAttrName() {
		return this.aname;
	}
	
	public Filter getTargetValueFilter() {
		return this.filter;
	}
	
	public Attribute getTargetAttribute() {
		return this.targAttr;
	}
	
	/**
	 * @return true if the target is the sub-attribute of a multi-valued attribute
	 */
	public boolean isSubAttrMultiValue() {
		return isMultiValue() && this.vpSubAttr !=null;
	}
	
	/**
	 * @return true if the target is the sub-attribute of a single value complex attribute
	 */
	public boolean isSimpleSubAttribute() {
		return this.vpSubAttr == null && targAttr.isChild();
	}
	
	/**
	 * @return A String containing the sub-attribute name. Only used in path expressions of the form attr[type eq "xyz"]subattr form
	 */
	public String getSubAttrName() {
		return this.vpSubAttr;
	}

	public Attribute getSubAttribute() {
		return targAttr.getSubAttribute(this.vpSubAttr);
	}
	
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Path: ");
		buf.append(this.aname);
		if (this.vpathFilter != null) {
			buf.append('[').append(this.vpathFilter).append(']');
			if (this.vpSubAttr != null)
				buf.append(this.vpSubAttr);
		}
		return buf.toString();
		
	}
}
