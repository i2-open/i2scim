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
import com.independentid.scim.op.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.servlet.ServletException;

@ApplicationScoped
@Priority(10)
public class TestPlugin implements IScimPlugin{
    private final static Logger logger = LoggerFactory.getLogger(TestPlugin.class);

    @Override
    public void init() throws ServletException {
        logger.info("Test Plugin Init called.");
    }

    @Override
    public void doPreOperation(Operation op) {
        logger.info("doPreOp called: "+op.getRequest().getRequestURI());

    }

    @Override
    public void doPostOperation(Operation op) {
        logger.info("doPostOp called: "+op.getRequest().getRequestURI());

    }
}
