package com.independentid.signals;


import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.backend.IIdentifierGenerator;
import com.independentid.scim.core.InjectionManager;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.BulkOps;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PatchOp;
import com.independentid.scim.protocol.JsonPatchOp;
import com.independentid.scim.protocol.JsonPatchRequest;
import com.independentid.scim.protocol.JsonPath;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.serializer.JsonUtil;
import com.independentid.set.SecurityEventToken;
import com.independentid.set.SubjectIdentifier;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

public class SignalsEventMapper {

    boolean pubAll;
    boolean rcvAll;
    List<String> pubEvents = new ArrayList<>();
    List<String> rcvEvents = new ArrayList<>();
    static IIdentifierGenerator idGen = InjectionManager.getInstance().getGenerator();

    public SignalsEventMapper(List<String> pubCfgEvents, List<String> rcvCfgEvents) {
        pubAll = false;
        rcvAll = false;
        if (pubCfgEvents.size() == 1 && pubCfgEvents.get(0).equals("*") || pubCfgEvents.size() == 0) {
            pubAll = true;
        } else {
            pubEvents.addAll(pubCfgEvents);
        }
        if (rcvCfgEvents.size() == 1 && rcvCfgEvents.get(0).equals("*") || rcvCfgEvents.size() == 0) {
            rcvAll = true;
        } else {
            rcvEvents.addAll(rcvCfgEvents);
        }

    }

    private final static Logger logger = LoggerFactory.getLogger(SignalsEventMapper.class);

    // This class is intended to allow the creation of multiple events sharing the same operation information (e.g. toe, txn)
    private static class EventSet {
        NumericDate date;
        String txn;
        SubjectIdentifier sid;
        List<SecurityEventToken> events = new ArrayList<>();

        public EventSet(SubjectIdentifier sid, String txn, NumericDate date) {
            this.date = date;
            this.txn = txn;
            this.sid = sid;
        }

        public SecurityEventToken newEvent() {
            SecurityEventToken event = new SecurityEventToken();
            event.SetSubjectIdentifier(this.sid);
            event.setTxn(this.txn);
            event.setJti(idGen.getNewIdentifier());
            event.setToe(this.date);
            events.add(event);
            return event;
        }

        public List<SecurityEventToken> getEvents() {
            return events;
        }
    }

    private ObjectNode setAttributes(ScimResource res) {
        ObjectNode payload = JsonUtil.getMapper().createObjectNode();
        ArrayNode names = payload.putArray("attributes");

        Set<Attribute> attrsPresent = res.getAttributesPresent();
        for (Attribute attr : attrsPresent) {
            names.add(attr.getName());
        }
        return payload;
    }

    public List<SecurityEventToken> MapOperationToSet(final Operation op) {
        ObjectMapper mapper = JsonUtil.getMapper();
        ScimResource res = op.getTransactionResource();
        SubjectIdentifier subjectIdentifier;
        if (op.getScimType().equals("PUT")) {
            subjectIdentifier = new SubjectIdentifier(op.getRequestCtx());
        } else {
            if (res != null)
                subjectIdentifier = new SubjectIdentifier(res);
            else {
                subjectIdentifier = new SubjectIdentifier(op.getRequestCtx());
                // TODO:  Not able to easily add additional items like externalid for PATCH and DELETE
            }
        }

        EventSet respEvents = new EventSet(subjectIdentifier, op.getRequestCtx().getTranId(), NumericDate.fromMilliseconds(op.getStats().getFinishDate().getTime()));

        if (!op.isError()) {
            JsonNode resNode;
            SecurityEventToken event;
            switch (op.getScimType()) {
                case "ADD":
                    if (res == null) {
                        logger.error("Unexpected null resource for Create op:" + op);
                        return null;
                    }
                    resNode = res.toJsonNode(op.getRequestCtx());
                    if (isTypePublished(EventTypes.PROV_CREATE_FULL)) {
                        ObjectNode payload = mapper.createObjectNode();
                        event = respEvents.newEvent();

                        if (resNode != null)
                            payload.set("data", resNode);
                        event.AddEventPayload(EventTypes.PROV_CREATE_FULL, payload);
                    }
                    if (isTypePublished(EventTypes.PROV_CREATE_NOTICE)) {
                        ObjectNode payload = setAttributes(res);
                        event = respEvents.newEvent();
                        event.AddEventPayload(EventTypes.PROV_CREATE_NOTICE, payload);
                    }
                    break;

                case "DEL":
                    ObjectNode empty = mapper.createObjectNode();
                    event = respEvents.newEvent();
                    event.AddEventPayload(EventTypes.PROV_DELETE, empty);
                    break;

                case "PUT":
                    if (res == null) {
                        logger.error("Unexpected null resource for Put op:" + op);
                        return null;
                    }
                    resNode = res.toJsonNode(op.getRequestCtx());
                    if (isTypePublished(EventTypes.PROV_PUT_FULL)) {
                        ObjectNode payload = mapper.createObjectNode();
                        event = respEvents.newEvent();
                        payload.set("data", resNode);
                        event.AddEventPayload(EventTypes.PROV_PUT_FULL, payload);
                    }
                    if (isTypePublished(EventTypes.PROV_PUT_NOTICE)) {
                        ObjectNode payload = setAttributes(res);
                        event = respEvents.newEvent();
                        event.AddEventPayload(EventTypes.PROV_PUT_NOTICE, payload);
                    }

                    break;

                case "PAT":
                    PatchOp pop = (PatchOp) op;
                    JsonNode patchData = pop.getPatchRequest().toJsonNode();

                    if (isTypePublished(EventTypes.PROV_PATCH_FULL)) {
                        ObjectNode payload = mapper.createObjectNode();
                        event = respEvents.newEvent();
                        payload.set("data", patchData);
                        event.AddEventPayload(EventTypes.PROV_PATCH_FULL, payload);
                    }
                    if (isTypePublished(EventTypes.PROV_PATCH_NOTICE)) {
                        ObjectNode payload = mapper.createObjectNode();
                        event = respEvents.newEvent();
                        ArrayNode names = payload.putArray("attributes");
                        JsonPatchRequest req = pop.getPatchRequest();
                        for (Iterator<JsonPatchOp> it = req.iterator(); it.hasNext(); ) {
                            JsonPatchOp patchOp = it.next();
                            try {
                                JsonPath path = new JsonPath(patchOp, pop.getRequestCtx());
                                String attrName = path.getTargetAttrName();
                                if (attrName != null && !attrName.equals(""))
                                    names.add(attrName);
                            } catch (ScimException e) {
                                logger.error("Error parsing JSON patch op: " + e.getMessage(), e);
                                break;
                            }
                        }
                        event.AddEventPayload(EventTypes.PROV_PATCH_NOTICE, payload);
                    }

                    break;
                default:
                    return null;
            }

        }
        int status = op.getScimResponse().getStatus();
        // Generate async response event if set
        if (op.getRequestCtx().isAsynchronous()) {
            SecurityEventToken asyncEvent = respEvents.newEvent();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("method", op.getRequest().getMethod());
            payload.put("version", op.getScimResponse().getETag());
            payload.put("status", status);
            payload.put("location", op.getLocation());
            if (status == 400) {
                StringWriter stringWriter = new StringWriter();
                try {
                    JsonGenerator gen = JsonUtil.getGenerator(stringWriter, false);
                    op.getScimResponse().serialize(gen, op.getRequestCtx());
                    JsonNode data = JsonUtil.getJsonTree(stringWriter.toString());
                    stringWriter.close();
                    payload.set("response", data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            asyncEvent.AddEventPayload(EventTypes.MISC_ASYNC_RESP, payload);
        }
        return respEvents.getEvents();

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

    protected boolean isTypeReceived(String type) {
        return (rcvAll || rcvEvents.contains(type));
    }

    protected boolean isTypePublished(String type) {
        return (pubAll || pubEvents.contains(type));
    }

    public Operation MapSetToOperation(SecurityEventToken event, SchemaManager schemaManager) {
        if (schemaManager == null)
            logger.error("Mapping to Operation: SCHEMA Manager is NULL!!");

        try {
            Iterator<String> evenUriIter = event.getEventUris();
            while (evenUriIter.hasNext()) {
                String eventUri = evenUriIter.next();
                if (!isTypeReceived(eventUri))
                    continue; // skip the event if not configured to receive it
                JsonNode payload = event.GetEvent(eventUri);
                ObjectNode bulkOpNode;
                SubjectIdentifier subId = event.getSubjectIdentifier();
                switch (eventUri) {
                    case EventTypes.PROV_CREATE_FULL:
                        bulkOpNode = convertToScimInternal(schemaManager, subId, Operation.Bulk_Method_POST, event.getTxn(), payload);
                        return BulkOps.parseOperation(bulkOpNode, null, 0, true);
                    case EventTypes.PROV_DELETE:
                        bulkOpNode = convertToScimInternal(schemaManager, subId, Operation.Bulk_Method_DELETE, event.getTxn(), payload);
                        return BulkOps.parseOperation(bulkOpNode, null, 0, true);
                    case EventTypes.PROV_PUT_FULL:
                        bulkOpNode = convertToScimInternal(schemaManager, subId, Operation.Bulk_Method_PUT, event.getTxn(), payload);
                        return BulkOps.parseOperation(bulkOpNode, null, 0, true);
                    case EventTypes.PROV_PATCH_FULL:
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
