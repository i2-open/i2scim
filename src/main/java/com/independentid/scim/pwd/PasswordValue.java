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

package com.independentid.scim.pwd;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.IVirtualValue;
import io.smallrye.jwt.auth.principal.ParseException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class PasswordValue extends StringValue implements IVirtualValue {

    private PasswordToken tkn;
    private final ScimResource res;

    public PasswordValue (ScimResource parent, Value value) {
        this(parent, value.getAttribute(), value.toString());
    }

    public PasswordValue(ScimResource parent, Attribute attr, JsonNode node) {
        this(parent,attr,node.asText());
    }

    public PasswordValue(ScimResource parent, Attribute attr, String value)  {
        super(attr, value);
        this.res = parent;
        // Hash the clear text value.
        try {
            if (value.startsWith(PasswordToken.PREFIX_TOKEN)) {
                this.tkn = null; //we want lazy parsing to avoid extra crypto work
                this.value = value.toCharArray();
            }else {
                this.tkn = new PasswordToken(parent,value);
                this.value = this.tkn.getRawValue().toCharArray();
            }

        } catch (NoSuchAlgorithmException | java.text.ParseException | ParseException e) {
            e.printStackTrace();
        }
    }

    public PasswordToken getToken() {
        if (this.tkn == null)
            try {
                this.tkn = new PasswordToken(res,this.getRawValue());
            } catch (NoSuchAlgorithmException | ParseException | java.text.ParseException e) {
                e.printStackTrace();
            }
        return this.tkn;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PasswordValue) {
            byte[] hash = getToken().getMatchHash();
            PasswordValue pval = (PasswordValue) obj;
            byte[] matchHash = pval.getToken().getMatchHash();
            if (matchHash.length != hash.length)
                return false;
            for(int i=0; i < hash.length; i++)
                if (hash[i] != matchHash[i])
                    return false;

            return true;
        }
        if (obj instanceof StringValue) {
            StringValue sval = (StringValue) obj;
            try {
                return getToken().validatePassword(sval.getCharArray());
            } catch (NoSuchAlgorithmException e) {
                //should not happen here
            }
        }

        return false;
    }

    @Override
    public void serialize(JsonGenerator gen, RequestCtx ctx) throws ScimException, IOException {
        super.serialize(gen, ctx);
    }

    @Override
    public boolean isVirtual() {
        return true;
    }
}
