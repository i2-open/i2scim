/**********************************************************************
 *  Independent Identity - Big Directory                              *
 *  (c) 2015 Phillip Hunt, All Rights Reserved                        *
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
package com.independentid.scim.server;

import org.springframework.stereotype.Component;

import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.ScimResponse;

/**
 * @author pjdhunt
 *
 */

@Component("AuditLog")
public class AuditLog {

	public final static String PARAM_AUDIT_LOGFILE = "scim.audit.logfile";
	public final static String DEFAULT_AUDIT_LOGFILE = "./audit.log";
	
	//private BufferedOutputStream out;
	
	/**
	 * 
	 */
	public AuditLog() {
		
	}
	
	public void logEvent(Operation op, ScimResponse resp) {
		
	}
	
	public void shutdown() {
		
	}
	
	

}
