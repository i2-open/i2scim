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
