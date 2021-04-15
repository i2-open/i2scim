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

package com.independentid.scim.test.password;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.core.err.BadFilterException;
import com.independentid.scim.core.err.ConflictException;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.pwd.PasswordToken;
import com.independentid.scim.pwd.PasswordValue;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.resource.ValueUtil;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaException;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.test.sub.ScimSubComponentTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.jwt.auth.principal.ParseException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class PasswordTest {

    // public final static String hash = "{PBKDF2}yZDH7XEcJ+oD2IWyeQByWg==";
    @Inject
    SchemaManager smgr;

    @Test
    public void a_TestPwdToken() throws NoSuchAlgorithmException, java.text.ParseException, ParseException {
        //PasswordToken.init(parser,"AyM1SysPpbyDfgZld3umj1qzKObwVMko","TESTER",10000,PasswordToken.ALG_PBKDF2);
        PasswordToken tkn = new PasswordToken("password");

        String value = tkn.getRawValue();
        System.out.println("coded: \t"+value);

        byte[] saltin = tkn.getSalt();

        try {
            PasswordToken tknc = new PasswordToken(value);
            byte[] saltout = tknc.getSalt();
            for (int i=0; i < saltout.length; i++)
                if (saltout[i] != saltin[i])
                    fail("Salts do not match");
            assertThat(tknc.validatePassword("password".toCharArray()))
                    .as("check for valid match")
                    .isTrue();
            assertThat(tknc.validatePassword("badpassword".toCharArray()))
                    .as("check for invalid match")
                    .isFalse();
        } catch (ParseException | java.text.ParseException e) {
            e.printStackTrace();
            fail("Parse exception occurr3ed when decrypting");
        }

    }

    @Test
    public void b_CheckVirtuals() {
        Attribute password = smgr.findAttribute("password",null);
        Constructor<?> cons = smgr.getAttributeJsonConstructor(password);

        assertThat(smgr.isVirtualAttr(password)).isTrue();
        assertThat(cons).isNotNull();
    }

    @Test
    public void c_TestPasswordValue() {
        Attribute password = smgr.findAttribute("password",null);
        StringValue sval = new StringValue(password,"password");
        JsonNode node = sval.toJsonNode(null,password.getName()).get("password");
        try {
            Value pval = ValueUtil.parseJson(password,node,null);
            assertThat(pval).isInstanceOf(PasswordValue.class);

            assertThat(pval.equals(sval)).isTrue();

            RequestCtx ctx = new RequestCtx("/Users",null,"password eq password",smgr);
            Filter pwdFilter= ctx.getFilter();
            assertThat(pwdFilter.isMatch(pval)).isTrue();

            ctx = new RequestCtx("/Users",null,"password eq dummy",smgr);
            pwdFilter= ctx.getFilter();
            assertThat(pwdFilter.isMatch(pval)).isFalse();

        } catch (ConflictException | SchemaException | java.text.ParseException | BadFilterException e) {
            fail("Failed to parse password: "+e.getMessage());
        } catch (ScimException e) {
           fail("unable to parse filter");
        }
    }


}
