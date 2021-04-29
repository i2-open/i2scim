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

package com.independentid.scim.core.err;

/**
 * Exception is thrown when a duplicate revision value is detected in meta.revisions.
 */
public class DuplicateTxnException extends ConflictException {

    public DuplicateTxnException(String message) {
        super(message);
    }
}
