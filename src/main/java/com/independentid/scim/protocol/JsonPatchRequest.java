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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.ConfigMgr;

/*
 * This class holds a parsed SCIM JSON Modify request.
 * @author pjdhunt
 *
 */
public class JsonPatchRequest {

	protected ConfigMgr cfg;
	
	protected RequestCtx ctx;
	
	protected ArrayList<JsonPatchOp> ops;
	
	/**
	 * 
	 */
	public JsonPatchRequest() {
		this.cfg = null;
		this.ctx = null;
		this.ops = new ArrayList<JsonPatchOp>();
	}
	
	/**
	 * @param cfg The current context Configuration context.
	 * @param resourceNode A pointer to  SCIM Json Modify request message to be parsed.
	 * @throws SchemaException
	 * @throws ParseException
	 */
	public JsonPatchRequest(ConfigMgr cfg, JsonNode resourceNode, RequestCtx ctx) throws SchemaException, ParseException {
		this.cfg = cfg;
		this.ctx = ctx;
		this.ops = new ArrayList<JsonPatchOp>();
		parseJson(resourceNode);
	}
	
	public void parseJson(JsonNode node) throws SchemaException {
		JsonNode snode = node.get("schemas");
		if (snode == null) throw new SchemaException("JSON is missing 'schemas' attribute.");
		
		boolean invalidSchema = true;
		if (snode.isArray()) {
			Iterator<JsonNode> jiter = snode.elements();
			while (jiter.hasNext() && invalidSchema){
				JsonNode anode = jiter.next();
				if (anode.asText().equalsIgnoreCase(ScimParams.SCHEMA_API_PatchOp))
					invalidSchema = false;
			}
		} else
			if (snode.asText().equalsIgnoreCase(ScimParams.SCHEMA_API_PatchOp))
				invalidSchema = false;
		
		if (invalidSchema)
			throw new SchemaException("Expecting JSON with schemas attribute with value of: "+ScimParams.SCHEMA_API_PatchOp);
		
		JsonNode opsnode = node.get("Operations");
		if (opsnode == null)
			throw new SchemaException("Missing 'Operations' attribute array.");
		
		if (!opsnode.isArray()) {
			throw new SchemaException("Expecting 'Operations' to be an array.");
		}
		
		Iterator<JsonNode> oiter = opsnode.elements();
		while (oiter.hasNext()) {
			JsonNode oper = oiter.next();
			JsonPatchOp op = new JsonPatchOp(this.ctx,oper);
			this.ops.add(op);
		}	
	
	}
	
	public int getSize() {
		return this.ops.size();
	}
	
	public Iterator<JsonPatchOp> iterator() {
		return this.ops.iterator();
	}
	
	public String toString() {
		if (this.ops.size() == 0) return "JsonPatchRequest ops: <EMPTY>";
		
		StringBuffer buf = new StringBuffer();
		buf.append("JsonPatchRequest ops: \n");
		Iterator<JsonPatchOp> iter = this.ops.iterator();
		while (iter.hasNext()) {
			JsonPatchOp jop = iter.next();
			buf.append("op=").append(jop.op);
			if (jop.path !=null)
				buf.append(", path=").append(jop.path);
			if (iter.hasNext()) buf.append(",\n");
		}
		return buf.toString();
	}

}
