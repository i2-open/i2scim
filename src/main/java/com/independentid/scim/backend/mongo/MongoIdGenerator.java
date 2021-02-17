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

package com.independentid.scim.backend.mongo;

import com.independentid.scim.backend.IIdentifierGenerator;
import org.bson.types.ObjectId;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Priority(10)
public class MongoIdGenerator implements IIdentifierGenerator {

    /**
     * Generates and obtains an identifier usable for objects within the persistence provider. This is most typically
     * used for generating transactionIds which will be ultimately stored in the providers "Trans" container.
     * @return A String value that can be used as an object identifier.
     */
    @Override
    public String getNewIdentifier() {
        return (new ObjectId()).toString();
    }

    @Override
    public String getProviderClass() {
        return MongoProvider.class.getName();
    }
}