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

import com.independentid.scim.resource.Value;

import java.util.List;

/**
 * This interface is for targets that may have a temporary batchId value that needs
 * to be updated prior to posting to a persistence store. 
 * @author pjdhunt
 *
 */
public interface IBulkIdTarget {

	/**
	 * @return true if one of the attribute values in the resource has a bulkid:
	 *         prefixed value that needs to be translated
	 */
	boolean hasBulkIds();
	
	/**
	 * @param bulkList A list provided by the caller which the target will
	 * update with any attributes that have a bulkId attribute that needs replacing.
	 */
	void getBulkIdsRequired(List<String> bulkList);
	
	void getAttributesWithBulkIdValues(List<Value> bulkIdAttrs);
}
