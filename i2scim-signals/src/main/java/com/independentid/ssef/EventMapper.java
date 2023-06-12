package com.independentid.ssef;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class EventMapper {
    private final static Logger logger = LoggerFactory.getLogger(EventMapper.class);
    public static JsonNode MapOperation(final Operation op) {

        ObjectMapper mapper = JsonUtil.getMapper();

        ObjectNode tokenNode = mapper.createObjectNode();

        tokenNode.put("toe",op.getStats().getFinishDateStr());
        tokenNode.put("txn",op.getRequestCtx().getTranId());

        ObjectNode subid = mapper.createObjectNode();

        ScimResource res = op.getTransactionResource();
        if (res.getExternalId() != null && res.getExternalId() != "") {
           subid.put("externalId",res.getExternalId());
        } else {
            try {
                Value val = res.getValue("username");
                subid.put("username",val.toString());
            } catch (SchemaException ignore) {

            }
        }
        tokenNode.put("sub_id",subid);


        ObjectNode event = mapper.createObjectNode();
        ObjectNode dataNode = mapper.createObjectNode();
        switch(op.getScimType()) {
            case "ADD":
                JsonNode rdata = res.toJsonNode(op.getRequestCtx());

                ObjectNode data = JsonUtil.getMapper().createObjectNode();
                data.put("data",rdata);

                event.put("urn:ietf:params:event:SCIM:prov:create",rdata);
                break;

            case "DEL":
                ObjectNode empty = mapper.createObjectNode();
                event.put("urn:ietf:params:event:SCIM:prov:delete",empty);
                break;

            case "PUT":
                JsonNode resNode = res.toJsonNode(op.getRequestCtx());
                dataNode.put("data",resNode);
                event.put("urn:ietf:params:event:SCIM:prov:put",dataNode);
                break;

            case "PAT":
                PatchOp pop = (PatchOp) op;
                JsonNode patchData = pop.getPatchRequest().toJsonNode();
                dataNode.put("data",patchData);
                event.put("urn:ietf:params:event:SCIM:prov:patch",dataNode);
                break;
            default:
                return null;
        }
        ArrayNode eventArray = mapper.createArrayNode();
        eventArray.add(event);
        tokenNode.put("events",eventArray);
        return tokenNode;

    }

    public static JsonNode ParseJwt(String JwtVal) {


        Operation op;
        try {
            JwtClaims claims = JwtClaims.parse(JwtVal);
            Map<String,Object> events = claims.getClaimValue("events",Map.class);

            for (Map.Entry<String,Object> event: events.entrySet()) {
                logger.info("event object is: "+event.getValue().getClass().getName());
                switch (event.getKey()) {
                    case "urn:ietf:params:event:SCIM:prov:create":

                    case "urn:ietf:params:event:SCIM:prov:delete":
                    case "urn:ietf:params:event:SCIM:prov:put":
                    case "urn:ietf:params:event:SCIM:prov:patch":

                }
            }
            String jsonString = claims.toJson();





        } catch (InvalidJwtException e) {
            throw new RuntimeException(e);
        } catch (MalformedClaimException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
