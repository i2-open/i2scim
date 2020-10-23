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

import java.util.Collection;
import java.util.HashSet;

import javax.annotation.Resource;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.independentid.scim.server.ConfigMgr;

@Component
public class RootAuthenticationProvider implements AuthenticationProvider {

	@Resource(name="ConfigMgr")
	private ConfigMgr cfg;
	
	static GrantedAuthority role = new SimpleGrantedAuthority("SCOPE_manager");
	
	@Override
    public Authentication authenticate(Authentication auth) 
      throws AuthenticationException {
        String username = auth.getName();
        String password = auth.getCredentials()
            .toString();
        
        String rUser = cfg.getRootUser();
        String rCred = cfg.getRootPassword();
        Collection<GrantedAuthority> roles = new HashSet<GrantedAuthority>();
        roles.add(role);
        if (rUser.equalsIgnoreCase(username) && rCred.equals(password)) {
        	UsernamePasswordAuthenticationToken tok = new UsernamePasswordAuthenticationToken
              (username, password, roles);
        
        	return tok;
        } else {
            throw new 
              BadCredentialsException("Basic authentrication failed for: "+username);
        }
    }
 
    @Override
    public boolean supports(Class<?> auth) {
        return auth.equals(UsernamePasswordAuthenticationToken.class);
    }

}
