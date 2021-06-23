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

import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.protocol.AttributeFilter;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.LogicFilter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.schema.SchemaManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@QuarkusTest
@TestProfile(ScimSubComponentTestProfile.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class FilterParserTest {

	private final Logger logger = LoggerFactory.getLogger(FilterParserTest.class);

	@Inject
	@Resource(name="SchemaMgr")
	SchemaManager smgr;
	
	/**
	 * Test filters from RFC7644, figure 2
	 */
	final String[][] testArray = new String[][] {
		{"Users","userName eq \"bjensen\""},  // basic attribute filter
		{"Users","displayName eq \"Phil Hunt\""},  //testing for parsing spaces
		{null,"urn:ietf:params:scim:schemas:core:2.0:User:name.familyName co \"O'Malley\""},
		{"Users","name.familyName co \"O'Malley\""},
		{"Users","userName sw \"J\""},
		{null,"urn:ietf:params:scim:schemas:core:2.0:User:userName sw \"J\""},
		{"Users","title pr"},
		{null,"undefinedattr eq bleh"},
		{"Users","title pr and userType eq \"Employee\""},
		{"Users","title pr or userType eq \"Intern\""},
		{"Users","userType eq \"Employee\" and (emails co \"example.com\" or emails.value co \"example.org\")"},
		{"Users","userType eq \"Employee\" and (emails co example.com Or emails.value co example.org)"},
		{"Users","userType ne \"Employee\" and not ( emails co \"example.com(\" Or emails.value cO \"ex)ample.org\")"},
		{"Users","userType ne \"Employee\" and not ( emails co example.com( Or emails.value cO ex)ample.org)"},
		{"Users","userType ne \"Employee\" and NOT(emails co \"example.com\" or emails.value co \"example.org\")"},
		{"Users","userType eq \"Employee\" and (emails.type eq \"work\")"},
		{"Users","userType eq \"Employee\" and emails[type eq \"work\" and value co \"@example.com\"]"},
		{"Users","emails[type eq \"work\" and value co \"@example.com\"] or ims[type eq \"xmpp\" and value co \"@foo.com\"]"},
		{null,"meta.lastModified gt \"2011-05-13T04:42:34Z\""},
		{null,"meta.lastModified ge \"2011-05-13T04:42:34Z\""},
		{null,"meta.lastModified lt \"2011-05-13T04:42:34Z\""},
		{null,"meta.lastModified le 2011-05-13T04:42:34Z"},
		{null,"schemas eq \"urn:ietf:params:scim:schemas:extension:enterprise:2.0:User\""},
		{null,"schemas eq urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"}

	};
	
	final String[] matchArray = new String[] {
			"userName eq bjensen",  // basic attribute filter
			"displayName eq \"Phil Hunt\"",  //testing for parsing spaces
			"name.familyName co O'Malley",
			"name.familyName co O'Malley",
			"userName sw J",
			"userName sw J",
			"title pr",
			"undefinedattr eq bleh",

			"title pr and userType eq Employee",
			"title pr or userType eq Intern",
			"userType eq Employee and (emails.value co example.com or emails.value co example.org)",
			"userType eq Employee and (emails.value co example.com or emails.value co example.org)",
			"userType ne Employee and not(emails.value co example.com( or emails.value co ex)ample.org)",
			"userType ne Employee and not(emails.value co example.com( or emails.value co ex)ample.org)",
			"userType ne Employee and not(emails.value co example.com or emails.value co example.org)",
			"userType eq Employee and emails.type eq work",
			"userType eq Employee and emails[type eq work and value co @example.com]",
			"emails[type eq work and value co @example.com] or ims[type eq xmpp and value co @foo.com]",
			"meta.lastModified gt 2011-05-13T04:42:34Z",
			"meta.lastModified ge 2011-05-13T04:42:34Z",
			"meta.lastModified lt 2011-05-13T04:42:34Z",
			"meta.lastModified le 2011-05-13T04:42:34Z",
			"schemas eq urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
			"schemas eq urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"

	};
	
	
	@Test
	public void a_cfgTest() {
		assertThat(smgr).as("Check injection worked")
			.isNotNull();

	}
	
	@Test
	public void b_testParseFilterString() {
		for(int i = 0; i < testArray.length; i++) {
			logger.debug("Parsing filter: ("+i+"):\t"+testArray[i][1]);
			RequestCtx ctx = null;
			try {
				ctx = new RequestCtx(testArray[i][0],null,testArray[i][1], smgr);
			} catch (ScimException e) {
				fail("Failed while generating RequestCtx and Filter: "+e.getMessage(),e);
			}
			assert ctx != null;
			Filter filter = ctx.getFilter();
			String sfilter = filter.toString();
			logger.debug("Parsed filter:      \t"+sfilter);
			
			/*
			assertThat(testArray[i])
				.as("Parsed matches input test")
				.isEqualToIgnoringCase(sfilter);
			*/
			if (!sfilter.equalsIgnoreCase(matchArray[i]))
				logger.error("**Unequal strings for test "+i+"!**");
			
			
			if (i < 7 || i > 17) {
				assertThat(filter)
					.as("Check filter class is AttributeFilter")
					.isInstanceOf(AttributeFilter.class);
			}
			if (i == 6) {
				AttributeFilter afilt = (AttributeFilter) filter;
				assertThat(afilt.getOperator())
					.as("Check for presence filter")
					.isEqualTo(AttributeFilter.FILTEROP_PRESENCE);
				assertThat(afilt.getValue())
					.as("Check null for presence filter")
					.isNull();
			}
			
			if (i > 7 && i < 10) {
				assertThat(filter)
					.as("Check filter class is LogicFilter")
					.isInstanceOf(LogicFilter.class);
				if (i == 8) {
					LogicFilter lfilt = (LogicFilter) filter;
					assertThat(lfilt.isAnd())
						.as("Check parsed as AND filter (isAnd is true)")
						.isTrue();
				}
				if (i == 9) {
					LogicFilter lfilt = (LogicFilter) filter;
					assertThat(lfilt.isAnd())
						.as("Check parsed as OR filter (isAnd is false)")
						.isFalse();
				}
			}
			
		}
		
	}


}
