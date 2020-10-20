package com.independentid.scim.test.sub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import javax.annotation.Resource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.independentid.scim.protocol.AttributeFilter;
import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.LogicFilter;
import com.independentid.scim.protocol.RequestCtx;
import com.independentid.scim.server.ConfigMgr;
import com.independentid.scim.server.ScimException;



/*
 * 
 * 
 */
@ActiveProfiles("testing")
@RunWith(SpringRunner.class)
@SpringBootTest(
		classes = {ConfigMgr.class})
@ContextConfiguration(classes = ConfigMgr.class)
public class FilterTest {

	private Logger logger = LoggerFactory.getLogger(FilterTest.class);

	@Resource(name="ConfigMgr")
	private ConfigMgr cfgMgr;
	
	/**
	 * Test filters from RFC7644, figure 2
	 */
	String[][] testArray = new String[][] {
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
	
	String[] matchArray = new String[] {
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
			"schemas eq urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"

	};
	
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void testParseFilterString() {
		for(int i = 0; i < testArray.length; i++) {
			logger.debug("Parsing filter: ("+i+"):\t"+testArray[i][1]);
			RequestCtx ctx = null;
			try {
				ctx = new RequestCtx(testArray[i][0],null,testArray[i][1]);
			} catch (ScimException e) {
				fail("Failed while generating RequestCtx and Filter: "+e.getMessage(),e);
			}
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
