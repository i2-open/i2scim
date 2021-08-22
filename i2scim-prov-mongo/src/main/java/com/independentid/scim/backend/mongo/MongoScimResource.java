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

package com.independentid.scim.backend.mongo;

import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.*;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.Schema;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.Set;

/**
 * This class wraps the ScimResource class to enable mapping of BSON documents to JSON. This class uses {@link
 * MongoMapUtil} for direct BSON to JSON conversions.
 * @author pjdhunt
 */
public class MongoScimResource extends ScimResource {
    private final static Logger logger = LoggerFactory.getLogger(MongoScimResource.class);

    private Document originalResource;

    /**
     * MongoScimResource wraps ScimResource in order to provide direct Mongo BSON Document mapping.
     * @param schemaManager Handle to the SCIM server SchemaManager instance
     */
    protected MongoScimResource(@NotNull SchemaManager schemaManager) {

        super(schemaManager);
    }

    /**
     * Parses a Mongo BSON Document and converts to ScimResource using {@link MongoMapUtil}.
     * @param schemaManager The SCIM {@link SchemaManager} which holds the Schema definitions
     * @param dbResource    A Mongo {@link Document} object containing the Mongo record to be converted to ScimResource
     * @param container     A String representing the Resource Type path (e.g. Users) for the object. Used to lookup
     *                      {@link com.independentid.scim.schema.ResourceType} and {@link Schema}.
     * @throws SchemaException is thrown when unable to parse data not defined in SCIM {@link Schema} configuration
     * @throws ParseException  is thrown when a known format is invalid (e.g. URI, Date, etc)
     * @throws ScimException   is thrown when a general SCIM protocol error has occurred.
     */
    public MongoScimResource(SchemaManager schemaManager, Document dbResource, String container)
            throws SchemaException, ParseException, ScimException {
        super(schemaManager);
        this.smgr = schemaManager;
        //super(cfg, MongoMapUtil.toScimJsonNode(dbResource), null);

        this.originalResource = dbResource;
        setResourceType(container);
        parseDocument(dbResource);

    }

    protected void parseDocument(Document doc) throws ParseException, ScimException {


        this.schemas = doc.getList("schemas", String.class);
        if (this.schemas == null)
            throw new SchemaException("Schemas attribute missing");

        ObjectId oid = doc.get("_id", ObjectId.class);
        if (oid != null)
            this.id = oid.toString();

        this.externalId = doc.getString("externalId");

        Document mdoc = doc.get("meta", Document.class);
        if (mdoc != null) {
            this.meta = new Meta();
            this.meta.setCreatedDate(mdoc.getDate(Meta.META_CREATED));
            this.meta.setLastModifiedDate(mdoc.getDate(Meta.META_LAST_MODIFIED));
            this.meta.setResourceType(mdoc.getString(Meta.META_RESOURCE_TYPE));
            this.meta.setLocation(mdoc.getString(Meta.META_LOCATION));
            try {
                this.meta.setVersion(mdoc.getString(Meta.META_VERSION));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            Attribute rev = commonSchema.getAttribute(Meta.META).getSubAttribute(Meta.META_REVISIONS);
            this.meta.setRevisions((MultiValue) MongoMapUtil.mapBsonDocument(rev, mdoc));
        }

        parseAttributes(doc);

    }

    protected void parseAttributes(Document doc) throws ScimException, ParseException {
        //ResourceType type = cfg.getResourceType(getResourceType());
        //String coreSchemaId = type.getSchema();

        // Look for all the core schema vals
        //Schema core = cfg.getSchemaById(coreSchemaId);

        Attribute[] attrs = mainSchema.getAttributes();
        for (Attribute attr : attrs) {
            Value val = MongoMapUtil.mapBsonDocument(attr, doc);
            if (smgr.isVirtualAttr(attr)) {
                try {  // convert from value type to virtual type
                    val = smgr.constructValue(this, attr, val);
                    if (val == null)
                        val = smgr.constructValue(this, attr);  // try a calculated attribute
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NullPointerException e) {
                    logger.error("Error mapping attribute " + attr.getName() + ": " + e.getMessage(), e);
                }
            }
            attrsInUse.add(attr);
            if (val != null)
                this.coreAttrVals.put(attr, val);
        }

        String[] eids = type.getSchemaExtension();
        for (String eid : eids) {
            Schema schema = smgr.getSchemaById(eid);
            ExtensionValues val = MongoMapUtil.mapBsonExtension(schema, doc);
            if (val != null) {
                this.extAttrVals.put(eid, val);
                Set<Attribute> eattrs = val.getAttributeSet();
                if (eattrs != null && !eattrs.isEmpty())
                    this.attrsInUse.addAll(eattrs);
            }
        }

    }

    /**
     * @return the original Mongo {@link Document} used to create this {@link ScimResource}.
     */
    public Document getOriginalDBObject() {
        return this.originalResource;
    }

    public Document toMongoDocument(RequestCtx ctx) {
        return toMongoDocument(this, ctx);
    }

    /**
     * Converts a {@link ScimResource} object to a Mongo {@link Document}. Conversion does not modify original
     * ScimResource. Performs necessary "id" to "_id" conversion.
     * @param res The ScimResource object to be converted
     * @param ctx The {@link RequestCtx} indicating the container associated with the resource (usually contains
     *            original query).
     * @return A {@link Document} representing the mapped {@link ScimResource}
     */
    public static Document toMongoDocument(ScimResource res, RequestCtx ctx) {

        return MongoMapUtil.mapResource(res);
    }

}
