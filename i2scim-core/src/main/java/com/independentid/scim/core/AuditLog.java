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
package com.independentid.scim.core;

import com.independentid.scim.op.Operation;
import com.independentid.scim.protocol.ScimResponse;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * @author pjdhunt
 *
 */

@ApplicationScoped
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
