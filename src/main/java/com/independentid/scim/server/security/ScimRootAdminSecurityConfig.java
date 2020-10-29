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

import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.scim.server.ConfigMgr;

/**
 * @author pjdhunt
 * If <ConfigMgr>.isRootEnabled, this security config enables root access via BasicAuth
 */
//@EnableWebSecurity(debug = true)
@EnableWebSecurity (debug = true)
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
				.csrf().disable()
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			.and()
			.authorizeRequests()
				.antMatchers("/ServiceProviderConfig").permitAll()
				
				.antMatchers("/certs/*").permitAll()
				
				.antMatchers("/**")
				  	.authenticated()
	        .and()
	          	.httpBasic()
	        .and()
	        	.oauth2ResourceServer().jwt();
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
	
	AuthenticationManagerResolver<HttpServletRequest> resolver() {
	    return request -> {
	    	String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
	    	if (auth != null && auth.startsWith("Basic"))
	    		return basicAuthenticationManager();
	    	return tokenAuthenticationManager();

	    };
	}
	
	
	public class CustomAuthenticationFailureHandler 
	  implements AuthenticationFailureHandler {
	 
	    private ObjectMapper objectMapper = new ObjectMapper();
	 
	    @Override
	    public void onAuthenticationFailure(
	      HttpServletRequest request,
	      HttpServletResponse response,
	      AuthenticationException exception) 
	      throws IOException, ServletException {
	 
	        response.setStatus(HttpStatus.UNAUTHORIZED.value());
	        Map<String, Object> data = new HashMap<>();
	        data.put(
	          "timestamp", 
	          Calendar.getInstance().getTime());
	        data.put(
	          "exception", 
	          exception.getMessage());
	 
	        response.getOutputStream()
	          .println(objectMapper.writeValueAsString(data));
	    }
	}
	
	private AuthenticationFilter authenticationFilter() {
	    AuthenticationFilter filter = new AuthenticationFilter(
	      resolver(), authenticationConverter());
	    filter.setSuccessHandler((request, response, auth) -> {});
	    return filter;
	}
	
	public AuthenticationConverter authenticationConverter() {
	        return new BasicAuthenticationConverter();
	}


	
	public AuthenticationManager basicAuthenticationManager() {
        return authentication -> {
            
                return new UsernamePasswordAuthenticationToken(
                  authentication.getPrincipal(),
                  authentication.getCredentials(),
                  Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            
        };
    }
	
	public AuthenticationManager tokenAuthenticationManager() {
        return authentication -> {
           
                return new UsernamePasswordAuthenticationToken(
                  authentication.getPrincipal(),
                  authentication.getCredentials(),
                  Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                );
            
        };
    }

}
