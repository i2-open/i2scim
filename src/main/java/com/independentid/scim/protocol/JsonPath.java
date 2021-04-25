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
