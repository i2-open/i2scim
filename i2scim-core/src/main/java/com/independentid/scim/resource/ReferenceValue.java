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
package com.independentid.scim.resource;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.op.IBulkIdTarget;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

public class ReferenceValue extends Value implements IBulkIdTarget  {

	URI value;
	IBulkIdResolver resolver;
	
	public ReferenceValue(Attribute attr, JsonNode node) throws SchemaException, ParseException {
		this(attr, node, null);
	}
	
	public ReferenceValue(Attribute attr, String uri) throws SchemaException {
		super();
		this.jtype = JsonNodeType.STRING;
		setUri(attr,uri);
	}
	
	public ReferenceValue(Attribute attr, URI uri) {
		super();
		this.jtype = JsonNodeType.STRING;
		this.value = uri;
		this.attr = attr;
	}


	public ReferenceValue(Attribute attr, JsonNode node, IBulkIdResolver bulkIdResolver) throws SchemaException, ParseException {
		super(attr, node);
		this.resolver = bulkIdResolver;
		
		parseJson(node);
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
	public void parseJson(JsonNode node)
			throws SchemaException, ParseException {
		setUri(attr,node.asText());
	}

	@Override
	public JsonNode toJsonNode(ObjectNode parent, String aname) {
		if (parent == null)
			parent = JsonUtil.getMapper().createObjectNode();
		parent.put(aname,toString());
		return parent;
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
	public URI getRawValue() {
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
			ReferenceValue obVal = (ReferenceValue) obj;
			return obVal.value.equals(value);
		}
		return false;
	}

	@Override
	public int compareTo(Value o) {
		if (o instanceof ReferenceValue) {
			ReferenceValue obVal = (ReferenceValue) o;
			return value.compareTo(obVal.value);
		}
		throw new ClassCastException("Unable to compare Value types");
	}

}
