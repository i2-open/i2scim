/*
 * Copyright (c) 2021.
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

package com.independentid.scim.events;

public class SecurityEvent {
    public final static String JWT_ATTR_ISS = "iss";
    public final static String JWT_ATTR_IAT = "iat";
    public final static String JWT_ATTR_JTI = "jti";
    public final static String JWT_ATTR_AUD = "aud";
    public final static String JWT_ATTR_SUB = "sub";

    public final static String SET_ATTR_EVENTS = "events";

    public final static String SET_EVENT_SCIMREPLICATION = "urn:io.exceptional.i2.set.replication";


}
