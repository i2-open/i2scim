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

import com.independentid.scim.core.err.InvalidSyntaxException;
import com.independentid.scim.core.err.NoTargetException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;

import java.util.StringTokenizer;

/**
 * @author pjdhunt
 *
 */
public class JsonPath {
	protected String aname;
	protected String vpath;
	protected String subAttr;
	protected Filter filter;
	protected Attribute targAttr;
	
	public JsonPath(ScimResource res, JsonPatchOp op, RequestCtx ctx) throws ScimException {
		StringTokenizer tkn = new StringTokenizer(op.path,"[]");
		
		aname = tkn.nextToken();
		
		vpath = null;
		subAttr = null;
		
		if (tkn.hasMoreTokens()) {
			vpath = tkn.nextToken();
		
			if (tkn.hasMoreTokens())
				subAttr = tkn.nextToken();
		}
		targAttr = res.getAttribute(aname, ctx);
		if (targAttr == null)
			throw new InvalidSyntaxException("Invalid or undefined attribute: "+aname);
		
		
		filter = null;
		if (vpath != null)
			filter = Filter.parseFilter(vpath,aname, ctx, null);
		
		// check to see if attribute has a multi-value parent
		if (targAttr.isChild()){
			Attribute parent = targAttr.getParent();
			if (parent == null)
				throw new ScimException("Unexpected null parent returned for Attribute definition marked as 'child' attribute.");
			if (parent.isMultiValued() && vpath == null)
				throw new NoTargetException("Target specified is the sub-attribute of a multi-valued attribute. No value filter specified.");
		}
	}
	
	public boolean isMultiValue() {
		return (targAttr != null && targAttr.isMultiValued());
	}
	
	public boolean hasSubAttr() {
		return (subAttr != null);
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
		return isMultiValue() && this.subAttr !=null;
	}
	
	/**
	 * @return true if the target is the sub-attribute of a single value complex attribute
	 */
	public boolean isSimpleSubAttribute() {
		return this.subAttr == null && targAttr.isChild();
	}
	
	/**
	 * @return A String containing the sub-attribute name. Only used in path expressions of the form attr[type eq "xyz"]subattr form
	 */
	public String getSubAttrName() {
		return this.subAttr;
	}
	
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("Path: ");
		buf.append(this.aname);
		if (this.vpath != null) {
			buf.append('[').append(this.vpath).append(']');
			if (this.subAttr != null)
				buf.append(this.subAttr);
		}
		return buf.toString();
		
	}
}
