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

import com.independentid.scim.protocol.ScimResponse;

/**
 * In SCIM, indicates that the ETag hash did not match.
 * @author pjdhunt
 *
 */
public class PreconditionFailException extends ScimException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PreconditionFailException() {
		this.status = ScimResponse.ST_PRECONDITION;
		this.scimType = null;
		this.detail = null;
	}

	public PreconditionFailException(String message) {
		super(message);
		this.status = ScimResponse.ST_PRECONDITION;
		this.scimType = null;
		
	}

	public PreconditionFailException(String message, String scimType) {
		super(message, scimType);
		this.status = ScimResponse.ST_PRECONDITION;
		
	}

	public PreconditionFailException(Throwable cause) {
		super(cause);
		this.status = ScimResponse.ST_PRECONDITION;
	}

	public PreconditionFailException(String message, Throwable cause) {
		super(message, cause);
		this.status = ScimResponse.ST_PRECONDITION;
	}

}
