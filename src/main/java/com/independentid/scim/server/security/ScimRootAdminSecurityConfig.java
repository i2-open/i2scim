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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

import com.independentid.scim.server.ConfigMgr;

/**
 * @author pjdhunt
 * If <ConfigMgr>.isRootEnabled, this security config enables root access via BasicAuth
 */
//@EnableWebSecurity(debug = true)
@EnableWebSecurity
@Configuration
@Order(1)
public class ScimRootAdminSecurityConfig extends WebSecurityConfigurerAdapter {
	public static final String ADMIN = "admin";

	private final static Logger logger = LoggerFactory
			.getLogger(ScimRootAdminSecurityConfig.class);
	
	@Resource(name="ConfigMgr")
	private ConfigMgr cfg;
	
	@Autowired
    RootAuthenticationProvider rootProvider;

	@Autowired
	public void configureGlobal(AuthenticationManagerBuilder auth) {
		
		if(cfg.isSecure() && cfg.isRootEnabled()) {
			logger.info("Configuring Root Basic Authenticator");
			auth.authenticationProvider(rootProvider);
		}
	    
	}	
	
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
		
       
		if (cfg.isSecure()) {
			if (cfg.isRootEnabled()) {
	
				logger.info("Configuring Root Admin HTTP Basic Access");
				http
		          .authorizeRequests()
		          	.anyRequest().authenticated()
		          .and()
		          .httpBasic();
			} 
		} else if (!cfg.isSecure()) {
			logger.info("Secure mode disabled. Disabling access control policy.");
			// Usually used for JUnit testing only.
			http
				.cors().disable().csrf().disable()
				.authorizeRequests()
					.anyRequest()
					.anonymous();
		}
	
	}
	

}
