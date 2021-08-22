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
 package com.independentid.scim.test.sub;

 import com.fasterxml.jackson.databind.JsonNode;
 import com.independentid.scim.core.ConfigMgr;
 import com.independentid.scim.core.err.BadFilterException;
 import com.independentid.scim.core.err.ScimException;
 import com.independentid.scim.protocol.Filter;
 import com.independentid.scim.protocol.RequestCtx;
 import com.independentid.scim.resource.ScimResource;
 import com.independentid.scim.resource.Value;
 import com.independentid.scim.schema.Attribute;
 import com.independentid.scim.schema.SchemaManager;
 import com.independentid.scim.serializer.JsonUtil;
 import io.quarkus.test.junit.QuarkusTest;
 import io.quarkus.test.junit.TestProfile;
 import org.junit.jupiter.api.Assertions;
 import org.junit.jupiter.api.MethodOrderer;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.TestMethodOrder;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;

 import javax.annotation.Resource;
 import javax.inject.Inject;
 import java.io.IOException;
 import java.io.InputStream;
 import java.text.ParseException;

 import static org.assertj.core.api.Assertions.assertThat;

 @QuarkusTest
 @TestProfile(ScimSubComponentTestProfile.class)
 @TestMethodOrder(MethodOrderer.MethodName.class)
 public class ScimValuePathMatchTest {

     private final Logger logger = LoggerFactory.getLogger(ScimValuePathMatchTest.class);

     //private static String userSchemaId = "urn:ietf:params:scim:schemas:core:2.0:User";

     @Inject
     @Resource(name="SchemaMgr")
     SchemaManager smgr;

     final static String testUserFile1 = "/schema/TestUser-bjensen.json";

     static ScimResource user1 = null;

     /**
      * This test checks that a JSON user can be parsed into a SCIM Resource
      */
     @Test
     public void a_ScimResParseUser1Test() {

         logger.info("========== SCIM ValuePath Match Test ==========");

         try {
             InputStream userStream = ConfigMgr.findClassLoaderResource(testUserFile1);
             //InputStream userStream = this.resourceloader.getResource(testUserFile1).getInputStream();
             JsonNode node = JsonUtil.getJsonTree(userStream);
             user1 = new ScimResource(smgr, node, "Users");
             //logger.debug("User loaded: \n" + user1.toString());

             assert userStream != null;
             userStream.close();

         } catch (IOException | ParseException | ScimException e) {
             Assertions.fail("Exception occured while parsing test user bjensen. " + e.getMessage(), e);
         }


     }

     @Test
     public void b_ValPathFilterTest() throws ScimException {
         Attribute addr = smgr.findAttribute("addresses", null);
         assertThat(addr).isNotNull();
         Value val = user1.getValue(addr);

         // Create a dummy RequestCtx to pass schemaMgr.
         RequestCtx ctx = new RequestCtx("/",smgr);

         Filter filter = Filter.parseFilter("addresses[type eq work and postalCode eq 91608]", null, ctx);

         assertThat(filter.isMatch(user1))
                 .as("Check the value path filter is true for Babs Jensen")
                 .isTrue();

         assertThat(filter.isMatch(val))
                 .as("Check the value match for addresses attribute")
                 .isTrue();

         filter = Filter.parseFilter("addresses[type eq work and postalCode eq 11111]", null, ctx);

         assertThat(filter.isMatch(user1))
                 .as("Check the value path filter is not true for Babs Jensen")
                 .isFalse();

         assertThat(filter.isMatch(val))
                 .as("Check the value match for addresses attribute is not true")
                 .isFalse();
     }

     @Test
     public void c_FaultyValPathFilterTest() {
         // Create a dummy RequestCtx to pass schemaMgr.
         RequestCtx ctx = new RequestCtx("/",smgr);
         boolean isBadDetected = false;
         try {
             Filter filter = Filter.parseFilter("addresses[type eq work and postalCode eq 91608",null,ctx);
         } catch (BadFilterException e) {
             isBadDetected = true;
         }
         assertThat(isBadDetected)
                 .as("Did parser detect faulting value path filter?")
                 .isTrue();

         isBadDetected = false;
         try {
             Filter filter = Filter.parseFilter("addresses type eq work and postalCode eq 91608]",null,ctx);
         } catch (BadFilterException e) {
             isBadDetected = true;
         }
         assertThat(isBadDetected)
                 .as("Did parser detect faulting value path filter?")
                 .isTrue();

     }

 }
