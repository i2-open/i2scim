/*
 * Copyright (c) 2020.
 *
 * Confidential and Proprietary
 *
 * This unpublished source code may not be distributed outside
 * “Independent Identity Org”. without express written permission of
 * Phillip Hunt.
 *
 * People at companies that have signed necessary non-disclosure
 * agreements may only distribute to others in the company that are
 * bound by the same confidentiality agreement and distribution is
 * subject to the terms of such agreement.
 */
package com.independentid.scim.op;


/**
 * @author pjdhunt
 *
 */
public interface IBulkIdResolver {

	/**
	 * @param bulkId
	 *            A bulkId to be translated. The value may begin with "bulkId:"
	 *            or be the plain bulkId to be looked up
	 * @return A String containing the translated value for the associated
	 *         bulkId.
	 */
    String translateId(String bulkId);

	/**
	 * @param bulkId
	 *            A bulkId to be translated. The value may begin with "bulkId:"
	 *            or be the plain bulkId to be looked up
	 * @return A String containing the translated value for the associated
	 *         bulkId expressed as a URL.
	 */
	String translateRef(String bulkId);

	
}
