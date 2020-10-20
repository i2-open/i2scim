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

package com.independentid.scim.backend.mongo;

import java.util.Date;

import org.bson.json.Converter;
import org.bson.json.StrictJsonWriter;

import com.independentid.scim.schema.Meta;

public class MongoDateConverter implements Converter<Long> {

	@Override
	public void convert(Long value, StrictJsonWriter writer) {
		//Instant ins = new Date(value).toInstant();
		String sdate = Meta.ScimDateFormat.format(new Date(value));
		writer.writeString(sdate);
		
	}

}
