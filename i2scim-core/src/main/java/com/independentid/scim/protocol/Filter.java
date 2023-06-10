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
import com.independentid.scim.schema.SchemaManager;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Transactional
public abstract class Filter {

	public static SchemaManager smgr;

	private final String filter;

	//cfg = ConfigMgr.getConfig();
	
	public final static String REGX_URN =
			"urn:[a-zA-Z0-9][a-zA-Z0-9-]{1,31}:([a-zA-Z0-9()+,.:=@;$_!*'-]|%[0-9A-Fa-f]{2})+";
	public final static String REGX_ATTRNAME =
			"[a-zA-Z][a-zA-Z0-9-]*"; 
	
	public final static String REGX_VALUEPATH =
			"\\[.*\\]";
	
	public final static String REGX_LOGICFILTER =
			"(eq|ne|sw|ew|gt|lt|ge|le|co)";
	
	public final static String LOGICEXPR =
			REGX_ATTRNAME +"\\s"+ REGX_LOGICFILTER +"\\s"+"\\S+";
	
	public final static String REGX_PRESEXPR =
			REGX_ATTRNAME +"\\spr";
	
	public final static String REGX_PRECEDENTEXPR =
			"(not){0,1}\\s{0,1}(.+)";
	
	public final static String REGX_SUBATTR = "\\."+ REGX_ATTRNAME;
	
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
	 * For the given filter, returns the set of attributes used in the filter.
	 * @param filter The Filter object to be evaluated
	 * @return The set of {@link Attribute} used in the filter.
	 */
	public static Set<Attribute> filterAttributes(Filter filter) {
		HashSet<Attribute> attrSet = new HashSet<>();
		if (filter != null)
			filter.filterAttributes(attrSet);
		return attrSet;
	}

	protected abstract void filterAttributes(@NotNull Set<Attribute> attrSet);

	/**
	 * Parses the provided filterStr and returns a Filter object
	 * @param filterStr A SCIM filter expressed in string form
	 * @param parentAttr Optional parent attribute, used when parsing Value Filters.
	 * @param ctx The RequestCtx which may be used for detecting attribute names using request Path
	 * @return A Filter object containing the parsed filter.
	 * @throws BadFilterException Thrown if the filter is an invalid SCIM filter.
	 */
	public static Filter parseFilter(String filterStr, String parentAttr, @NotNull RequestCtx ctx) throws BadFilterException {
		smgr = ctx.getSchemaMgr();
		int bCnt = 0;  int bIndex = -1;
		int valPathCnt = 0;  int vPathStartIndex = -1;
		int wordIndex = -1;
		ArrayList<Filter> clauses = new ArrayList<>();
		
		boolean isLogic = false;
		boolean isAnd = false;
		boolean isNot = false;
		boolean isAttr = false;
		String attr = null;
		boolean isExpr = false;
		String cond = null;
		boolean isValue = false;

		String value;
		boolean isQuote = false;

		int i;
		
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
				valPathCnt++;
				if (valPathCnt == 1)
					vPathStartIndex = i;
				
				i++;
				boolean quotedSqBracket = false;
				while (i < filterStr.length() && valPathCnt > 0) {
					char cc = filterStr.charAt(i);
					switch (cc) {
					case '\"':
						quotedSqBracket = !quotedSqBracket;
						break;
					case '[':
						if (quotedSqBracket)
							break;
						if (valPathCnt > 1)
							throw new BadFilterException("Invalid filter: A second '[' was detected while loocking for a ']' in an attribute value filter.");
						valPathCnt++;
						break;
					case ']':
						if (quotedSqBracket)
							break;
						valPathCnt--;
						if (valPathCnt == 0) {
							String aname = filterStr.substring(wordIndex,vPathStartIndex);
							String valueFilterStr = filterStr.substring(vPathStartIndex+1,i);
							Filter clause = new ValuePathFilter(aname,valueFilterStr,ctx);
							clauses.add(clause);
							if (i+1 < filterStr.length() && filterStr.charAt(i+1) != ' ') {
								i++;
								// advance forward to the end of the phrase (ignore sub-attribute for the purposes of a filter)
								while (i < filterStr.length() && filterStr.charAt(i) != ' ')
									i++;
							}
							//reset for next phrase
							vPathStartIndex = -1;
							wordIndex = -1;
							isAttr = false;
						}

					default:
						
					}
					// only increment if we are still processing ( ) phrases
					if (valPathCnt > 0)
						i++;
				}
				if (i == filterStr.length() && valPathCnt > 0)
					throw new BadFilterException("Invalid filter: missing close ']' bracket");
				break;
				
			case ' ':
				if (isQuote)
					break;
				//end of phrase
				if (wordIndex > -1) {
					String phrase = filterStr.substring(wordIndex,i);
					if (phrase.equalsIgnoreCase("or") || phrase.equalsIgnoreCase("and")) {
						isLogic = true;
						isAnd = phrase.equalsIgnoreCase("and");
						wordIndex=-1;
						break;
					}
					
					if(isAttr && attr == null) {
						attr = phrase;
						wordIndex = -1;
					} else 
						if (isExpr && cond == null) {
							cond = phrase;
							wordIndex = -1;
							if (cond.equalsIgnoreCase(AttributeFilter.FILTEROP_PRESENCE)) {
								Filter attrExp = new AttributeFilter(attr,cond,null,parentAttr, ctx);
								attr = null; isAttr = false;
								cond = null; isExpr = false;
								isValue = false;
								clauses.add(attrExp);
							}
						} else
							if (isValue) {
								value = phrase;
								wordIndex = -1;
								Filter attrExp = new AttributeFilter(attr,cond,value,parentAttr, ctx);
								attr = null; isAttr = 	false;
								cond = null; isExpr = false;
								isValue = false;
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
				if (valPathCnt == 0)
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
					isQuote = !isQuote; //toggle quote state
				if (wordIndex == -1)
					wordIndex = i;
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
		if (valPathCnt > 0)
			throw new BadFilterException("Invalid filter: missing ']' bracket");
		
		if (wordIndex > -1 && i == filterStr.length()) {
			if (isAttr && cond != null) {
				// a value match at the end of the filter input string
				value = filterStr.substring(wordIndex);
				if (value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length()-1);
				}
				Filter attrExp = new AttributeFilter(attr,cond,value,parentAttr, ctx);
				
				clauses.add(attrExp);
			} else {
				// a presence match at the end of the filter input string
 				if (isAttr)
					cond = filterStr.substring(wordIndex);
				// in a presence filter the value is always null.
				Filter attrExp = new AttributeFilter(attr,cond,null,parentAttr, ctx);
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

	/**
	 * This method checks for a virtual attribute filter and if found removes it. This method will
	 * recurse through nested Filters (e.g. LogicFilters) and remove virtual terms. If none are left, a null is returned.
	 * @param filter The Filter to be checked.
	 * @return The filter representation with virtual attribute filters removed or null if no clauses remain.
	 */
	public static Filter removeVirtualClauses(Filter filter) {

		if (filter instanceof LogicFilter) {
			LogicFilter lfilter = (LogicFilter) filter;
			Filter v1 = lfilter.getValue1();
			Filter v2 = lfilter.getValue2();
			boolean modified = false;

			if (v1 instanceof LogicFilter) {
				v1 = removeVirtualClauses(v1);
				modified = true;
			}
			if (v2 instanceof LogicFilter) {
				v2 = removeVirtualClauses(v2);
				modified = true;
			}

			if (v1 instanceof AttributeFilter) {
				AttributeFilter attrFilter = (AttributeFilter) v1;
				if (attrFilter.isVirtualAttribute())
					return v2;
			}
			if (v2 instanceof AttributeFilter) {
				AttributeFilter attrFilter = (AttributeFilter) v2;
				if (attrFilter.isVirtualAttribute())
					return v1;
			}

			if (modified)
				return new LogicFilter(lfilter.isAnd(), v1, v2);
			else
				return filter;
		}

		if (filter instanceof AttributeFilter)
			return (((AttributeFilter)filter).isVirtualAttribute()) ? null:filter;

		if (filter instanceof PrecedenceFilter) {
			PrecedenceFilter pfilter = (PrecedenceFilter) filter;

			Filter sfilter = removeVirtualClauses(pfilter.getChildFilter());
			if (sfilter == null)
				return null;
			return filter;
		}

		// ignore valpath filters
		return filter;
	}

	abstract public String toValuePathString();
}
