package com.independentid.scim.serializer;

import java.io.IOException;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.independentid.scim.protocol.ScimResponse;

@JsonComponent
public class ScimResponseSerializer extends JsonSerializer<ScimResponse> {

	//TODO: Is this needed?
	
	@Override
	public void serialize(ScimResponse value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
		// TODO Auto-generated method stub
		throw new IOException("NOT IMPLEMENTED!");
	}

}
