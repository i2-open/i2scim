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
package com.independentid.scim.schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.serializer.ScimSerializer;
/*
 * Attributes defines the set of attributes for a SCIM <code>Schema</code> object
 * 
 */
public class Attribute implements ScimSerializer,Comparable<Attribute> {
	
	public final static String TYPE_String = "string";
	public final static String TYPE_Date = "dateTime";
	public final static String TYPE_Boolean = "boolean";
	public final static String TYPE_Decimal = "decimal";
	public final static String TYPE_Number = "number";
	public final static String TYPE_Complex = "complex";
	public final static String TYPE_Reference = "reference";
	
	public final static String MUTABILITY_readWrite = "readWrite";
	public final static String MUTABILITY_writeOnly = "writeOnly";
	public final static String MUTABILITY_readOnly = "readOnly";
	public final static String MUTABILITY_immutable = "immutable";
	
	public final static String RETURNED_default = "default";
	public final static String RETURNED_always = "always";
	public final static String RETURNED_request = "request";
	public final static String RETURNED_never = "never";
	
	public final static String UNIQUE_none = "none";
	public final static String UNIQUE_server = "server";
	public final static String UNIQUE_global = "global";

	private String schema;
	
	private String path;
	
    private String name;
	
    private String description;
	
    private boolean caseExact;
	
    private String mutability;
	
    private String uniqueness;
	
    private ArrayList<String> canonicalValues;
	
	private ArrayList<String> referenceTypes;

	private TreeMap<String,Attribute> subAttributes;

    private String returned;
	
    private boolean required;
	
    private boolean multiValued;
	
    private String type;
	
	private Attribute parent;   
    
	public Attribute () {
		this.subAttributes = new TreeMap<String,Attribute>(String.CASE_INSENSITIVE_ORDER);
	}	
	
	public Attribute(String name) {
		this.subAttributes = new TreeMap<String,Attribute>(String.CASE_INSENSITIVE_ORDER);
		this.name = name;
		setPath(null,name);
	}
	
	public Attribute(JsonNode node) throws SchemaException {
		this(node, null);
	}

	public Attribute(JsonNode node, Attribute parent) throws SchemaException {
		this.subAttributes = new TreeMap<String,Attribute>(String.CASE_INSENSITIVE_ORDER);
		this.parent = parent;
		this.parseJson(node);
	}
            
    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean getCaseExact() {
        return this.caseExact;
    }

    public void setCaseExact(boolean caseExact) {
        this.caseExact = caseExact;
    }

    public String getMutability() {
        return this.mutability;
    }

    public void setMutability(String mutability) {
        this.mutability = mutability;
    }

    public Attribute getParent() {
    	return this.parent;
    }
    
    public boolean isChild() {
    	return (this.parent != null);
    }
    
    public String getUniqueness() {
        return this.uniqueness;
    }

    public void setUniqueness(String uniqueness) {
        this.uniqueness = uniqueness;
    }

    public Map<String,Attribute> getSubAttributesMap() {
        return this.subAttributes;
    }
    
    public Attribute getSubAttribute(String name) {
    	return this.subAttributes.get(name);
    }

    public void setSubAttribute(Attribute subattribute) {
        this.subAttributes.put(subattribute.getName(), subattribute);
    }

    public String getReturned() {
        return this.returned;
    }
    
    /**
     * Based on the request context <RequestCtx> and {@link #getReturned()}, checks if an Attribute <Value> should be returned.
     * @param ctx A <RequestCtx> object containing values for requested and excluded attributes per SCIM request line
     * @return true if the attribute should be returned to the SCIM client.
     */
    public boolean isReturnable(RequestCtx ctx) {
    	if (ctx == null) {
    		switch (this.getReturned()) {
    		case Attribute.RETURNED_always:
        		return true;
        	case Attribute.RETURNED_never:
        		return false;
        	case Attribute.RETURNED_request:
        		// is only returned when specifically requested
        		return false;
        	case Attribute.RETURNED_default:
        		return true;
    		}
    	}
        	
    	boolean isReturnable = ctx.isAttrRequested(this);
    	if (this.getSubAttributesMap().isEmpty())
    		return isReturnable;
    
    	if (ctx.isAttrExcluded(this))
    		return false;
    	if (isReturnable)
    		return true;
    	
    	// Check if a sub-attribute is returnable
    	boolean subreturn = false;
    	Iterator<Attribute> sattrIter = this.getSubAttributesMap().values().iterator();
    	while (!subreturn && sattrIter.hasNext()) {
    		if (ctx.isAttrRequested(sattrIter.next()))
    			subreturn = true;
    	}
    	
    	return subreturn;
    }
    
    public boolean isSubAttrReturnable(RequestCtx ctx) {
    	
    	return false;
    }

    public void setReturned(String returned) {
        this.returned = returned;
    }

    public boolean getRequired() {
        return this.required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isMultiValued() {
        return this.multiValued;
    }

    public void setMultiValued(boolean multiValued) {
        this.multiValued = multiValued;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ArrayList<String> getCanonicalValues() {
        return this.canonicalValues;
    }

    public void setCanonicalValues(List<String> canonicalValues) {
        this.canonicalValues = new ArrayList<String>(canonicalValues);
    }
    
    public ArrayList<String> getReferenceTypes() {
    	return this.referenceTypes;
    }
    
    public void setReferenceTypes(List<String> types) {
    	this.referenceTypes = new ArrayList<String>(types);
    }
    
    public void setPath(String schema, String relPath) {
    	this.schema  = schema;
    	
    	if (relPath == null)
    		this.path = this.name;
    	else if (relPath.endsWith(this.name))
    		this.path = relPath;
    	else
    		this.path = relPath + "." + this.name;

    	if (this.subAttributes == null) return;
    	
    	Iterator<String> saiter = this.subAttributes.keySet().iterator();
    	while (saiter.hasNext()) {
    		String sname = saiter.next();
    		Attribute attr = this.subAttributes.get(sname);
    		attr.setPath(schema, this.path+"."+sname);
    	}
    }
    
    /**
     * @return The schema URI part of the attribute path or null if undefined
     */
    public String getSchema() {
    	return this.schema;
    }
    
    /**
     * @return The relative path (without schema) of the attribute
     */
    public String getRelativePath() {
    	return this.path;
    }
    /**
     * @return Returns the full path of the attribute
     */
    public String getPath() {
    	if (schema == null)
    		return this.path;
    	else 
    		return schema + ":" + this.path;
    }


	@Override
	public int compareTo(Attribute attr) {
		return this.path.compareTo(attr.getPath());
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		serialize(gen, ctx, false);
	}

	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx, boolean forHash) throws IOException {
		gen.writeStartObject();
		
		gen.writeStringField("name", this.name);
		
		gen.writeStringField("type", this.type);
		
		if (this.description != null) {
			gen.writeStringField("description", this.description);
		}
		
		if (this.returned != null)
			gen.writeStringField("returned", this.returned);
		
		if (this.mutability != null)
			gen.writeStringField("mutability", this.mutability);
		
		
		
		if (this.type.equals(TYPE_String)) {
			gen.writeBooleanField("caseExact", caseExact);
			
			if (this.uniqueness != null)
				gen.writeStringField("uniqueness", this.uniqueness);
			
			if (this.canonicalValues != null &&
					this.canonicalValues.size() > 0) {
				gen.writeArrayFieldStart("canonicalValues");
				Iterator<String> iter = this.canonicalValues.iterator();
				while (iter.hasNext()) {
					String value = iter.next();
					gen.writeString(value);
				}
				gen.writeEndArray();
			}
		}
		
		if (this.type.equals(TYPE_Reference)) {
			if (this.referenceTypes != null & this.referenceTypes.size()>0) {
				gen.writeArrayFieldStart("referenceTypes");
				Iterator<String> iter = this.referenceTypes.iterator();
				while (iter.hasNext()) {
					String value = iter.next();
					gen.writeString(value);
				}
				gen.writeEndArray();
			}
		}
		
		gen.writeBooleanField("multiValued", multiValued);
		
		gen.writeBooleanField("required", required);
		
		if (this.subAttributes.size() > 0) {
			gen.writeArrayFieldStart("subAttributes");
			Iterator<Attribute> iter = this.subAttributes.values().iterator();
			while (iter.hasNext()) {
				Attribute attr = iter.next();
				attr.serialize(gen, ctx, forHash);
			}
			gen.writeEndArray();
			
		}
		
		gen.writeEndObject();
		
	}

	@Override
	public void parseJson(JsonNode node) throws SchemaException {
		JsonNode item = node.get("name");
		if (item != null)
			this.name = item.asText();
		else
			throw new SchemaException("Attribute has no name\n"
					+ node.toString());

		item = node.get("type");
		if (item == null)
			throw new SchemaException("Attribute " + this.name
					+ " has no type defined.");
		this.type = item.asText();

		item = node.get("description");
		if (item != null)
			this.description = item.asText();

		item = node.get("mutability");
		if (item != null)
			this.mutability = item.asText();

		if (this.type.equals(TYPE_String)) {
			item = node.get("caseExact");
			if (item != null)
				this.caseExact = item.asBoolean(false);

			item = node.get("uniqueness");
			if (item != null)
				this.uniqueness = item.asText();

			item = node.get("canonicalValues");
			if (item != null) {
				this.canonicalValues = new ArrayList<String>();
				Iterator<JsonNode> iter = item.elements();
				while (iter.hasNext()) {
					JsonNode term = iter.next();
					this.canonicalValues.add(term.asText());
				}
			}
		}

		if (this.type.equals(Attribute.TYPE_Reference)) {
			item = node.get("referenceTypes");
			this.referenceTypes = new ArrayList<String>();
			if (item != null) {
				Iterator<JsonNode> iter = item.elements();
				while (iter.hasNext()) {
					JsonNode term = iter.next();
					this.referenceTypes.add(term.asText());
				}
			}

		}

		item = node.get("returned");
		if (item != null)
			this.returned = item.asText().toLowerCase();

		item = node.get("required");
		if (item != null)
			this.required = item.asBoolean();

		item = node.get("multiValued");
		if (item != null)
			this.multiValued = item.asBoolean();

		if (this.type.equals(TYPE_Complex)) {
			item = node.get("subAttributes");
			if (item != null) {
				// this should already be initialized!
				//this.subAttributes = new TreeMap<String, Attribute>(String.CASE_INSENSITIVE_ORDER);
				Iterator<JsonNode> iter = item.elements();
				while (iter.hasNext()) {
					JsonNode snode = iter.next();
					Attribute attr = new Attribute(snode, this);
					String path = this.name + "." + attr.getName();

					// The path for a sub attribute doesn't have URI.
					attr.setPath(null, path);
					this.subAttributes.put(attr.getName(), attr);
				}
			}
		}

	}
	
	public boolean isModifiable() {
		return (this.mutability.equalsIgnoreCase(MUTABILITY_readWrite)
				|| this.mutability.equalsIgnoreCase(MUTABILITY_writeOnly));
	}
	
	public String toString() {
		return "Attribute: "+this.getRelativePath();
	}

}
