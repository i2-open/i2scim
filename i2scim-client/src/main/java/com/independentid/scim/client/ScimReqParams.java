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

package com.independentid.scim.client;

import com.independentid.scim.protocol.Filter;
import com.independentid.scim.protocol.ScimParams;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Date;

/**
 * ScimParams holds all HTTP request parameters supported by SCIM (RFC7644) except for Filter.
 */
public class ScimReqParams {
    protected String attrs = null;
    protected String exclAttrs = null;
    protected String sortBy = null;
    protected Boolean sortOrder = null;
    protected int startIndex = -1;
    protected int count = -1;

    protected String head_ifMatch = null;
    protected String head_ifNoMatch = null;
    protected Date head_ifUnModSince = null;
    protected Date head_ifModSince = null;

    public ScimReqParams() {

    }

    public void setAttributesRequested(String attrs) {
        this.attrs = attrs;
    }

    public void setExclAttrs (String attrs) {
        this.exclAttrs = attrs;
    }

    public void sortBy(String attrs) {
        this.sortBy = attrs;
    }

    public void setSortOrder(boolean isAscending) {
        this.sortOrder = isAscending;
    }

    public void setStartIndex(int index) {
        this.startIndex = index;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void setHead_ifMatch(String etag_value) {
        this.head_ifMatch = etag_value;
    }

    public void setHead_ifNoMatch(String etag_value) {
        this.head_ifNoMatch = etag_value;
    }

    public void setHead_ifUnModSince(Date date) {
        this.head_ifUnModSince = date;
    }

    public void setHead_ifUnModSince(String date) throws ParseException {
        this.head_ifUnModSince = i2scimClient.httpDate.parse(date);
    }

    public void setHead_ifModSince(Date date) {
        this.head_ifModSince = date;
    }

    public void setHead_ifModSince(String httpDate) throws ParseException {
        this.head_ifModSince = i2scimClient.httpDate.parse(httpDate);
    }

}
