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

package com.independentid.scim.test.auth;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.backend.BackendException;
import com.independentid.scim.backend.BackendHandler;
import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.GetOp;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.protocol.ResourceResponse;
import com.independentid.scim.protocol.ScimResponse;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.Attribute;
import com.independentid.scim.schema.SchemaManager;
import com.independentid.scim.security.AccessControl;
import com.independentid.scim.security.AccessManager;
import com.independentid.scim.security.AciSet;
import com.independentid.scim.security.ScimBasicIdentityProvider;
import com.independentid.scim.serializer.JsonUtil;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.http.HttpStatus;
import org.apache.http.auth.BasicUserPrincipal;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimAuthTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class AccessMgrTest {
    private final Logger logger = LoggerFactory.getLogger(AccessMgrTest.class);

    @Inject
    ConfigMgr cmgr;

    @Inject
    SchemaManager smgr;

    @Inject
    @Resource(name="AccessMgr")
    AccessManager amgr;

    @Inject
    ScimBasicIdentityProvider idp;

    @Inject
    BackendHandler handler;

    @ConfigProperty(name="scim.mongodb.uri",defaultValue = "mongodb://localhost:27017")
    String dbUrl;

    @ConfigProperty(name="scim.mongodb.dbname",defaultValue = "testSCIM")
    String scimDbName;

    private MongoClient mclient = null;

    private static final String testUserFile1 = "/schema/TestUser-bjensen.json";
    private static final String testUserFile2 = "/schema/TestUser-jsmith.json";
    private static final String testPass = "t1meMa$heen";

    private static ScimResource user1,user2 = null;
    private static SecurityIdentity idUser1,idUser2 = null;

    @Test
    public void a_AmgrInitTest() throws ScimException {
        logger.info("========== Beginning Access Manager Tests ==========");
        logger.info("\tA.\tBasic init test");
        assertThat(amgr)
                .as("Access Manger is available")
                .isNotNull();
        SecurityIdentity root = authUser(cmgr.getRootUser(),cmgr.getRootPassword(),null);
        RequestCtx ctx = new RequestCtx("/",null,null,smgr);
        ctx.setRight(AccessControl.Rights.search);
        ctx.setSecSubject(root);
        AciSet set = amgr.getAcisByPath(ctx);
        assertThat(set.getAcis().size())
                .as("Some ACI returned for root")
                .isGreaterThan(0);

        // Compare getPath vs. container
        ctx = new RequestCtx("/ServiceProviderConfig",null,null,smgr);
        ctx.setRight(AccessControl.Rights.read);
        ctx.setSecSubject(root);
        set = amgr.getAcisByPath(ctx);

        AciSet setnode = amgr.getAcisForResourceContainer("/ServiceProviderConfig");
        assertThat(set.getAcis().size())
                .as("Check aci by path has more acis than by node")
                .isGreaterThan(setnode.getAcis().size());
        assertThat(setnode.getAcis().size())
                .as("At least one ACI for ServiceProviderConfig")
                .isGreaterThan(0);
        AccessControl aci = setnode.getAcis().get(0);
        logger.info("ServiceProviderConfig aci\n"+aci.toString());
        assertThat(aci.isAnyClient())
                .as("ServiceProviderConfig aci applies to any actor")
                .isTrue();

        loadData();
    }

    private void loadData() {
        // Initialize the database

        logger.info("\t\t Initializing test database: "+scimDbName);

        if (mclient == null)
            mclient = MongoClients.create(dbUrl);


        MongoDatabase scimDb = mclient.getDatabase(scimDbName);

        scimDb.drop();

        try {
            handler.getProvider().syncConfig(smgr.getSchemas(), smgr.getResourceTypes());

            InputStream userStream1 = ConfigMgr.getClassLoaderFile(testUserFile1);
            JsonNode userNode1 = JsonUtil.getJsonTree(userStream1);
            user1 = new ScimResource(smgr,userNode1,"Users");
            user1.setId(null);

            InputStream userStream2 = ConfigMgr.getClassLoaderFile(testUserFile2);
            JsonNode userNode2 = JsonUtil.getJsonTree(userStream2);
            user2 = new ScimResource(smgr,userNode2,"Users");
            user2.setId(null);
            RequestCtx ctx = new RequestCtx("/Users",null,null,smgr);
            ScimResponse resp = handler.create(ctx,user1);
            assertThat(resp.getStatus())
                    .as("User 1 added")
                    .isEqualTo(HttpStatus.SC_CREATED);
            idUser1 = authUser("bjensen@example.com",testPass,user1);

            resp = handler.create(ctx,user2);
            assertThat(resp.getStatus())
                    .as("User 2 added")
                    .isEqualTo(HttpStatus.SC_CREATED);
            idUser2 = authUser("jsmith@example.com",testPass,user2);

        } catch (IOException | ParseException | ScimException | BackendException e) {
            fail("Failed to initialize test Mongo DB: "+scimDbName+": "+e.getLocalizedMessage());
        }



    }

    private SecurityIdentity authUser(String user, String pwd,ScimResource res) {
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder();
        builder
                .setPrincipal(new BasicUserPrincipal(user));
        if (res != null) {
            builder
                    .addAttribute("uri", res.getMeta().getLocation())
                    .addAttribute(ScimBasicIdentityProvider.ATTR_SELF_ID, res.getId())
                    .addAttribute(ScimBasicIdentityProvider.ATTR_ACTOR_RES, res)
                    .addRole("user"); // add the default role;
            Attribute role = res.getAttribute("roles", null);
            Value val = res.getValue(role);
            if (val instanceof MultiValue) {
                Collection<Value> vals = ((MultiValue) val).values();
                for (Value aval : vals) {
                    StringValue sval = (StringValue) aval;
                    builder.addRole(sval.toString());
                }

            } else {
                if (val instanceof StringValue) {
                    StringValue sval = (StringValue) val;
                    builder.addRole(sval.toString());
                }
            }

        }
        if (cmgr.getRootUser().equals(user))
            builder.addRole("root");
        if (pwd != null)
            builder
                .addCredential(new PasswordCredential(pwd.toCharArray()));

        return builder.build();
    }

    @Test
    public void b_TestReadAsRoot() {

        try {
            RequestCtx ctx = new RequestCtx("/Users",null,null,smgr);
            ctx.setRight(AccessControl.Rights.search); //usually set by the AccessFilter
            //ctx.setRight(AccessControl.Rights.read);  // this is normally set by the Operation when constructed

            SecurityIdentity root = authUser(cmgr.getRootUser(),cmgr.getRootPassword(),null);
            assertThat(amgr.filterRequestandInitAcis(ctx,root))
                    .as("Is root authorized to search?")
                    .isTrue();
            assertThat(ctx.getRight())
                    .as("Operation parsed as search")
                    .isEqualTo(AccessControl.Rights.search);
            AciSet set = ctx.getAcis();


            List<AccessControl> acis = set.getAcis();
            assertThat(acis.size())
                    .as("Check there should be 2 acis appilcable")
                    .isEqualTo(2);
            assertThat(set.isOpAllAttrs)
                    .as("All attributes are returnable")
                    .isTrue();

            Attribute username = smgr.findAttribute("username",ctx);
            assertThat(set.isAttributeNotAllowed(username))
                    .as("Check username returnable")
                    .isFalse();
            Attribute ims = smgr.findAttribute("ims",ctx);
            assertThat(set.isAttributeNotAllowed(ims))
                    .as("Check ims returnable")
                    .isFalse();

        } catch (ScimException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void c_TestReadAsBJensen_self() {

        try {
            RequestCtx ctx = new RequestCtx("/Users/"+user1.getId(),null,null,smgr);
            ctx.setRight(AccessControl.Rights.search); //usually set by the AccessFilter

            assertThat(amgr.filterRequestandInitAcis(ctx,idUser1))
                    .as("Is bjensen authorized to search?")
                    .isTrue();
            assertThat(ctx.getRight())
                    .as("Operation parsed as search")
                    .isEqualTo(AccessControl.Rights.search);
            AciSet set = ctx.getAcis();

            Attribute username = smgr.findAttribute("username",ctx);
            assertThat(set.isAttributeNotAllowed(username))
                    .as("Check username returnable")
                    .isFalse();
            Attribute ims = smgr.findAttribute("ims",ctx);
            assertThat(set.isAttributeNotAllowed(ims))
                    .as("Check ims returnable")
                    .isTrue();


            //Check ability to read self resource
            List<AccessControl> acis = set.getAcis();
            assertThat(acis.size())
                    .as("Check there should be 2 acis appilcable")
                    .isEqualTo(2);
            assertThat(set.isOpAllAttrs)
                    .as("All attributes are returnable")
                    .isTrue();
            ScimResource u1copy = user1.copy(ctx);
            ResourceResponse resp = new ResourceResponse(u1copy,ctx);
            resp.applyAciSet(ctx.getAcis());

            StringWriter writer = new StringWriter();
            JsonGenerator gen = JsonUtil.getGenerator(writer, false);
            resp.serialize(gen, ctx, false);
            gen.close();
            writer.close();
            String res = writer.toString();

            //u1copy should now have attributes blocked.
            logger.debug("User 1 filtered:\n"+ res);

            assertThat(res)
                    .as("Contains username field")
                    .contains("\"userName\"");
            assertThat(res)
                    .as("Does not contain userType")
                    .doesNotContain("\"userType\"");

        } catch (ScimException | ParseException | IOException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void d_TestReadAsBJensen_Other() {

        try {
            RequestCtx ctx = new RequestCtx("/Users/"+user2.getId(),null,null,smgr);
            ctx.setRight(AccessControl.Rights.search); //usually set by the AccessFilter


            assertThat(amgr.filterRequestandInitAcis(ctx,idUser1))
                    .as("Is bjensen authorized to search?")
                    .isTrue();
            assertThat(ctx.getRight())
                    .as("Operation parsed as search")
                    .isEqualTo(AccessControl.Rights.search);
            AciSet set = ctx.getAcis();

            Attribute username = smgr.findAttribute("username",ctx);
            assertThat(set.isAttributeNotAllowed(username))
                    .as("Check username returnable")
                    .isFalse();
            Attribute ims = smgr.findAttribute("ims",ctx);
            assertThat(set.isAttributeNotAllowed(ims))
                    .as("Check ims not returnable")
                    .isTrue();

            //Check ability to read self resource
            List<AccessControl> acis = set.getAcis();
            assertThat(acis.size())
                    .as("Check there should be 1 acis appilcable")
                    .isEqualTo(1);
            assertThat(set.isOpAllAttrs)
                    .as("All attributes are not returnable")
                    .isFalse();
            ScimResource u2copy = user2.copy(ctx);
            ResourceResponse resp = new ResourceResponse(u2copy,ctx);
            resp.applyAciSet(ctx.getAcis());

            StringWriter writer = new StringWriter();
            JsonGenerator gen = JsonUtil.getGenerator(writer, false);
            resp.serialize(gen, ctx, false);
            gen.close();
            writer.close();
            String res = writer.toString();

            //u1copy should now have attributes blocked.
            logger.debug("User 2 filtered:\n"+ res);

            assertThat(res)
                    .as("Contains username field")
                    .contains("\"userName\"");
            assertThat(res)
                    .as("Does not contain userType")
                    .doesNotContain("\"userType\"");
            assertThat(res)
                    .as("Does not contain externalId")
                    .doesNotContain("\"externalId\"");
            assertThat(res)
                    .as("Does contain phoneNumbers")
                    .contains("\"phoneNumbers\"");

        } catch (ScimException | ParseException | IOException e) {
            e.printStackTrace();
        }

    }

    @Test
    public void e_TestSearchAsBJensen_Other() {

        try {
            RequestCtx ctx = new RequestCtx("/Users",user2.getId(),"userName eq jsmith@example.com",smgr);
            ctx.setRight(AccessControl.Rights.search); //usually set by the AccessFilter

            GetOp op = new GetOp(ctx,0);
            assertThat(amgr.filterRequestandInitAcis(ctx,idUser1))
                    .as("Is bjensen authorized to search?")
                    .isTrue();
            assertThat(ctx.getRight())
                    .as("Operation parsed as search")
                    .isEqualTo(AccessControl.Rights.search);
            AciSet set = ctx.getAcis();
            AccessManager.checkRetrieveOp(op);
            assertThat(op.isError())
                    .as("Check status authorized")
                    .isFalse();

            Attribute username = smgr.findAttribute("username",ctx);
            assertThat(set.isAttributeNotAllowed(username))
                    .as("Check username searchable")
                    .isFalse();
            assertThat(set.isAttrNotReturnable(username))
                    .as("Check username returnable")
                    .isFalse();
            Attribute name = smgr.findAttribute("name",ctx);
            assertThat(set.isAttributeNotAllowed(name))
                    .as("Check name not searchable")
                    .isTrue();
            assertThat(set.isAttrNotReturnable(name))
                    .as("Check name returnable")
                    .isFalse();

            ctx = new RequestCtx("/Users",user2.getId(),"name.familyname eq Smith",smgr);
            ctx.setRight(AccessControl.Rights.search); //usually set by the AccessFilter
            //ctx.setRight(AccessControl.Rights.read);  // this is normally set by the Operation when constructed
            op = new GetOp(ctx,0);
            assertThat(amgr.filterRequestandInitAcis(ctx,idUser1))
                    .as("Is bjensen authorized to search?")
                    .isTrue();
            assertThat(ctx.getRight())
                    .as("Operation parsed as search")
                    .isEqualTo(AccessControl.Rights.search);

            AccessManager.checkRetrieveOp(op);
            assertThat(op.isError())
                    .as("Check status unauthorized")
                    .isTrue();

            ctx = new RequestCtx("/Users",null,"name.familyname eq Smith",smgr);
            ctx.setRight(AccessControl.Rights.search); //usually set by the AccessFilter
            //ctx.setRight(AccessControl.Rights.read);  // this is normally set by the Operation when constructed
            op = new GetOp(ctx,0);
            assertThat(amgr.filterRequestandInitAcis(ctx,idUser1))
                    .as("Is bjensen authorized to search?")
                    .isTrue();
            assertThat(ctx.getRight())
                    .as("Operation parsed as search")
                    .isEqualTo(AccessControl.Rights.search);

            AccessManager.checkRetrieveOp(op);
            assertThat(op.isError())
                    .as("Check status unauthorized")
                    .isTrue();

            ctx = new RequestCtx("/Users",user1.getId(),"name.familyname eq Jensen",smgr);
            ctx.setRight(AccessControl.Rights.search); //usually set by the AccessFilter
            //ctx.setRight(AccessControl.Rights.read);  // this is normally set by the Operation when constructed
            op = new GetOp(ctx,0);
            assertThat(amgr.filterRequestandInitAcis(ctx,idUser1))
                    .as("Is bjensen authorized to search?")
                    .isTrue();
            assertThat(ctx.getRight())
                    .as("Operation parsed as search")
                    .isEqualTo(AccessControl.Rights.search);

            AccessManager.checkRetrieveOp(op);
            assertThat(op.isError())
                    .as("Check status authorized")
                    .isFalse();

        } catch (ScimException e) {
            e.printStackTrace();
        }

    }
}
