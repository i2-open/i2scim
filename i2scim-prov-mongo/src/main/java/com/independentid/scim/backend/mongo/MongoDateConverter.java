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

package com.independentid.scim.backend.mongo;

import java.util.Date;

import org.bson.json.Converter;
import org.bson.json.StrictJsonWriter;

import com.independentid.scim.resource.Meta;

public class MongoDateConverter implements Converter<Long> {

	@Override
	public void convert(Long value, StrictJsonWriter writer) {
		//Instant ins = new Date(value).toInstant();
		String sdate = Meta.ScimDateFormat.format(new Date(value));
		writer.writeString(sdate);
		
	}

}
