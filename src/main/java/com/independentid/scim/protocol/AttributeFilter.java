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

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.independentid.scim.resource.BooleanValue;
import com.independentid.scim.resource.ComplexValue;
import com.independentid.scim.resource.DateValue;
import com.independentid.scim.resource.IntegerValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.resource.ValueUtil;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.server.BadFilterException;

public class AttributeFilter extends Filter {
	public final static String FILTEROP_EQ = "eq";
	public final static String FILTEROP_NE = "ne";
	public final static String FILTEROP_CONTAINS = "co";
	public final static String FILTEROP_STARTSWITH = "sw";
	public final static String FILTEROP_ENDSWITH = "ew";
	public final static String FILTEROP_GREATER = "gt";
	public final static String FILTEROP_LESS = "lt";
	public final static String FILTEROP_GREATEROREQUAL = "ge";
	public final static String FILTEROP_LESSOREQUAL = "le";
	public final static String FILTEROP_PRESENCE = "pr";
	
	public final static List<String> valid_ops = 
		Arrays.asList(FILTEROP_EQ,FILTEROP_NE,
				FILTEROP_CONTAINS, FILTEROP_PRESENCE,
				FILTEROP_STARTSWITH, FILTEROP_ENDSWITH,
				FILTEROP_GREATER, FILTEROP_LESS,
				FILTEROP_GREATEROREQUAL, FILTEROP_LESSOREQUAL);
	private Attribute parentAttr;
	
	private Attribute attr;
	
    private String compOp;
    
    private String valString;
    
    private Object val;
	
    /*
	public AttributeFilter(ConfigMgr cfg, String filterStr) throws BadFilterException {
		super(cfg, filterStr);
		this.parseFilter(filterStr);
	}
	*/
	
	public AttributeFilter(String attr, String cond, String value, RequestCtx ctx) throws BadFilterException {
		this(attr, cond, value, null, ctx);
	}

	public AttributeFilter(String aname, String cond, String value, String parentAttr, RequestCtx ctx) throws BadFilterException {
		super();
		if (parentAttr == null)
			this.parentAttr = null;
		else
			this.parentAttr = cfg.findAttribute(parentAttr, ctx);
		
		if (this.parentAttr != null) {
			this.attr = this.parentAttr.getSubAttribute(aname);
		} else
			this.attr = cfg.findAttribute(aname, ctx);
		
		if (this.attr == null) {
			// If no attribute check if it is common schema or just create a placeholder attribute definition
			this.attr = new Attribute(aname);
			this.attr.setType(Attribute.TYPE_String);
			if (aname.equalsIgnoreCase("id")
					|| aname.equalsIgnoreCase("name")
					|| aname.equalsIgnoreCase("description"))
				this.attr.setCaseExact(true);
				
			else if (aname.equalsIgnoreCase("meta")) {
				this.attr.setType(Attribute.TYPE_Complex);
				
			} else 
				this.attr.setType(ValueUtil.parseValueType(aname, value));
			
		} else if (this.attr.isChild() && this.parentAttr == null)
			this.parentAttr = this.attr.getParent();
		if (this.attr.getType().equals(Attribute.TYPE_Complex)) {
			// when just a parent attribute is specified, the value attribute is used. 
			Attribute vAttr = this.attr.getSubAttribute("value");
			if (vAttr != null) {
				this.parentAttr = this.attr;
				this.attr = vAttr;
			}
		}
		this.compOp = cond;
		//this.val = value;
		this.valString = value;
		
		if (value == null)
			// Set null value object for presence filter cases
			this.val = null;
		else {
			String type = this.attr.getType();
			switch (type.toLowerCase()) {
			case ValueUtil.TYPE_BINARY:
				this.val = value.getBytes();
				
			case ValueUtil.TYPE_BOOLEAN:
				this.val = Boolean.parseBoolean(value);
				break;
				
			case ValueUtil.TYPE_DATETIME:
				this.val = value;
				
				break;
				
			case ValueUtil.TYPE_STRING:
				
				if (value.startsWith("\"") &&
						value.endsWith("\""))
					this.val = new String(value.substring(1,value.length()-1));
				else
					this.val = new String(value);
				this.valString = (String) this.val;
				break;
				
			}
		}
	}

	/*
	public void parseFilter(String filterStr) throws BadFilterException {
		StringTokenizer tkn = new StringTokenizer(filterStr,"");
		this.attr = null;
		if (tkn.hasMoreTokens()) {
			String attrName = tkn.nextToken();
			this.attr = this.cfg.findAttribute(attrName, null, null);
			
		}
		if (this.attr == null)
			throw new BadFilterException("Missing on invalid attribute name. "+filterStr);
		
		compOp = null;
		if (tkn.hasMoreTokens()) {
			this.compOp = tkn.nextToken().toLowerCase();
			if (!valid_ops.contains(this.compOp));
				throw new BadFilterException("Invalid filter operator: "+this.compOp);
		}
		if (compOp == null)
			throw new BadFilterException("Missing comparision operator." + filterStr);
		
		if (!this.compOp.equals(FILTEROP_PRESENCE)) {
			if (!tkn.hasMoreTokens())
				throw new BadFilterException("Missing comparison value: "+filterStr);
			String value = tkn.nextToken();
			this.valString = value;
			String type = this.attr.getType();
			switch (type.toLowerCase()) {
			case ValueUtil.TYPE_BINARY:
				this.val = value.getBytes();
				
			case ValueUtil.TYPE_BOOLEAN:
				this.val = new Boolean(value);
				break;
				
			case ValueUtil.TYPE_DATETIME:
				try {
					this.val = Meta.ScimDateFormat.parse(value);
				} catch (ParseException e) {
					throw new BadFilterException("Invalid date in filter");
				}
				break;
				
			case ValueUtil.TYPE_STRING:
				this.val = new String(value);
				break;
				
			}
		}
		
	}
	*/
	
	public Attribute getAttribute() {
		return this.attr;
	}
	
	public String getOperator() {
		return this.compOp;
	}
	
	public String getValueType(){
		return this.attr.getType();
	}
	
	public Object getValue() {
		return this.val;
	}
	
	public byte[] getBinary() {
		if (this.val instanceof byte[])
			return (byte[]) this.val;
		
		return null;
	}
	public Date getDate() {
		if (this.val instanceof Date) 
			return (Date) this.val;
		return null;
	}
	
	public String getString() {
		if (this.val instanceof String)
			return (String) this.val;
		return null;
	}
	
	public Boolean getBoolean() {
		if (this.val instanceof Boolean) {
			return (Boolean) this.val;
		}
		return null;
	}
	
	public Integer getInt() {
		if (this.val instanceof Integer)
			return (Integer) val;
		return null;
	}
	
	/**
	 * @return The filter excluding the parent attribute as it is not needed in ValuePath filters
	 */
	public String toValuePathString() {
		StringBuffer buf = new StringBuffer();
		buf.append(this.attr.getName());
		buf.append(' ').append(this.compOp);
		if (!this.compOp.equals(FILTEROP_PRESENCE)) {
			String sval = this.val.toString();
			if (sval.contains(" "))
				buf.append(" \"").append(sval).append("\"");
			else
				buf.append(' ').append(sval);
		}
		return buf.toString();
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		
		/*
		if (this.parentAttr == null)
			buf.append(this.attr.getPath());
		else 
			buf.append(this.attr.getName());
			*/
		
		if (this.parentAttr != null)
			buf.append(this.parentAttr.getName())
				.append(".").append(this.attr.getName());
		else
			buf.append(this.attr.getName());
		buf.append(' ').append(this.compOp);
		if (!this.compOp.equals(FILTEROP_PRESENCE)) {
			String sval = this.val.toString();
			if (sval.contains(" "))
				buf.append(" \"").append(sval).append("\"");
			else
				buf.append(' ').append(sval);
		}
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
		buf.append(' ').append(this.compOp);
		if (!this.compOp.equals(FILTEROP_PRESENCE)) {
			String sval = this.val.toString();
			if (sval.contains(" "))
				buf.append(" \"").append(sval).append("\"");
			else
				buf.append(' ').append(sval);
		}
		return buf.toString();
	}
	
	
	/**
	 * @return Returns the value as originally specified in the filter (no conversion)
	 */
	public String asString() {
		return this.valString;
	}
	
	public boolean isMatch(Value matchVal) throws BadFilterException {
		Value value = matchVal;
		if (value instanceof ComplexValue) {
			// locate the sub-attribute value that is to be matched.
			ComplexValue cval = (ComplexValue) value;
			
			value = cval.vals.get(this.attr.getName());
		}
		
		if (value == null && compOp.equals(AttributeFilter.FILTEROP_PRESENCE)) 
			return false;
		
		switch (attr.getType().toLowerCase()) {
		
		case Attribute.TYPE_String: {
			String val = ((StringValue) value).getValueArray();
			switch (compOp) {

			case AttributeFilter.FILTEROP_EQ: {
				if (!attr.getCaseExact()) {
					// do case inexact regex	
					return val.equalsIgnoreCase(valString);
					
				} else
					return val.equals(valString);

			}
				
			case AttributeFilter.FILTEROP_NE:
				if (!attr.getCaseExact())
					return !val.equalsIgnoreCase(valString);
				else
					return !val.equals(valString);

			case AttributeFilter.FILTEROP_CONTAINS: {
				if (attr.getCaseExact())
					return val.contains(valString);
				else
					return val.toLowerCase().contains(valString.toLowerCase());
			}
				

			case AttributeFilter.FILTEROP_STARTSWITH: {
				if (attr.getCaseExact())
					return val.startsWith(valString);
				else
					return val.toLowerCase().startsWith(valString.toLowerCase());
			}
				

			case AttributeFilter.FILTEROP_ENDSWITH: {
				if (attr.getCaseExact())
					return val.endsWith(valString);
				else
					return val.toLowerCase().endsWith(valString.toLowerCase());
			}

			case AttributeFilter.FILTEROP_GREATER:
				if (attr.getCaseExact())
					return val.compareTo(valString) > 0;
				return val.compareToIgnoreCase(valString) > 0;
				

			case AttributeFilter.FILTEROP_LESS:
				if (attr.getCaseExact())
					return val.compareTo(valString) < 0;
				return val.compareToIgnoreCase(valString) < 0;
				
			case AttributeFilter.FILTEROP_GREATEROREQUAL:
				if (attr.getCaseExact())
					return val.compareTo(valString) >= 0;
				return val.compareToIgnoreCase(valString) >= 0;

			case AttributeFilter.FILTEROP_LESSOREQUAL:
				if (attr.getCaseExact())
					return val.compareTo(valString) < 1;
				return val.compareToIgnoreCase(valString)  < 1;

			}
			
		}
			
		case Attribute.TYPE_Boolean: {
			Boolean val = ((BooleanValue) value).getValueArray();
			switch (getOperator()) {

			case AttributeFilter.FILTEROP_EQ: {
				return val.equals(getBoolean());
			}
				
			case AttributeFilter.FILTEROP_NE:
				return !val.equals(getBoolean());

			case AttributeFilter.FILTEROP_CONTAINS: {
				throw new BadFilterException("Filter operator not supported with boolean attributes.");

			}
				

			case AttributeFilter.FILTEROP_STARTSWITH: {
				throw new BadFilterException("Filter operator not supported with boolean attributes.");
			}
				

			case AttributeFilter.FILTEROP_ENDSWITH: {
				throw new BadFilterException("Filter operator not supported with boolean attributes.");
			}

			case AttributeFilter.FILTEROP_GREATER:
				throw new BadFilterException("Filter operator not supported with boolean attributes.");

			case AttributeFilter.FILTEROP_LESS:
				throw new BadFilterException("Filter operator not supported with boolean attributes.");

			case AttributeFilter.FILTEROP_GREATEROREQUAL:
				throw new BadFilterException("Filter operator not supported with boolean attributes.");

			case AttributeFilter.FILTEROP_LESSOREQUAL:
				throw new BadFilterException("Filter operator not supported with boolean attributes.");

			}
		
		}
				
		case Attribute.TYPE_Complex: {
			throw new BadFilterException("Complex attributes may not be used in a comparison filter without a sub-attribute");
		}
				
		case Attribute.TYPE_Date: {
			Date val = ((DateValue) value).getDateValue();
			switch (getOperator()) {

			case AttributeFilter.FILTEROP_EQ: {
				return val.equals(getDate());
			}
				
			case AttributeFilter.FILTEROP_NE:
				return !val.equals(getDate());

			case AttributeFilter.FILTEROP_CONTAINS: {
				throw new BadFilterException("Filter operator not supported with date attributes.");
			}
				

			case AttributeFilter.FILTEROP_STARTSWITH: {
				throw new BadFilterException("Filter operator not supported with date attributes.");
			}
				

			case AttributeFilter.FILTEROP_ENDSWITH: {
				throw new BadFilterException("Filter operator not supported with date attributes.");
			}

			case AttributeFilter.FILTEROP_GREATER:
				return val.compareTo(getDate()) > 0;

			case AttributeFilter.FILTEROP_LESS:
				return val.compareTo(getDate()) < 0;

			case AttributeFilter.FILTEROP_GREATEROREQUAL:
				return val.compareTo(getDate()) > -1;

			case AttributeFilter.FILTEROP_LESSOREQUAL:
				return val.compareTo(getDate()) < 1;

			}

		}
				
		case Attribute.TYPE_Number:{
			Integer val = ((IntegerValue) value).getValueArray();
			switch (getOperator()) {

			case AttributeFilter.FILTEROP_EQ: {
				return val.equals(getInt());
			}
				
			case AttributeFilter.FILTEROP_NE:
				return !val.equals(getInt());

			case AttributeFilter.FILTEROP_CONTAINS: {
				throw new BadFilterException("Filter operator not supported with number attributes.");
			}
				

			case AttributeFilter.FILTEROP_STARTSWITH: {
				throw new BadFilterException("Filter operator not supported with number attributes.");
			}
				

			case AttributeFilter.FILTEROP_ENDSWITH: {
				throw new BadFilterException("Filter operator not supported with number attributes.");
			}

			case AttributeFilter.FILTEROP_GREATER:
				return val.compareTo(getInt()) > 0;

			case AttributeFilter.FILTEROP_LESS:
				return val.compareTo(getInt()) < 0;

			case AttributeFilter.FILTEROP_GREATEROREQUAL:
				return val.compareTo(getInt()) >= 0;

			case AttributeFilter.FILTEROP_LESSOREQUAL:
				return val.compareTo(getInt()) < 1;

			}
		
		}

	}
		
		return true;
	}
	
	public boolean isMatch(ScimResource res) throws BadFilterException {
		
		Value value = res.getValue(attr);
		return this.isMatch(value);
	}

}
