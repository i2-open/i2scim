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

package com.independentid.scim.security;

import com.independentid.scim.op.CreateOp;
import com.independentid.scim.op.DeleteOp;
import com.independentid.scim.op.Operation;
import com.independentid.scim.op.PutOp;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.schema.Attribute;
import io.quarkus.security.identity.SecurityIdentity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An AciSet contains a set of ACIs applicable to a specific SCIM path. Each set only has one SCIM method (right)
 * at a time. The set also provides the combined set of attributes allowed for a transaction based on the client requesting.
 */
public class AciSet {
    public final String path;

    /*
     * ArrayList is used because the order of insertion matters.
     */
    private final ArrayList<AccessControl> acis = new ArrayList<>();
    public final ArrayList<AccessControl> readAcis = new ArrayList<>();

    private final AccessControl.Rights right;

    public final HashSet<Attribute> opAttrsAllowed = new HashSet<>();
    public final HashSet<Attribute> opAttrsExcluded = new HashSet<>();
    public final HashSet<Attribute> retAttrsAllowed = new HashSet<>();
    public final HashSet<Attribute> retAttrsExcluded = new HashSet<>();

    public boolean isOpAllAttrs = false;
    public boolean isReadAllAttrs = false;

    public AciSet(String path, AccessControl.Rights right) {
        this.right = right;
        this.path = path;
    }

    public AccessControl.Rights getRight() {
        return this.right;
    }

    /**
     * Checks an attribute provided against the targetAttr value to see if the attribute is allowed for the ACI.
     * @param attr The Attribute to be evaluated
     * @return true If attribute allowed or all attributes wildcarded. Also returns true if targetAttrs is unspecified.
     */
    public boolean isAttributeNotAllowed(Attribute attr) {
        if (isOpAllAttrs)
            return opAttrsExcluded.contains(attr);

        return !opAttrsAllowed.contains(attr);
    }

    /**
     * Called to check if the attributes used in a filter are allowed.
     * @param attributeSet The set of {@link Attribute}s used in a query filter
     * @return true if all attributes are allowed.
     */
    public boolean isAttrSetAllowed(Set<Attribute> attributeSet) {

        for (Attribute attribute : attributeSet) {
            if (isAttributeNotAllowed(attribute))
                return false;
        }
        return true;

    }

    public boolean isAttrNotReturnable(Attribute attr) {
        if (isReadAllAttrs)
            return retAttrsExcluded.contains(attr);

        return !retAttrsAllowed.contains(attr);
    }

    public boolean isTargetFilterAllowed(ScimResource res) {
        for (AccessControl aci : this.acis) {
            if (aci.isTargetFilterOk(res))
                return true;
        }
        return false;
    }

    /**
     * Generates a copy of the ACIset.
     * @return A safe copy of the current AciSet.
     */
    public AciSet copy() {
        AciSet clone = new AciSet(this.path,this.right);
        clone.opAttrsAllowed.addAll(this.opAttrsAllowed);
        clone.opAttrsExcluded.addAll(this.opAttrsExcluded);
        clone.isOpAllAttrs = this.isOpAllAttrs;
        for (AccessControl aci : this.acis)
            clone.addAci(aci);
        return clone;
    }


    /**
     * Generates a copy of the ACIset but returns only acis that apply to a specific right and that match
     * the client making the operation
     * @param ctx The current RequestCtx if available or null (ctx.getRight must be set)
     * @param identity The current security identity
     * @return A safe copy of the current AciSet.
     */
    public AciSet filterCopy(RequestCtx ctx, SecurityIdentity identity) {
        AciSet clone = new AciSet(this.path,ctx.getRight());
        clone.opAttrsAllowed.addAll(this.opAttrsAllowed);
        clone.opAttrsExcluded.addAll(this.opAttrsExcluded);
        clone.isOpAllAttrs = this.isOpAllAttrs;
        for (AccessControl aci : this.acis) {
            if ((aci.getRights().contains(AccessControl.Rights.all) || aci.getRights().contains(ctx.getRight()))
                && aci.isClientMatch(ctx,identity))
                clone.addAci(aci);
        }
        return clone;
    }

    /**
     * Adds a potential ACI to the set. To be added, the right granted must match that of the current set.
     * @param aci The potential ACI to add.
     */
    public void addAci(AccessControl aci) {
        // If the set is marked as Rights.all then we store all acis and no pre-processing for excluded attrs
        if (this.right.equals(AccessControl.Rights.all)) {
            acis.add(aci);
            return;
        }
        if (aci.getRights().contains(AccessControl.Rights.all) ||
                aci.getRights().contains(this.right)) {
            acis.add(aci);
            if (aci.isAllAttrs()) {
                if (aci.getAttrsExcluded().isEmpty()) {
                    // all attributes are allowed by the current aci
                    isOpAllAttrs = true;
                    opAttrsExcluded.clear();
                    opAttrsAllowed.clear();
                } else {
                   if (!isOpAllAttrs) {
                        isOpAllAttrs = true;
                        for (Attribute attr : aci.getAttrsExcluded()) {
                            if (opAttrsAllowed.contains(attr))
                                continue; // the attribute is explicitly allowed by another aci
                            opAttrsExcluded.add(attr);
                        }
                   } else {
                       for (Attribute attr : aci.getAttrsExcluded()) {
                           if (opAttrsExcluded.contains(attr))
                               continue; // if both exclude, the attribute remains excluded
                           opAttrsExcluded.remove(attr);
                       }
                   }
                }
            } else if (isOpAllAttrs) {
                // we have a specific set of attributes merge them
                for (Attribute attr : aci.getAttrsExcluded()) {
                    if (opAttrsExcluded.contains(attr))
                        continue;
                    opAttrsExcluded.remove(attr);  // this aci allows it
                }
            } else {
                // set has all attrs with some exclusions, aci is specific
                for (Attribute attr : aci.getAttrsAllowed()) {
                    opAttrsExcluded.remove(attr);
                    opAttrsAllowed.add(attr);
                    //override a previously excluded attribute.
                }
            }

        }

        if (aci.getRights().contains(AccessControl.Rights.read) ||
                aci.getRights().contains(AccessControl.Rights.all)) {
            readAcis.add(aci);
            if (aci.isAllAttrs()) {
                if (aci.getAttrsExcluded().isEmpty()) {
                    // all attributes are allowed by the current aci
                    isReadAllAttrs = true;
                    retAttrsExcluded.clear();
                    retAttrsAllowed.clear();
                } else {
                    if (!isReadAllAttrs) {
                        isReadAllAttrs = true;
                        for (Attribute attr : aci.getAttrsExcluded()) {
                            if (retAttrsAllowed.contains(attr))
                                continue; // the attribute is explicitly allowed by another aci
                            retAttrsExcluded.add(attr);
                        }
                    } else {
                        for (Attribute attr : aci.getAttrsExcluded()) {
                            if (retAttrsExcluded.contains(attr))
                                continue; // if both exclude, the attribute remains excluded
                            retAttrsExcluded.remove(attr);
                        }
                    }
                }
            } else {
                if (isReadAllAttrs) {
                    // we have a specific set of attributes merge them
                    for (Attribute attr : aci.getAttrsExcluded()) {
                        if (retAttrsExcluded.contains(attr))
                            continue;
                        retAttrsExcluded.remove(attr);  // this aci allows it
                    }
                } else {
                    // set has all attrs with some exclusions, aci is specific
                    for (Attribute attr : aci.getAttrsAllowed()) {
                        retAttrsExcluded.remove(attr);
                        retAttrsAllowed.add(attr);
                        //override a previously excluded attribute.
                    }
                }
            }
        }
    }

    public List<AccessControl> getAcis() {
        return this.acis;
    }

    /**
     * @param acis The set of ACIs to be added
     * @param ctx RequestCtx is provided, the client match filter will be applied to filter out non-relevant acis
     */
    public void addAll(List<AccessControl> acis, RequestCtx ctx) {
        for (AccessControl aci : acis) {
            if(ctx == null)
                addAci(aci);
            else
                if (aci.isClientMatch(ctx,ctx.getSecSubject()))
                    addAci(aci);
        }
    }

    public boolean hasAcis() { return !acis.isEmpty();}

    public boolean checkCreatePreOp(CreateOp op) {
        if (hasAcis() && right.equals(AccessControl.Rights.add)) {
            ScimResource res = op.getTransactionResource();

            for (Attribute attr : res.getAttributesPresent()) {
                if (isAttributeNotAllowed(attr))
                    res.blockAttribute(attr);
            }

            return isTargetFilterAllowed(res);
        }
        return false;
    }

    /**
     * THis routine used to gather all targetFilters from ACIs speicired in the set.
     * @param acis The acis to check for targetFilter
     * @return A List of Filters from all acis in the input list
     */
    private List<Filter> getTargetFilters(List<AccessControl> acis) {
        List<Filter> tfilters = new ArrayList<>();
        for (AccessControl aci : acis) {
            Filter targetFilter = aci.getTargetFilter();
            if (targetFilter != null)
                tfilters.add(targetFilter);
        }
        return tfilters;
    }

    public boolean checkDeletePreOp(DeleteOp op) {
        if (this.acis.isEmpty() || !right.equals(AccessControl.Rights.delete))
            return false;

        RequestCtx ctx = op.getRequestCtx();

        List<Filter> tfilters = getTargetFilters(acis);
        // if multiple targetFilters are specified, combine to test that the item to be deleted matches at least one
        if (!tfilters.isEmpty())
            // There are target filters that must be applied. Modify the request context with the targetFilter values.
            ctx.combineFilters(tfilters);
        // At this point it can only fail if a targetfilter fails the operaiton.
        return true;
    }

    public boolean checkFilterOp(Operation op) {
        if (hasAcis() && (right.equals(AccessControl.Rights.read)
                || right.equals(AccessControl.Rights.search))) {

            RequestCtx ctx = op.getRequestCtx();
            Filter filter = ctx.getFilter();
            if (filter != null && right.equals(AccessControl.Rights.read))
                return false; // no permission to search
            if (filter != null) {
                Set<Attribute> filterAttrs = Filter.filterAttributes(filter);
                if (!filterAttrs.isEmpty()
                        && !isAttrSetAllowed(filterAttrs))
                    return false;
            }
            List<Filter> tfilters = getTargetFilters(getAcis());
            if (!tfilters.isEmpty())
                // There are target filters that must be applied. Modify the request context with the targetFilter values.
                ctx.combineFilters(tfilters);
            return true;
        }
        return false;
    }

    public boolean checkPutPreOp(PutOp op) {
        if (hasAcis() && right.equals(AccessControl.Rights.modify)) {
            RequestCtx ctx = op.getRequestCtx();

            ScimResource res = op.getTransactionResource();
            for (Attribute attr : res.getAttributesPresent()) {
                if (isAttributeNotAllowed(attr))
                    res.blockAttribute(attr);
            }
            List<Filter> tfilters = getTargetFilters(acis);
            if (!tfilters.isEmpty())
                // There are target filters that must be applied. Modify the request context with the targetFilter values.
                ctx.combineFilters(tfilters);

            // For put, the operation is allowed to proceed once disallowed attributes removed.
            return true;
        }
        return false;
    }

    public String toString() {
        return "AciSet (path=" + path +
                ", size=" + acis.size() +
                ", isAll=" + isOpAllAttrs +
                ", right=" + right.toString() + ")";
    }

}
