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

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.op.IBulkIdResolver;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import io.smallrye.jwt.auth.principal.ParseException;

import java.security.NoSuchAlgorithmException;

public class PasswordValue extends StringValue {

    private PasswordToken tkn;

    public PasswordValue (Value value) {
        this(value.getAttribute(), value.toString());
    }

    public PasswordValue(Attribute attr, JsonNode node, IBulkIdResolver bulkIdResolver) throws SchemaException {
        super(attr, node, bulkIdResolver);
    }

    public PasswordValue(Attribute attr, JsonNode node) throws SchemaException {
        super(attr, node);
    }

    public PasswordValue(Attribute attr, String value)  {
        super(attr, value);
        // Hash the clear text value.
        try {
            if (value.startsWith(PasswordToken.PREFIX_TOKEN)) {
                this.tkn = null; //we want lazy parsing to avoid extra crypto work
                this.value = value.toCharArray();
            }else {
                this.tkn = new PasswordToken(value);
                this.value = this.tkn.toString().toCharArray();
            }

        } catch (NoSuchAlgorithmException | java.text.ParseException | ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void parseJson(JsonNode node) throws SchemaException {
        String svalue = node.asText();
        try {
            if (svalue.startsWith(PasswordToken.PREFIX_TOKEN)) {
                this.tkn = null; //we want lazy parsing to avoid extra crypto work
                this.value = svalue.toCharArray();
            }else {
                this.tkn = new PasswordToken(svalue);
                this.value = this.tkn.getRawValue().toCharArray();
            }

        } catch (NoSuchAlgorithmException | java.text.ParseException | ParseException e) {
            e.printStackTrace();
        }
    }

    public PasswordToken getToken() {
        if (this.tkn == null)
            try {
                this.tkn = new PasswordToken(this.getRawValue());
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
}
