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

package com.independentid.scim.backend.mongo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.*;
import com.independentid.scim.schema.*;
import com.independentid.scim.resource.Meta;
import com.independentid.scim.serializer.JsonUtil;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.internal.Base64;
import org.bson.json.JsonWriterSettings;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Pattern;


/**
 * @author pjdhunt A general SCIM / MongoDb bidirectional mapping utility.
 */
//@ApplicationScoped
@Singleton
public class MongoMapUtil {
    private final static Logger logger = LoggerFactory.getLogger(MongoMapUtil.class);

    public final static Pattern hrefPattern = Pattern.compile("\\\"href\\\"");

    @Inject
    SchemaManager schemaManager;

    @ConfigProperty(name = "scim.prov.mongo.indexes", defaultValue=MongoProvider.DEFAULT_MONGO_INDEXES)
    String[] indexes;

    /**
     * Converts a <ScimResource> object to a Mongo <Document>. Conversion does not modify original ScimResource.
     * Performs necessary "id" to "_id" conversion.
     * @param res The <ScimResource> object to be mapped to Mongo.
     * @return A <Document> translation of the provided SCIM resource.
     */
    public static Document mapResource(final ScimResource res) {
        Document doc = new Document();
        if (res.getId() == null)
            doc.put("_id", new ObjectId());
        else
            doc.put("_id", new ObjectId(res.getId()));

        doc.put("schemas", res.getSchemaURIs());

        if (res.getExternalId() != null)
            doc.put("externalId", res.getExternalId());

        if (res.getMeta() != null) {
            // Should always be a meta!
            Meta meta = res.getMeta();
            Document metaDoc = new Document();

            if (meta.getCreatedDate() != null)
                metaDoc.append(Meta.META_CREATED, meta.getCreatedDate());
            if (meta.getLastModifiedDate() != null)
                metaDoc.append(Meta.META_LAST_MODIFIED, meta.getLastModifiedDate());

            if (meta.getResourceType() != null)
                metaDoc.append(Meta.META_RESOURCE_TYPE, meta.getResourceType());

            if (meta.getLocation() != null)
                metaDoc.append(Meta.META_LOCATION, meta.getLocation());

            if (meta.getVersion() != null)
                metaDoc.append(Meta.META_VERSION, meta.getVersion());

            if (meta.getRevisions() != null)
                metaDoc.append(Meta.META_REVISIONS,MongoMapUtil.mapValue(meta.getRevisions()));

            doc.append("meta", metaDoc);
        }

        Map<String, Object> map = mapCoreAttributes(res);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            doc.put(key, value);
        }

        Map<String, Document> emap = mapExtensions(res);
        emap.forEach(doc::put);
        return doc;
    }

    public static Map<String, Object> mapCoreAttributes(final ScimResource res) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();

        Map<Attribute, Value> cmap = res.getCoreAttrVals();
        cmap.forEach((aname, val) -> {
            String mname = aname.getName();
            // $ref is special in Mongo, rename the field to href
            if (mname.equals("$ref"))
                mname = "href";
            map.put(mname, MongoMapUtil.mapValue(val));
        });

        return map;
    }

    /**
     * Returns a Map of Document. The keys are the encoded schema URIs for the extension schemas and Document holds the
     * attributes for that extension.
     * @param res The ScimResource whose extension schemas are to be mapped.
     * @return A Map<String,Document> whose keys are the encoded URIs and the value is a DBObject containing mapped
     * claims.
     */
    public static Map<String, Document> mapExtensions(final ScimResource res) {
        Map<String, Document> map = new LinkedHashMap<>();
        Map<String, ExtensionValues> cmap = res.getExtensions();
        cmap.forEach((aname, ext) -> {
            String mname = mapExtensionId(aname);
            // $ref is special in Mongo, rename the field to href

            map.put(mname, MongoMapUtil.mapValue(ext));
        });

        return map;
    }

    public static String mapExtensionId(String extensionId) {
        return ScimResource.SCHEMA_EXT_PREFIX + Base64.encode(extensionId.getBytes());
    }

    /**
     * Maps any Scim <Value> or sub-class into a corresponding BSON <Object>
     * @param val A SCIM <Value> to be mapped (e.g. StringValue, BooleanValue, ReferenceValue, MultiValue etc)
     * @return A corresponding Java or Mongo BSON <Object>  (e.g. String, Boolean, URI, <Document>)
     */
    public static Object mapValue(Value val) {
        if (val instanceof MultiValue)
            return mapValue((MultiValue) val);
        if (val instanceof ComplexValue)
            return mapValue((ComplexValue) val);

        if (val instanceof StringValue)
            return mapValue((StringValue) val);
        if (val instanceof DateValue)
            return mapValue((DateValue) val);
        if (val instanceof ReferenceValue)
            return mapValue((ReferenceValue) val);

        if (val instanceof IntegerValue)
            return mapValue((IntegerValue) val);
        if (val instanceof DecimalValue)
            return mapValue((DecimalValue) val);

        if (val instanceof BinaryValue)
            return mapValue((BinaryValue) val);
        if (val instanceof BooleanValue)
            return mapValue((BooleanValue) val);

        logger.warn("\n\n\n***** Mapping object to mongo using default type: " + val.getClass().toString() + " = " + val.toString());
        return val.getValueArray();
    }

    public static Object mapValue(StringValue val) {
        return val.getValueArray();
    }

    public static Object mapValue(DateValue val) {
        return val.getDateValue();
    }

    public static Object mapValue(BinaryValue val) {
        return new Binary(val.getValueArray());
    }

    public static Object mapValue(BooleanValue val) {
        return val.getValueArray();
    }

    public static Document mapValue(ComplexValue val) {
        Document doc = new Document();
        Map<Attribute, Value> map = val.getValueArray();
        map.forEach((aname, item) -> {
            String mname = aname.getName();
            // $ref is special in Mongo, rename the field to href
            if (mname.equals("$ref"))
                mname = "href";
            doc.put(mname, MongoMapUtil.mapValue(item));
        });
        return doc;
    }

    public static Object mapValue(MultiValue val) {
        List<Object> values = new ArrayList<>();
        Value[] mvarray = val.getValueArray();
        for (Value value : mvarray) {
            values.add(MongoMapUtil.mapValue(value));
        }

        return values;
    }

    public static Object mapValue(DecimalValue val) {
        return val.getValueArray();
    }

    public static Object mapValue(IntegerValue val) {
        return val.getValueArray();

    }

    public static Object mapValue(ReferenceValue val) {
        String out = val.toString();
        if (out.startsWith("http:/") && !out.startsWith("http://"))
            //if no explicit host, just get rid of protocol as it is assumed relative.
            return out.substring(5);
        else
            return out;
    }

    public static Document mapValue(ExtensionValues val) {
        Document doc = new Document();
        Map<Attribute, Value> map = val.getValueMap();
        map.forEach((attr, value) ->
                doc.put(attr.getName(), mapValue(value)));
        return doc;
    }

    /**
     * Generates and converts a SCIM JsonNode value. Leaves the original document unmodified. Performs necessary "_id"
     * to "id" conversion.
     * @param doc The original Mongo Document to be converted
     * @return a <JsonNode> object containing SCIM object (ready for using in ScimResource constructor)
     * @throws JsonProcessingException May be thrown by Jackson JSON parser.
     */
    public static JsonNode toScimJsonNode(final Document doc) throws JsonProcessingException {

        Document copy = Document.parse(doc.toJson());
        copy.put("id", doc.get("_id").toString());
        copy.remove("_id");

        // Convert href attributes back to $ref
        String jres = copy.toJson(JsonWriterSettings
                .builder()
                .dateTimeConverter(new MongoDateConverter())
                .build());

        if (jres.contains("\"href\""))
            jres = hrefPattern.matcher(jres).replaceAll("\"\\$ref\"");
        //jres = jres.replaceAll("\\\"href\\\"","\"$ref\"");

        return JsonUtil.getJsonTree(jres);
    }

    /**
     * Takes a container BSON Document and maps the requested Attribute to a SCIM Value.
     * @param attr  An Attribute specifying the attribute to be mapped from the Document
     * @param value A java object value (coming from BSON docs) containing a value to be mapped to BSON (e.g. String,
     *              Boolean, Date, URI...)
     * @return A SCIM <Value> object.
     */
    @SuppressWarnings("unchecked")
    public static Value mapBson(Attribute attr, Object value) throws SchemaException {
        if (value instanceof String)
            return mapBson(attr, (String) value);
        if (value instanceof List)
            return mapBson(attr, (List<Object>) value);

        if (value instanceof Boolean)
            return mapBson(attr, (Boolean) value);
        if (value instanceof Date)
            return mapBson(attr, (Date) value);
        if (value instanceof BigDecimal)
            return mapBson(attr, (BigDecimal) value);
        if (value instanceof Integer)
            return mapBson(attr, (Integer) value);
        if (value instanceof URI)
            return mapBson(attr, (URI) value);
        if (value instanceof Binary) {
            return mapBson(attr, (Binary) value);
        }
        if (value instanceof Document && attr.getType().equals(Attribute.TYPE_Complex))
            return mapBsonComplex(attr, (Document) value);

        if (value instanceof byte[])
            return mapBson(attr, (byte[]) value);
        logger.warn("Unmapped attribute type: " + attr.getName() + ", value class: " + value.getClass());
        return null;
    }

    /**
     * Used to map a SCIM Attribute value that is represented in Mongo as a <Document> and returns a SCIM <Value> type
     * object. Note @See <MongoScimResource> to convert an entire Mongo collection <Document> to create a <ScimResource>
     * object.
     * @param attr         The <Attribute> to be retrieved from the containerDoc.
     * @param containerDoc A Mongo BSON <Document> that contains 1 or more sub-objects (attributes) to be mapped.
     * @return A SCIM <Value> object for the requested attr or NULL.
     * @throws SchemaException thrown due to an invalid schema or malformed attribute error
     */
    public static Value mapBsonDocument(final Attribute attr, final Document containerDoc) throws SchemaException {
        Value val = null;
        String name = attr.getName();
        if (name.equals("$ref"))
            name = "href";
        if (!containerDoc.containsKey(name)) return null;

        if (attr.isMultiValued())
            return mapBson(attr, containerDoc.getList(name, Object.class));

        switch (attr.getType()) {
            case Attribute.TYPE_String:
                val = mapBson(attr, containerDoc.getString(name));
                break;
            case Attribute.TYPE_Complex:
                val = mapBsonComplex(attr, containerDoc.get(name, Document.class));
                break;
            case Attribute.TYPE_Boolean:
                val = mapBson(attr, containerDoc.getBoolean(name));
                break;
            case Attribute.TYPE_Date:
                val = new DateValue(attr, containerDoc.getDate(name));
                break;
            case Attribute.TYPE_Binary:
                val = mapBson(attr, Base64.decode(containerDoc.getString(name)));
                break;
            case Attribute.TYPE_Integer:
                val = mapBson(attr, containerDoc.getInteger(name));
                break;
            case Attribute.TYPE_Reference:
                String newUri = containerDoc.getString(name);
                URI uri;
                try {
                    if (newUri.startsWith("urn:"))
                        uri = new URI(newUri);
                    else {
                        // the value is some form of URL
                        URL url;
                        if (newUri.startsWith("/"))
                            url = new URL("http", "localhost", newUri);
                        else
                            url = new URL(newUri);
                        uri = url.toURI();
                    }
                } catch (MalformedURLException | URISyntaxException e) {

                    throw new SchemaException("Invalid url parsed: " + newUri + " for attribute: " + attr.getPath(), e);
                }
                val = mapBson(attr, uri);
                break;
            case Attribute.TYPE_Decimal:
                Object bval = containerDoc.get(name);
                if (bval instanceof Decimal128)
                    val = mapBson(attr, ((Decimal128)bval).bigDecimalValue());
                else  // try string parse
                    val = mapBson(attr,new BigDecimal(containerDoc.getString(name)));

        }

        return val;
    }

    public static IntegerValue mapBson(Attribute attr, Integer value) {
        return new IntegerValue(attr, value);
    }

    public static DecimalValue mapBson(Attribute attr, BigDecimal value) {
        return new DecimalValue(attr, value);
    }

    public static StringValue mapBson(Attribute attr, String value) {
        return new StringValue(attr, value);
    }

    public static BooleanValue mapBson(Attribute attr, Boolean value) {
        return new BooleanValue(attr, value);
    }

    public static DateValue mapBson(Attribute attr, Date value) {
        return new DateValue(attr, value);
    }

    public static BinaryValue mapBson(Attribute attr, byte[] val) {
        return new BinaryValue(attr, val);
    }

    public static BinaryValue mapBson(Attribute attr, Binary val) {
        return new BinaryValue(attr, val.getData());
    }

    public static ReferenceValue mapBson(Attribute attr, URI value) {
        return new ReferenceValue(attr, value);
    }

    /**
     * Converts an Array of objects to a SCIM <MultiValue> representation.
     * @param attr   The multi-value <Attribute> to be represented.
     * @param values A <List> of Java objects to be mapped.
     * @return A <MultiValue> representation of the Array of objects that have been mapped.
     */
    public static MultiValue mapBson(@NotNull final Attribute attr, final List<Object> values) throws SchemaException {

        List<Value> mvals = new ArrayList<>();

        for (Object val : values) {
            mvals.add(mapBson(attr, val));
        }

        return new MultiValue(attr, mvals);
    }

    /**
     * Takes a Mongo <Document> holding a set of attributes to be mapped as a SCIM <ComplexValue>.
     * @param attr The parent <Attribute> that defines the set of sub-attributes to be mapped from the provided doc.
     * @param doc  A <Document> containing one or more sub-attributes to be mapped.
     * @return A <ComplexValue> representation of the Mongo <Document>
     */
    public static ComplexValue mapBsonComplex(@NotNull final Attribute attr, final Document doc) throws SchemaException {
        LinkedHashMap<Attribute, Value> vals = new LinkedHashMap<>();
        Map<String, Attribute> attrs = attr.getSubAttributesMap();
        for (String aname : attrs.keySet()) {
            Attribute sattr = attrs.get(aname);
            String docName = (aname.equals("$ref") ? "href" : aname);
            if (doc.containsKey(docName))
                vals.put(sattr, mapBson(sattr, doc.get(docName)));
        }

        return new ComplexValue(attr, vals);
    }

    /**
     * Using the provided <Document>, looks for a SCIM Schema extension object (which is based64 encoded in Mongo) and
     * converts to a SCIM <ExtensionValues> representation.
     * @param schema       A <Schema> object representing the schema id to be mapped from the provided container
     *                     <Document>.
     * @param containerDoc A <Document> containing the extension schema object to be mapped.
     * @return A SCIM <ExtensionValues> object mapped from the containing <Document>
     */
    public static ExtensionValues mapBsonExtension(Schema schema, final Document containerDoc) {
        String mschema = ScimResource.SCHEMA_EXT_PREFIX + Base64.encode(schema.getId().getBytes());
        Document extDoc = containerDoc.get(mschema, Document.class);
        if (extDoc == null)
            return null;

        LinkedHashMap<Attribute, Value> valMap = new LinkedHashMap<>();
        Attribute[] attrs = schema.getAttributes();
        for (Attribute attr : attrs) {
            try {
                Value val = mapBsonDocument(attr, extDoc);
                if (val != null)
                    valMap.put(attr, val);
            } catch (SchemaException e) {
                logger.warn("Error parsing Mongo document: " + e.getLocalizedMessage(), e);
            }
        }
        return new ExtensionValues(schema, valMap);
    }

    public PersistStateResource mapConfigState(int rcnt, int scnt) {
        return new PersistStateResource(schemaManager,rcnt,scnt);
    }

    public PersistStateResource mapConfigState(Document persistDoc) throws ScimException, ParseException, JsonProcessingException {
        if(persistDoc == null)
            return null;
        String jsonstr = persistDoc.toJson();
        JsonNode jdoc = JsonUtil.getJsonTree(jsonstr);

        return new PersistStateResource(schemaManager,jdoc,null, PersistStateResource.RESTYPE_CONFIG);
    }

    public ScimResource mapScimResource(Document res, String type) throws ScimException, BackendException {
        try {
            return new MongoScimResource(schemaManager, res, type);

        } catch (SchemaException | ParseException e) {
            throw new BackendException(
                    "Unknown parsing exception parsing data from MongoDB."
                            + e.getMessage(), e);
        }

    }

    public Schema mapSchema(Document doc) throws SchemaException, JsonProcessingException {
        JsonNode jdoc = JsonUtil.getJsonTree(doc.toJson());
        return new Schema(schemaManager,jdoc);
    }

    public ResourceType mapResourceType(Document doc) throws JsonProcessingException, SchemaException {
        JsonNode jdoc = JsonUtil.getJsonTree(doc.toJson());
        return new ResourceType(jdoc, schemaManager);
    }

    public Collection<Attribute> getIndexedAttributes() {
        Collection<Attribute> iattrs = new ArrayList<>();

        for (String index : indexes) {
            Attribute attr = schemaManager.findAttribute(index, null);
            iattrs.add(attr);
        }
        return iattrs;
    }


}
