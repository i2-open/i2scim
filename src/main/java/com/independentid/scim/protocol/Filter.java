/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015,2020 Phillip Hunt, All Rights Reserved                   *
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

import java.util.ArrayList;

import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.server.BadFilterException;
import com.independentid.scim.server.ConfigMgr;

public abstract class Filter {

	private String filter;
	
	protected static ConfigMgr cfg = ConfigMgr.getInstance();
	
	
	protected final static String URN = 
			"urn:[a-zA-Z0-9][a-zA-Z0-9-]{1,31}:([a-zA-Z0-9()+,.:=@;$_!*'-]|%[0-9A-Fa-f]{2})+";
	protected final static String ATTRNAME = 
			"[a-zA-Z][a-zA-Z0-9-]*"; 
	
	protected final static String VALUEPATH =
			"\\[.*\\]";
	
	protected final static String LOGICFILTER = 
			"(eq|ne|sw|ew|gt|lt|ge|le|co)";
	
	protected final static String LOGICEXPR = 
			ATTRNAME+"\\s"+LOGICFILTER+"\\s"+"\\S+";
	
	protected final static String PRESEXPR = 
			ATTRNAME+"\\spr";
	
	protected final static String PRECEDENTEXPR = 
			"(not){0,1}\\s{0,1}(.+)";
	
	protected final static String SUBATTR = "\\."+ATTRNAME;
	
	//protected final static String PATH = URI + ATTR
	
	public Filter(String filterStr) {
		this.filter = filterStr;
	}
	
	public Filter() {
		this.filter = null;
		
	}
	
	
	public String getFilterStr () {
		return this.filter;
	} 
	
	public static Filter parseFilter(String filterStr, RequestCtx ctx) throws BadFilterException {
		return parseFilter(filterStr, null, ctx);
	}

	/**
	 * Parses the provided filterStr and returns a Filter object
	 * @param filterStr A SCIM filter expressed in string form
	 * @param parentAttr Optional parent attribute, used when parsing Value Filters.
	 * @param ctx Optional RequestCtx that provides additional context for matching 
	 * short attribute names. For example, ambiguous attribute "name" can be matched to User schema if 
	 * searching within the Users container.
	 * @return A Filter object containing the parsed filter.
	 * @throws BadFilterException Thrown if the filter is an invalid SCIM filter.
	 */
	public static Filter parseFilter(String filterStr, String parentAttr, RequestCtx ctx) throws BadFilterException {

		int i = 0;
		int bCnt = 0;  int bIndex = -1;
		int vCnt = 0;  int vIndex = -1;
		int wIndex = -1;
		ArrayList<Filter> clauses = new ArrayList<Filter>();
		
		boolean isLogic = false;
		boolean isAnd = false;
		boolean isNot = false;
		boolean isAttr = false;
		String attr = null;
		boolean isExpr = false;
		String cond = null;
		boolean isValue = false;
		String value = null;
		boolean isQuote = false;

		
		for (i = 0; i < filterStr.length(); i++) {
			char c = filterStr.charAt(i);
			switch (c) {
			case '(':
				// ignore brackets in value strings
				if (isQuote || isValue)
					break;
				bCnt++;
				if (bCnt == 1)
					bIndex = i;
				i++;
				boolean quotedBracket = false;
				while (i < filterStr.length() && bCnt > 0) {
					char cc = filterStr.charAt(i);
					switch (cc) {
					case '\"':
						quotedBracket = !quotedBracket;
						break;
					case '(':
						//ignore brackets in values
						if (quotedBracket)
							break;
						bCnt++;
						break;
					case ')':
						//ignore brackets in values
						if (quotedBracket)
							break;
						bCnt--;
						if (bCnt == 0) {
							String subFilterStr = filterStr.substring(bIndex+1,i);
							Filter subFilter = Filter.parseFilter(subFilterStr, parentAttr, ctx);
							// Precedence is redundant if Attribute Filter
							if (! (subFilter instanceof AttributeFilter))
								clauses.add(new PrecedenceFilter(subFilter, isNot));
							else
								clauses.add(subFilter);
							//reset for next phrase
							bIndex = -1;
						}
					default:
						
					}
					// only increment if we are still processing ( ) phrases
					if (bCnt > 0) 
						i++;
				}
				break;
				
			case '[':
				if (isQuote || isValue)
					break;
				vCnt++;
				if (vCnt == 1)
					vIndex = i;
				
				i++;
				boolean quotedSqBracket = false;
				while (i < filterStr.length() && vCnt > 0) {
					char cc = filterStr.charAt(i);
					switch (cc) {
					case '\"':
						quotedSqBracket = !quotedSqBracket;
						break;
					case '[':
						if (quotedSqBracket)
							break;
						if (vCnt > 0)
							throw new BadFilterException("Invalid filter: A second '[' was detected while loocking for a ']' in an attribute value filter.");
						vCnt++;
						break;
					case ']':
						if (quotedSqBracket)
							break;
						vCnt--;
						if (vCnt == 0) {
							String aname = filterStr.substring(wIndex,vIndex);
							String valueFilterStr = filterStr.substring(vIndex+1,i);
							Filter clause = new ValuePathFilter(aname,valueFilterStr);
							clauses.add(clause);
							//reset for next phrase
							vIndex = -1;
							wIndex = -1;
							isAttr = false;
						}
					default:
						
					}
					// only increment if we are still processing ( ) phrases
					if (vCnt > 0) 
						i++;
				}
				if (i == filterStr.length() && vCnt > 0)
					throw new BadFilterException("Invalid filter: missing close ']' bracket");
				break;
				
			case ' ':
				if (isQuote)
					break;
				//end of phrase
				if (wIndex > -1) {
					String phrase = filterStr.substring(wIndex,i);
					if (phrase.equalsIgnoreCase("or") || phrase.equalsIgnoreCase("and")) {
						isLogic = true;
						isAnd = phrase.equalsIgnoreCase("and");
						wIndex=-1;
						break;
					}
					
					if(isAttr && attr == null) {
						attr = phrase;
						wIndex = -1;
					} else 
						if (isExpr && cond == null) {
							cond = phrase;
							wIndex = -1;
							if (cond.equalsIgnoreCase(AttributeFilter.FILTEROP_PRESENCE)) {
								Filter attrExp = new AttributeFilter(attr,cond,null,parentAttr, ctx);
								attr = null; isAttr = false;
								cond = null; isExpr = false;
								value = null; isValue = false;
								clauses.add(attrExp);
							}
						} else
							if (isValue && value == null) {
								value = phrase;
								wIndex = -1;
								Filter attrExp = new AttributeFilter(attr,cond,value,parentAttr, ctx);
								attr = null; isAttr = false;
								cond = null; isExpr = false;
								value = null; isValue = false;
								clauses.add(attrExp);
							}
					
				}
				break;
			case ')':
				// ignore brackets in value strings
				if (isQuote || isValue)
					break;
				if (bCnt == 0) 
					throw new BadFilterException("Invalid filter: missing open '(' bracket");
				// is this not still an error?
				break;
			case ']':
				// ignore brackets in value strings
				if (isQuote || isValue)
					break;
				if (vCnt == 0)
					throw new BadFilterException("Invalid filter: missing open '[' bracket");
			case 'n': case 'N':
				if (!isValue) {
					if (i+3 < filterStr.length()
							&& filterStr.substring(i, i+3)
								.equalsIgnoreCase("not")) {
						isNot = true;
						i = i + 2; // skip to open brace.
						break;
				}}
				// let the default mode execute
			default:
				if (c == '\"')
					if (isQuote)
						isQuote = false;
					else 
						isQuote = true;
				if (wIndex == -1)
					wIndex = i;
				if (!isAttr)
					isAttr = true;
				else if (!isExpr && attr != null)
					isExpr = true;
				else if (!isValue && cond != null)
					isValue = true;
			}
			
			
		}
		if (bCnt > 0) 
			throw new BadFilterException("Invalid filter: missing close ')' bracket");
		if (vCnt > 0)
			throw new BadFilterException("Invalid filter: missing ']' bracket");
		
		if (wIndex > -1 && i == filterStr.length()) {
			if (isAttr && cond != null) {
				// a value match at the end of the filter input string
				value = filterStr.substring(wIndex);
				if (value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length()-1);
				}
				Filter attrExp = new AttributeFilter(attr,cond,value,parentAttr, ctx);
				
				clauses.add(attrExp);
			} else {
				// a presence match at the end of the filter input string
				if (isAttr) 
					cond = filterStr.substring(wIndex);
				Filter attrExp = new AttributeFilter(attr,cond,value,parentAttr,ctx);
				clauses.add(attrExp);
			}
		}
		
		if (isLogic && clauses.size() == 2) {
			return new LogicFilter(isAnd,clauses.remove(0),clauses.remove(0));
		}
		
		if (clauses.size() == 1)
			return clauses.remove(0);
		
		if (clauses.size() == 0)
			throw new BadFilterException("Unknown filter exception (no filter to return)");
		throw new BadFilterException("Invalid filter. Missing and/or clause.");
		
	}
	
	public abstract boolean isMatch(Value value) throws BadFilterException;
	
	public abstract boolean isMatch(ScimResource res) throws BadFilterException;

	public static boolean checkMatch(ScimResource res, RequestCtx ctx) throws BadFilterException {
		Filter cfilter = ctx.filter;
		if (cfilter == null)
			return true;
		
		return cfilter.isMatch(res);
		
	}
	
	abstract public String toValuePathString();
}
