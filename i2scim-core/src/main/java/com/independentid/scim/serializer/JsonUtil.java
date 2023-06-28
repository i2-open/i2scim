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

        //mapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true)); // this added because 1.0 gets converted to 1
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
