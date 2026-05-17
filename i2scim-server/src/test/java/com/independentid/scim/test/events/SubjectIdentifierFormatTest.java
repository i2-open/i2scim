package com.independentid.scim.test.events;

import com.independentid.set.SubjectIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SubjectIdentifier#forFormat} — building RISC subject
 * identifiers in the {@code email}, {@code username} and {@code phone} formats.
 * A pure transformation, so plain JUnit (no Quarkus). Slice 4 (#90).
 */
public class SubjectIdentifierFormatTest {

    @Test
    public void emailFormatSubjectBuilt() {
        SubjectIdentifier sid = SubjectIdentifier.forFormat("email", "bjensen@example.com");
        assertThat(sid).as("email format subject built").isNotNull();
        assertThat(sid.format).as("format is email").isEqualTo("email");
        assertThat(sid.email).as("email value carried").isEqualTo("bjensen@example.com");
    }

    @Test
    public void usernameFormatSubjectBuilt() {
        SubjectIdentifier sid = SubjectIdentifier.forFormat("username", "bjensen");
        assertThat(sid).as("username format subject built").isNotNull();
        assertThat(sid.format).as("format is username").isEqualTo("username");
        assertThat(sid.username).as("username value carried").isEqualTo("bjensen");
    }

    @Test
    public void phoneFormatSubjectBuilt() {
        SubjectIdentifier sid = SubjectIdentifier.forFormat("phone", "555-555-5555");
        assertThat(sid).as("phone format subject built").isNotNull();
        assertThat(sid.format).as("format is phone").isEqualTo("phone");
        assertThat(sid.phoneNumber).as("phone value carried").isEqualTo("555-555-5555");
    }

    @Test
    public void unsatisfiableFormatReturnsNull() {
        assertThat(SubjectIdentifier.forFormat("email", null))
                .as("null value → no subject").isNull();
        assertThat(SubjectIdentifier.forFormat("scim", "x"))
                .as("scim is not a value-bearing format here → no subject").isNull();
        assertThat(SubjectIdentifier.forFormat("bogus", "x"))
                .as("unknown format → no subject").isNull();
    }
}
