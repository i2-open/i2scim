/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2020 Phillip Hunt, All Rights Reserved                        *
 *                                                                    *
 *  Confidential and Proprietary                                      *
 *                                                                    *
 *  This unpublished source code may not be distributed outside       *
 *  “Independent Identity Org”. without express written permission of *
 *  Phillip Hunt.                                                     *
 *                                                                    *
 *  People at companies that have signed necessary non-disclosure     *
 *  agreements may only distribute to others in the company that are  *
 *  bound by the same confidentiality agreement and distribution is   *
 *  subject to the terms of such agreement.                           *
 **********************************************************************/
package com.independentid.scim.server.security;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

import com.independentid.scim.server.ConfigMgr;

/**
 * @author pjdhunt
 * Spring web security config for enabling OAuth2 JWT tokens
 */
//@EnableWebSecurity(debug = true)
@EnableWebSecurity
@Configuration
@Order(2)
public class ScimJwtSecurityConfig extends WebSecurityConfigurerAdapter {
	private final static Logger logger = LoggerFactory
			.getLogger(ScimJwtSecurityConfig.class);
	
	@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
	String jwkSetUri;
	
	@Resource(name="ConfigMgr")
	private ConfigMgr cfg;
    
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		
		if (cfg.isSecure()) {
			
			logger.info("Configuring JWT Access");
			//logger.info("Configuring JWT Token Access");
			
			http
				.sessionManagement()
					.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				.and()
				.authorizeRequests().anyRequest().authenticated()
				.and()
				.oauth2ResourceServer().jwt();		
			
		} 
	}

}
