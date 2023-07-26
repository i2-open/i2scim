package com.independentid.signals;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.BulkOps;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.set.SubjectIdentifier;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Objects;

public class SignalsEventMapper {

    private final static Logger logger = LoggerFactory.getLogger(SignalsEventMapper.class);

    public static SecurityEventToken MapOperationToSet(final Operation op) {

        ObjectMapper mapper = JsonUtil.getMapper();

        SecurityEventToken event = new SecurityEventToken();
        String id = op.getRequestCtx().getTranId();
        // TODO: For now we will set txn and jti to be the same.
        event.setTxn(id);
        event.setJti(id);
        NumericDate numDate = NumericDate.fromMilliseconds(op.getStats().getFinishDate().getTime());

        event.setToe(numDate);

        ScimResource res = op.getTransactionResource();
        if (op.getScimType().equals("PUT")) {
            event.SetScimSubjectId(op.getRequestCtx());
        } else {
            if (res != null)
                event.SetScimSubjectId(res);
            else {
                RequestCtx ctx = op.getRequestCtx();
                if (ctx != null)
                    event.SetScimSubjectId(ctx);

                // TODO:  Not able to easily add additional items like externalid for PATCH and DELETE
            }
        }

        event.setTxn(op.getRequestCtx().getTranId());

        ObjectNode payload = mapper.createObjectNode();
        JsonNode resNode;
        switch (op.getScimType()) {
            case "ADD":
                if (res == null) {
                    logger.error("Unexpected null resource for Create op:" + op.toString());
                    return null;
                }
                resNode = res.toJsonNode(op.getRequestCtx());
                if (resNode != null)
                    payload.set("data", resNode);
                event.AddEventPayload("urn:ietf:params:SCIM:event:prov:create:full", payload);

                break;

            case "DEL":
                ObjectNode empty = mapper.createObjectNode();
                event.AddEventPayload("urn:ietf:params:SCIM:event:prov:delete", empty);
                break;

            case "PUT":
                if (res == null) {
                    logger.error("Unexpected null resource for Put op:" + op.toString());
                    return null;
                }
                resNode = res.toJsonNode(op.getRequestCtx());
                payload.set("data", resNode);
                event.AddEventPayload("urn:ietf:params:SCIM:event:prov:put:full", payload);
                break;

            case "PAT":
                PatchOp pop = (PatchOp) op;
                JsonNode patchData = pop.getPatchRequest().toJsonNode();
                payload.set("data", patchData);
                event.AddEventPayload("urn:ietf:params:SCIM:event:prov:patch:full", payload);
                break;
            default:
                return null;
        }
        return event;

    }

    protected static ObjectNode convertToScimInternal(SchemaManager schemaManager, SubjectIdentifier id, String method, String tranId, JsonNode eventNode) {
        ObjectNode bulkOpNode = JsonUtil.getMapper().createObjectNode();
        String path = id.uri;
        if (path == null || Objects.equals(path, "")) {
            ResourceType type = schemaManager.getResourceTypeByName(id.rtype);
            if (type != null) {
                path = "/" + type.getTypePath() + "/" + id.id;
            }
        }
        bulkOpNode.put(BulkOps.PARAM_PATH, path);
        bulkOpNode.set(BulkOps.PARAM_DATA, eventNode.get("data"));
        bulkOpNode.put(BulkOps.PARAM_METHOD, method);

        if (tranId != null) bulkOpNode.put(BulkOps.PARAM_TRANID, tranId);
        return bulkOpNode;
    }

    public static Operation MapSetToOperation(SecurityEventToken event, SchemaManager schemaManager) {
        if (schemaManager == null)
            logger.error("Mapping to Operation: SCHEMA Manager is NULL!!");

        try {
            JsonNode node = event.GetEvents();
            Operation op;

            Iterator<String> evenUriIter = event.getEventUris();
            while (evenUriIter.hasNext()) {
                String eventUri = evenUriIter.next();
                JsonNode payload = event.GetEvent(eventUri);
                ObjectNode bulkOpNode;
                SubjectIdentifier subId = event.getSubjectIdentifier();
                switch (eventUri) {
                    case "urn:ietf:params:SCIM:event:prov:create:full":
                        bulkOpNode = convertToScimInternal(schemaManager, subId, Operation.Bulk_Method_POST, event.getTxn(), payload);
                        return BulkOps.parseOperation(bulkOpNode, null, 0, true);
                    case "urn:ietf:params:SCIM:event:prov:delete":
                        bulkOpNode = convertToScimInternal(schemaManager, subId, Operation.Bulk_Method_DELETE, event.getTxn(), payload);
                        return BulkOps.parseOperation(bulkOpNode, null, 0, true);
                    case "urn:ietf:params:SCIM:event:prov:put:full":
                        bulkOpNode = convertToScimInternal(schemaManager, subId, Operation.Bulk_Method_PUT, event.getTxn(), payload);
                        return BulkOps.parseOperation(bulkOpNode, null, 0, true);
                    case "urn:ietf:params:SCIM:event:prov:patch:full":
                        bulkOpNode = convertToScimInternal(schemaManager, subId, Operation.Bulk_Method_PATCH, event.getTxn(), payload);
                        return BulkOps.parseOperation(bulkOpNode, null, 0, true);
                }
                logger.info("Ignored event: " + eventUri);

            }

        } catch (MalformedClaimException | ScimException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

}
