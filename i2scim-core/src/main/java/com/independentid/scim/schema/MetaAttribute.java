/*
 * Copyright (c) 2021.
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

package com.independentid.scim.schema;

import com.independentid.scim.resource.Meta;
import com.independentid.scim.security.AccessControl;

/**
 * This class definition created due to loading conflict (causing NPEs). Meta is now defined in class rather then dynamic loading.
 * This enables a loading conflict to be resolved between AccessManager and SchemaManager. The attribute returned
 * should be identical to the one loaded dynamically and found via SchemaManager
 */
public class MetaAttribute extends Attribute {

    static Attribute attr = new MetaAttribute();

    private MetaAttribute() {
        super(Meta.META);
        setPath(SchemaManager.SCIM_CORE_SCHEMAID, Meta.META);
        setType(Attribute.TYPE_Complex);
        setMultiValued(false);
        setReturned(Attribute.RETURNED_default);
        setMutability(Attribute.MUTABILITY_readOnly);

        Attribute revisions = new Attribute(Meta.META_REVISIONS);
        revisions.setPath(SchemaManager.SCIM_CORE_SCHEMAID,Meta.META);
        revisions.setMultiValued(true);
        revisions.setType(Attribute.TYPE_Complex);
        revisions.setRequired(false);
        revisions.setReturned(Attribute.RETURNED_request);
        revisions.setMutability(Attribute.MUTABILITY_readOnly);

        this.setSubAttribute(revisions);

            Attribute rdate = new Attribute("date");
            rdate.setPath(SchemaManager.SCIM_CORE_SCHEMAID,revisions.getPath());
            revisions.setSubAttribute(rdate);
            rdate.setType(Attribute.TYPE_Date);
            rdate.setReturned(Attribute.RETURNED_default);
            rdate.setMutability(Attribute.MUTABILITY_readOnly);

            Attribute rvalue = new Attribute("value");
            rvalue.setPath(SchemaManager.SCIM_CORE_SCHEMAID,revisions.getPath());
            revisions.setSubAttribute(rvalue);
            rvalue.setType(Attribute.TYPE_String);
            rvalue.setReturned(Attribute.RETURNED_default);
            rdate.setMutability(Attribute.MUTABILITY_readOnly);

        Attribute acis = new Attribute(Meta.META_ACIS);
        acis.setPath(SchemaManager.SCIM_CORE_SCHEMAID,Meta.META);
        acis.setMultiValued(true);
        acis.setType(Attribute.TYPE_Complex);
        acis.setRequired(false);
        acis.setReturned(Attribute.RETURNED_request);
        acis.setMutability(Attribute.MUTABILITY_readOnly);

        this.setSubAttribute(acis);

            Attribute path = new Attribute(AccessControl.FIELD_PATH);
            path.setPath(SchemaManager.SCIM_CORE_SCHEMAID,acis.getPath());
            acis.setSubAttribute(path);
            path.setType(Attribute.TYPE_String);
            path.setMutability(Attribute.MUTABILITY_readOnly);
            path.setReturned(Attribute.RETURNED_default);
            path.setCaseExact(true);

            Attribute name = new Attribute(AccessControl.FIELD_NAME);
            name.setPath(SchemaManager.SCIM_CORE_SCHEMAID,acis.getPath());
            acis.setSubAttribute(name);
            name.setType(Attribute.TYPE_String);
            name.setMutability(Attribute.MUTABILITY_readOnly);
            name.setReturned(Attribute.RETURNED_default);
            name.setCaseExact(true);

            Attribute tFilter = new Attribute(AccessControl.FIELD_TARGET_FILTER);
            tFilter.setPath(SchemaManager.SCIM_CORE_SCHEMAID,acis.getPath());
            acis.setSubAttribute(tFilter);
            tFilter.setType(Attribute.TYPE_String);
            tFilter.setMutability(Attribute.MUTABILITY_readOnly);
            tFilter.setReturned(Attribute.RETURNED_default);
            tFilter.setCaseExact(false);

            Attribute tAttrs = new Attribute(AccessControl.FIELD_TARGETATTRS);
            tAttrs.setPath(SchemaManager.SCIM_CORE_SCHEMAID,acis.getPath());
            acis.setSubAttribute(tAttrs);
            tAttrs.setType(Attribute.TYPE_String);
            tAttrs.setMutability(Attribute.MUTABILITY_readOnly);
            tAttrs.setReturned(Attribute.RETURNED_default);
            tAttrs.setCaseExact(false);

            Attribute rights = new Attribute(AccessControl.FIELD_RIGHTS);
            rights.setPath(SchemaManager.SCIM_CORE_SCHEMAID,acis.getPath());
            acis.setSubAttribute(rights);
            rights.setType(Attribute.TYPE_String);
            rights.setMutability(Attribute.MUTABILITY_readOnly);
            rights.setReturned(Attribute.RETURNED_default);
            rights.setCaseExact(false);

            Attribute actors = new Attribute(AccessControl.FIELD_ACTORS);
            actors.setPath(SchemaManager.SCIM_CORE_SCHEMAID,acis.getPath());
            acis.setSubAttribute(actors);
            actors.setType(Attribute.TYPE_String);
            actors.setReturned(Attribute.RETURNED_default);
            actors.setCaseExact(false);
            actors.setMutability(Attribute.MUTABILITY_readOnly);

        Attribute location = new Attribute(Meta.META_LOCATION);
        location.setPath(SchemaManager.SCIM_CORE_SCHEMAID,Meta.META);
        this.setSubAttribute(location);
        location.setMultiValued(false);
        location.setCaseExact(true);
        location.setType(Attribute.TYPE_Reference);
        location.setMutability(Attribute.MUTABILITY_readOnly);
        location.setReturned(Attribute.RETURNED_default);

        Attribute created = new Attribute(Meta.META_CREATED);
        created.setPath(SchemaManager.SCIM_CORE_SCHEMAID,Meta.META);
        this.setSubAttribute(created);
        created.setMultiValued(false);
        created.setCaseExact(true);
        created.setType(Attribute.TYPE_Date);
        created.setMutability(Attribute.MUTABILITY_readOnly);
        created.setReturned(Attribute.RETURNED_default);

        Attribute modified = new Attribute(Meta.META_LAST_MODIFIED);
        modified.setPath(SchemaManager.SCIM_CORE_SCHEMAID,Meta.META);
        this.setSubAttribute(modified);
        modified.setMultiValued(false);
        modified.setCaseExact(true);
        modified.setType(Attribute.TYPE_Date);
        modified.setMutability(Attribute.MUTABILITY_readOnly);
        modified.setReturned(Attribute.RETURNED_default);

        Attribute rType = new Attribute(Meta.META_RESOURCE_TYPE);
        rType.setPath(SchemaManager.SCIM_CORE_SCHEMAID,Meta.META);
        this.setSubAttribute(rType);
        rType.setMultiValued(false);
        rType.setCaseExact(false);
        rType.setType(Attribute.TYPE_String);
        rType.setMutability(Attribute.MUTABILITY_readOnly);
        rType.setReturned(Attribute.RETURNED_default);

        Attribute vers = new Attribute(Meta.META_VERSION);
        vers.setPath(SchemaManager.SCIM_CORE_SCHEMAID,Meta.META);
        this.setSubAttribute(vers);
        vers.setMultiValued(false);
        vers.setCaseExact(false);
        vers.setType(Attribute.TYPE_String);
        vers.setMutability(Attribute.MUTABILITY_readOnly);
        vers.setReturned(Attribute.RETURNED_default);
    }

    static public Attribute getMeta() {
        return attr;
    }
}
