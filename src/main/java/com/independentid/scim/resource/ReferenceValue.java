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
package com.independentid.scim.resource;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.server.ConflictException;

public class ReferenceValue extends Value implements IBulkIdTarget  {

	URI value;
	IBulkIdResolver resolver;
	
	public ReferenceValue() {
		
	}

	public ReferenceValue(Attribute attr, JsonNode node) throws ConflictException, SchemaException, ParseException {
		this(attr, node, null);
	}
	
	public ReferenceValue(Attribute attr, String uri) throws ConflictException, SchemaException, ParseException {
		super();
		this.jtype = JsonNodeType.STRING;
		setUri(attr,uri);
	}
	
	public ReferenceValue(Attribute attr, URI uri) throws ConflictException, SchemaException, ParseException {
		super();
		this.jtype = JsonNodeType.STRING;
		this.value = uri;
	}


	public ReferenceValue(Attribute attr, JsonNode node, IBulkIdResolver bulkIdResolver) throws ConflictException, SchemaException, ParseException {
		super(attr, node);
		this.resolver = bulkIdResolver;
		
		parseJson(attr,node);
	}

	public String toString() {
		String host = this.value.getHost();
		if (host != null && host.equalsIgnoreCase("localhost"))
			return value.getPath();
		else
			return value.toString();
	}
	@Override
	public void serialize(JsonGenerator gen, RequestCtx ctx) throws IOException {
		String host = this.value.getHost();
		if (host != null && host.equalsIgnoreCase("localhost"))
			gen.writeString(value.getPath());
		else
			gen.writeString(value.toString());
		
	}

	@Override
	public void parseJson(Attribute attr, JsonNode node)
			throws ConflictException, SchemaException, ParseException {
		setUri(attr,node.asText());
	}
	
	private void setUri(Attribute attr,String newUri) throws SchemaException {
	
		try {
			if (newUri.startsWith("urn:"))
				this.value = new URI(newUri);
			else {
				// the value is some form of URL
				URL url;
				if (newUri.startsWith("/"))
					url = new URL("http","localhost",newUri);
				else
					url = new URL(newUri);
				this.value = url.toURI();
			}
		} catch (MalformedURLException | URISyntaxException e) {
			
			throw new SchemaException ("Invalid url parsed: "+newUri+ " for attribute: "+attr.getPath(),e);
		}
	}

	@Override
	public URI getValueArray() {
		return this.value;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#hasBulkIds()
	 */
	@Override
	public boolean hasBulkIds() {
		return this.value.toString().toLowerCase().startsWith("bulkid:");
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getBulkIdsRequired(java.util.List)
	 */
	@Override
	public void getBulkIdsRequired(List<String> bulkList) {
		
		if (hasBulkIds())
			bulkList.add(this.value.toString());
			
		
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.op.IBulkIdTarget#getAttributesWithBulkIdValues(java.util.List)
	 */
	@Override
	public void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs) {
		if (hasBulkIds())
			bulkIdAttrs.add(this);
		
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ReferenceValue) {
			ReferenceValue val = (ReferenceValue) obj;
			// equality is based on the java.net.URL based equality
			return
				val.value.equals(this.value);
		}
		// types don't match,so not equal
		return false;
	}
	
	

}
