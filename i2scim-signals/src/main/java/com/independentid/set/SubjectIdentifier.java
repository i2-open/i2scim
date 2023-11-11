package com.independentid.set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.ResourceType;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.serializer.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONAware;

@JsonPropertyOrder({"format", "id", "username", "email"})
public class SubjectIdentifier {
    @JsonProperty("format")
    public String format;
    @JsonProperty("rtype")
    public String rtype;
    @JsonProperty("uri")
    public String uri;  // UniformResourceIdentifier
    @JsonProperty("email")
    public String email;
    @JsonProperty("externalId")
    public String externalId;
    @JsonProperty("username")
    public String username;

    @JsonProperty("id")
    public String id;  // OpaqueIdentifier
    @JsonProperty("phoneNumber")
    public String phoneNumber;
    @JsonProperty("url")
    public String url;  // DecentralizedId

    @JsonProperty("sub")
    public String sub;  // old fashioned sub identifier (from JWT)

    public SubjectIdentifier() {
    }

    public SubjectIdentifier(ScimResource res) {
        this.format = "scim";
        this.id = res.getId();
        this.uri = "/" + res.getContainer() + "/" + res.getId();
        this.rtype = res.getResourceType();
        if (res.getExternalId() != null) this.externalId = res.getExternalId();

        try {
            Value usernameValue = res.getValue("Username");
            if (usernameValue != null) this.username = usernameValue.toString();
        } catch (SchemaException ignore) {
        }
    }

    public SubjectIdentifier(RequestCtx ctx) {
        this.format = "scim";
        this.id = ctx.getPathId();
        this.uri = ctx.getPath();
        ResourceType type = ctx.getSchemaMgr().getResourceTypeByPath(ctx.getResourceContainer());
        if (type != null) this.rtype = type.getId();
    }

    public static SubjectIdentifier NewSubjectIdentifier(String format) {
        SubjectIdentifier sid = new SubjectIdentifier();
        sid.format = format;
        return sid;
    }

    @JsonSetter("format")
    public void setFormat(String format) {
        this.format = format;
    }

    @JsonSetter("username")
    public SubjectIdentifier setUsername(String username) {
        this.username = username;
        return this;
    }

    @JsonSetter("email")
    public SubjectIdentifier setEmail(String email) {
        this.email = email;
        return this;
    }

    @JsonSetter("externalId")
    public SubjectIdentifier setExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    @JsonSetter("id")
    public SubjectIdentifier setId(String id) {
        this.id = id;
        return this;
    }


    private void addAttrNode(ObjectNode parent, String name, String value) {
        if (value == null) return;
        parent.put(name, value);
    }

    public JsonNode toJsonNode() {
        ObjectNode node = JsonUtil.getMapper().createObjectNode();

        node.put("format", this.format);
        addAttrNode(node, "rtype", this.rtype);
        addAttrNode(node, "uri", this.uri);
        addAttrNode(node, "id", this.id);
        addAttrNode(node, "externalId", this.externalId);
        addAttrNode(node, "username", this.username);

        addAttrNode(node, "email", this.email);
        addAttrNode(node, "sub", this.sub);
        addAttrNode(node, "phone_number", this.phoneNumber);
        return node;
    }

    public String toPrettyString() {
        return toJsonNode().toPrettyString();
    }

    public String toJsonString() {
        return toJsonNode().toString();
    }


}
