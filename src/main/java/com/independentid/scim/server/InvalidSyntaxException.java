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
 * @author pjdhunt
 * InvalidSyntaxException - The request body message structure was invalid or
 * did not conform to the request schema.
 */
public class InvalidSyntaxException extends ScimException {

	private static final long serialVersionUID = 1L;
	
	public final static String SCIM_TYPE = ScimResponse.ERR_TYPE_SYNTAX;
	
	/**
	 * InvalidSyntaxException - The request body message structure was 
	 * invalid or did not conform to the request schema.   
	 */
	public InvalidSyntaxException() {
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	/**
	 * InvalidSyntaxException - The request body message structure was 
	 * invalid or did not conform to the request schema.   
	 * @param message A description of the error to be returned in error detail
	 */
	public InvalidSyntaxException(String message) {
		super(message);
		this.scimType = SCIM_TYPE;
		this.status = 400;
	}

	public InvalidSyntaxException(Throwable cause) {
		super(cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

	public InvalidSyntaxException(String message, Throwable cause) {
		super(message, cause);
		this.scimType = SCIM_TYPE;
		this.status = 400;
		this.detail = cause.getLocalizedMessage();
	}

}
