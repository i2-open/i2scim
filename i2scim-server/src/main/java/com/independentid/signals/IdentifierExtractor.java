package com.independentid.signals;

import com.independentid.scim.resource.ComplexValue;
import com.independentid.scim.resource.MultiValue;
import com.independentid.scim.resource.ScimResource;
import com.independentid.scim.resource.StringValue;
import com.independentid.scim.resource.Value;
import com.independentid.scim.schema.SchemaException;

/**
 * Extracts a {@code User}'s primary login-identifier value from a
 * {@link ScimResource}. A pure transformation — no I/O — so it can be
 * unit-tested directly. Slice 3 (#89).
 */
public final class IdentifierExtractor {

    private IdentifierExtractor() {
    }

    /**
     * @param res      a SCIM resource (may be {@code null}).
     * @param attrName an identifier attribute name, e.g. {@code userName} or {@code emails}.
     * @return the primary scalar value of that identifier, or {@code null} when absent.
     */
    public static String primaryValue(ScimResource res, String attrName) {
        if (res == null) return null;
        try {
            Value v = res.getValue(attrName);
            if (v instanceof StringValue) {
                return (String) v.getRawValue();
            }
            if (v instanceof MultiValue) {
                return primaryOf((MultiValue) v);
            }
        } catch (SchemaException e) {
            // attribute undefined for this resource — identifier absent
        }
        return null;
    }

    /**
     * @return the {@code value} sub-attribute of the multi-value's primary entry.
     *         An entry flagged {@code primary} wins; absent any such flag, a lone
     *         entry is treated as primary. Returns {@code null} otherwise.
     */
    private static String primaryOf(MultiValue mv) {
        ComplexValue lone = null;
        int count = 0;
        for (Value entry : mv.values()) {
            if (!(entry instanceof ComplexValue)) continue;
            ComplexValue cv = (ComplexValue) entry;
            if (cv.isPrimary()) {
                return subValue(cv, "value");
            }
            lone = cv;
            count++;
        }
        return (count == 1) ? subValue(lone, "value") : null;
    }

    /** @return the named sub-attribute of a complex value as a string, or {@code null}. */
    private static String subValue(ComplexValue cv, String subName) {
        Value sub = cv.getValue(subName);
        return (sub instanceof StringValue) ? (String) sub.getRawValue() : null;
    }
}
