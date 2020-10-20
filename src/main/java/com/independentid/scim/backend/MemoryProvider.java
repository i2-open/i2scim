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
package com.independentid.scim.backend;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.ListResponse;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Meta;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.InvalidValueException;
import com.independentid.scim.server.ScimException;

/**
 * @author pjdhunt
 *
 */
@Repository("MemoryDao")
public class MemoryProvider implements IScimProvider {

	private static final Logger logger = LoggerFactory.getLogger(MemoryProvider.class);

	public final static String PARAM_PERSIST_FILE = "scim.memory.persist.file";
	public final static String DEFAULT_FILE = "~/scimdata.json";
	
	private HashMap<String,ScimResource> mainMap;
	
	@Resource(name="ConfigMgr")
	private ConfigMgr config;
	
	private boolean ready = false;
	
	@Value("${scim.memdao.memoryFile:scimdata.json}")
	private String storeFile;
	
	/**
	 * 
	 */
	public MemoryProvider() {
		this.mainMap = new HashMap<String,ScimResource>();
		this.ready = false;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#create(com.independentid.scim.protocol.RequestCtx, com.independentid.scim.resource.ScimResource)
	 */
	@Override
	public ScimResponse create(RequestCtx ctx, ScimResource res) throws ScimException,BackendException {
		UUID uid = UUID.randomUUID();
		String sid = uid.toString();
		res.setId(sid);
		
		Meta meta = res.getMeta();
		Date created = new Date();
		meta.setCreatedDate(created);
		meta.setLastModifiedDate(created);
		StringBuffer buf = new StringBuffer();
		buf.append('/');
		buf.append(ctx.getResourceContainer());
		buf.append('/');
		buf.append(sid);
		meta.setLocation(buf.toString());
		
		
		//ServletContext sctx = ctx.getServletContext();
						
		String path = ctx.getResourceContainer();
		ResourceType type = config.getResourceTypeByPath(path);
		if (type != null)
			meta.setResourceType(type.getName());
		
		
		if (meta.getResourceType() == null)
			throw new BackendException("Unable to determine object resource endpoint");
		
		this.mainMap.put(res.getId(), res);
		
		ListResponse resp = new ListResponse(res,ctx);
		resp.setStatus(ScimResponse.ST_CREATED);
		resp.setLocation(res.getMeta().getLocation());
		if (logger.isDebugEnabled())
			logger.debug("Added: \n"+res.toString()+"\n");
		return resp;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#get(com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public ScimResponse get(RequestCtx ctx) throws ScimException {
		String id = ctx.getPathId();
		if (id == null) {
			
			// TODO Filtered search not implemented on GET!!
			
			ArrayList<ScimResource> vals = new ArrayList<ScimResource>();
			vals.addAll(this.mainMap.values());
			if (logger.isDebugEnabled()) {
				StringBuffer buf = new StringBuffer();
				buf.append("Search: \nURL:\t").append(ctx.getPath());
				buf.append("\nMatches:\t").append(vals.size());
				logger.debug(buf.toString());
			}
			return new ListResponse(vals,ctx);
		}
		ScimResource res = this.mainMap.get(id);
		if (res == null) {
			ScimResponse resp = new ScimResponse();
			resp.setStatus(ScimResponse.ST_NOTFOUND);
			resp.setDetail("Search Path: "+ctx.getPath()+" Filter="+
			((ctx.getFilter() != null)?ctx.getFilter().getFilterStr():"NONE"));
			if (logger.isDebugEnabled()) {
				StringBuffer buf = new StringBuffer();
				buf.append("Search: \nURL:\t").append(ctx.getPath());
				buf.append("\nMatches:\t").append("NO MATCH");
				logger.debug(buf.toString());
			}
			return resp;
		}
		
		if (logger.isDebugEnabled()) {
			StringBuffer buf = new StringBuffer();
			buf.append("Search: \nURL:\t").append(ctx.getPath());
			buf.append("\nMatches:\t").append("1");
			logger.debug(buf.toString());
		}
		return new ListResponse(res,ctx);
	}
	
	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#getResource(com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public ScimResource getResource(RequestCtx ctx) throws ScimException,
			BackendException {
		String id = ctx.getPathId();
		if (id == null)
			throw new InvalidValueException("Missing resource identifier exception");
		
		ScimResource res = this.mainMap.get(id);
		if (logger.isDebugEnabled())
			logger.debug("Get Resource: \n"+res.toString()+"\n");
		return res;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#replace(com.independentid.scim.protocol.RequestCtx, com.independentid.scim.resource.ScimResource)
	 */
	@Override
	public ScimResponse replace(RequestCtx ctx, ScimResource replaceResource) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#patch(com.independentid.scim.protocol.RequestCtx, com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	public ScimResponse patch(RequestCtx ctx, JsonPatchRequest req) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#bulkRequest(com.independentid.scim.protocol.RequestCtx, com.fasterxml.jackson.databind.JsonNode)
	 */
	@Override
	public ScimResponse bulkRequest(RequestCtx ctx, JsonNode node) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#delete(com.independentid.scim.protocol.RequestCtx)
	 */
	@Override
	public ScimResponse delete(RequestCtx ctx) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#init(javax.servlet.ServletConfig)
	 */
	@Override
	public void init() {
		
		//String file = cfg.getInitParameter(PARAM_PERSIST_FILE);
	    String file = this.storeFile;
		if (file == null)
			file = DEFAULT_FILE;
		
		this.storeFile = file;
		
		File data = new File(file);
		if (!data.exists()) {
			System.err.println("Memory store file not found. Initializing new file: "+file);
			return;  // assume it is a new file
		}
				
		try {
			JsonNode node = JsonUtil.getJsonTree(data);
			Iterator<JsonNode> iter = node.iterator();
			while (iter.hasNext()) {
				JsonNode resNode = iter.next();
				ScimResource res;
				try {
					res = new ScimResource(config,resNode, null, null);
					this.mainMap.put(res.getId(), res);
				} catch (SchemaException | ScimException | ParseException e) {
					System.err.println("Error parsing SCIM resource");
					e.printStackTrace();
				} 
				
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.ready = true;

	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#ready()
	 */
	@Override
	public boolean ready() {
		return this.ready;
	}

	/* (non-Javadoc)
	 * @see com.independentid.scim.backend.PersistenceProvider#shutdown()
	 */
	@Override
	public synchronized void shutdown() {
		this.ready = false;
		
		if (storeFile == null)
			storeFile = DEFAULT_FILE;
		
		File data = new File(storeFile);
		if (!data.exists()) {
			try {
				data.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		try {
			FileWriter writer = new FileWriter(data);
			ObjectMapper mapper = new ObjectMapper();
			mapper.setDateFormat(Meta.ScimDateFormat);
			mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
			mapper.setSerializationInclusion(Include.NON_EMPTY);
			JsonFactory jsonFactory = new JsonFactory();
			JsonGenerator gen = jsonFactory.createGenerator(writer);
			gen.useDefaultPrettyPrinter();
			RequestCtx ctx = null;
			try {
				ctx = new RequestCtx(null,null, false);
			} catch (ScimException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // just create a dummy for now
			Iterator<ScimResource> iter = this.mainMap.values().iterator();
			while (iter.hasNext())
				try {
					iter.next().serialize(gen, ctx, false);
				} catch (ScimException e) {
					logger.error("Error during shutdown. "+e.getLocalizedMessage(),e);
				}
			gen.close();
			writer.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}

	@Override
	public Collection<Schema> loadSchemas() throws ScimException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<ResourceType> loadResourceTypes() throws ScimException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void syncConfig(Collection<Schema> schemaCol, Collection<ResourceType> resTypeCol) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
