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

package com.independentid.scim.plugin;

import com.independentid.scim.core.ConfigMgr;
import com.independentid.scim.core.err.ScimException;
import com.independentid.scim.op.Operation;

import javax.servlet.ServletException;

public interface IScimPlugin {

    default void init() throws ServletException {}

    void doPreOperation(Operation op) throws ScimException;

    void doPostOperation(Operation op) throws ScimException;
}
