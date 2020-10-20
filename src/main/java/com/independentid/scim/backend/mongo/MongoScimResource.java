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

package com.independentid.scim.backend.mongo;

import java.io.IOException;
import java.text.ParseException;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ExtensionValues;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.Meta;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ConflictException;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */
/**
 * @author pjdhunt
 *
 */
public class MongoScimResource extends ScimResource {

	

	private Document originalResource;
	
	
	/**
	 * MongoScimResource wraps ScimResource in order to provide direct Mongo BSON Document mapping.
	 */
	protected MongoScimResource() {
		
	}

	/**
	 * Parses a Mongo <Document> and converts to <ScimResource> using <MongoMapUtil>.
	 * @param cfg A handle to SCIM <ConfigMgr> which holds the Schema definitions
	 * @param container TODO
	 * @param resourceNode A Mongo <Document> object containing the Mongo resource to be converted to ScimResource
	 * @throws SchemaException is thrown when unable to parse data not defined in SCIM <Schema> configuration
	 * @throws ParseException is thrown when a known format is invalid (e.g. URI, Date, etc)
	 * @throws IOException  is thrown due to an unexpected IO Error
	 * @throws JsonProcessingException  is thrown when converting from/to Jackson <JsonNode> representation.
	 * @throws ConflictException 
	 */
	public MongoScimResource(ConfigMgr cfg, Document dbResource, String container)
			throws SchemaException, ParseException, ScimException, JsonProcessingException, IOException {
		super();
		//super(cfg, MongoMapUtil.toScimJsonNode(dbResource), null);
		
		this.originalResource = dbResource;	
		setResourceType(container);;
		parseDocument(dbResource);
		
	}
	
	protected void parseDocument(Document doc) throws ParseException, SchemaException, ScimException {
		

		this.schemas = doc.getList("schemas", String.class);
		if (this.schemas == null)
			throw new SchemaException("Schemas attribute missing");
		
		ObjectId oid = doc.get("_id", ObjectId.class);
		if (oid != null)
			this.id = oid.toString();
		
		this.externalId = doc.getString("externalId");
		
		Document mdoc = doc.get("meta", Document.class);
		if (mdoc != null) {
			this.meta = new Meta();
			this.meta.setCreatedDate(mdoc.getDate("created"));
			this.meta.setLastModifiedDate(mdoc.getDate("lastModified"));
			this.meta.setResourceType(mdoc.getString("resourceType"));
			this.meta.setLocation(mdoc.getString("location"));
			try {
				this.meta.setVersion(mdoc.getString("version"));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		parseAttributes(doc);
		
	}
	
	protected void parseAttributes(Document doc) throws ScimException, SchemaException, ParseException {
		//ResourceType type = cfg.getResourceType(getResourceType());
		//String coreSchemaId = type.getSchema();

		// Look for all the core schema vals
		//Schema core = cfg.getSchemaById(coreSchemaId);
		
		Attribute[] attrs = coreSchema.getAttributes(); 
		for (int i=0; i < attrs.length; i++) {
			Value val = MongoMapUtil.mapBsonDocument(attrs[i], doc);
			
			if (val != null)
				this.coreAttrs.put(attrs[i].getName(), val);
		}
		
		String[] eids = type.getSchemaExtension();
		for (int i=0; i < eids.length; i++) {
			Schema schema = cfg.getSchemaById(eids[i]);
			ExtensionValues val = MongoMapUtil.mapBsonExtension(schema, doc);
			if (val != null) 
				this.extAttrs.put(eids[i], val);
		}
		
	}
	
	/**
	 * @return the original Mongo <Document> used to create this <ScimResource>.
	 */
	public Document getOriginalDBObject() {
		return this.originalResource;
	}
	
	public Document toMongoDocument(RequestCtx ctx) throws ScimException {
		return toMongoDocument(this,ctx);
	}
	
	/**
	 * Converts a <ScimResource> object to a Mongo <Document>. Conversion does not modify original ScimResource.
	 * Performs necessary "id" to "_id" conversion.
	 * @param res The <ScimResource> object to be converted
	 * @param ctx The <RequestCtx> indicating the container associated with the resource (usually contains original query).
	 * @return A <Document> representing the mapped <ScimResource>
	 * @throws ScimException
	 */
	public static Document toMongoDocument(ScimResource res,RequestCtx ctx) throws ScimException {

		return MongoMapUtil.mapResource(res);
	}


	
}
