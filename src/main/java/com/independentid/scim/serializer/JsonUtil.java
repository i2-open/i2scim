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

package com.independentid.scim.serializer;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.independentid.scim.resource.Meta;

import java.io.*;

public class JsonUtil {

	static final JsonFactory jFact = new JsonFactory();
		
	static final ObjectMapper mapper;
	
	static {
		mapper = new ObjectMapper();
		mapper.setDateFormat(Meta.ScimDateFormat);
		mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
		mapper.setSerializationInclusion(Include.NON_EMPTY);
	}
	
	public static JsonGenerator getGenerator(Writer writer, boolean compact) throws IOException {
		if (writer == null)
			writer = new StringWriter();
		
        JsonGenerator gen = jFact.createGenerator(writer);
        if (!compact) 
        	gen.useDefaultPrettyPrinter();
        
        return gen;
	}
	
	/**
	 * Returns a handle to the FasterXML mapper.
	 * @return ObjectMapper instance handle
	 */
	public static ObjectMapper getMapper() {		
		return mapper;
	}
	
	/**
	 * Utility - reads a File containing JSON structure and returns a JsonNode object;
	 * @param filepath The path to a file containing JSON data to be parsed
	 * @return JsonNode containing parsed JSON structure.
	 * @throws JsonProcessingException May be thrown trying to parse JSON data
	 * @throws IOException May be thrown trying to read file
	 */
	public static com.fasterxml.jackson.databind.JsonNode getJsonTree(File filepath) throws JsonProcessingException, IOException {
		
		return JsonUtil.getMapper().readTree(filepath);
	}
	
	/**
	 * Utility - parses an input String containing JSON structure and returns a JsonNode object;
	 * @param jsonStr A String containing a JSON structure to be parsed
	 * @return JsonNode containing parsed JSON structure.
	 * @throws JsonProcessingException May be thrown parsing JSON string into a JsonNode tree
	 */
	public static JsonNode getJsonTree(String jsonStr) throws JsonProcessingException {
		return JsonUtil.getMapper().readTree(jsonStr);
	}
	
	/**
	 * Utility - Parses an InputStream containing JSON structure and returns a JsonNode object;
	 * @param instream InputStream to be parsed into JSON
	 * @return JsonNode containing parsed JSON structure.
	 * @throws JsonProcessingException Maybe thrown mapping the JSON Tree
	 * @throws IOException May be thrown reading InputStream
	 */
	public static JsonNode getJsonTree(InputStream instream) throws IOException {
		return JsonUtil.getMapper().readTree(instream);  
	}
	
	public static JsonNode getJsonTree(byte[] inbytes) throws IOException {
		return JsonUtil.getMapper().readTree(inbytes); 
	}

}
