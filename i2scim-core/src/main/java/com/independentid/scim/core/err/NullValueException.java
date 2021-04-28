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

import javax.validation.constraints.Null;

/**
 * This exception used to flag that a Value cannot be constructed because the input was empty or null. Used by virtual attributes.
 */
public class NullValueException extends ScimException {

    public NullValueException(String message) {
        super(message);
    }
}
