package com.independentid.scim.test.set;

import com.independentid.set.SecurityEventToken;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityEventTokenTest {

    @Test
    void getTxnReturnsNullWhenClaimAbsent() throws Exception {
        // Per RFC 8417 §2.2, "txn" is OPTIONAL. A SET without txn (e.g. an SSF
        // verification event) must not blow up the receiver.
        SecurityEventToken event = new SecurityEventToken();
        event.setJti("jti-1");

        assertThat(event.getTxn()).isNull();
    }

    @Test
    void getTxnReturnsValueWhenPresent() throws Exception {
        SecurityEventToken event = new SecurityEventToken();
        event.setTxn("tx-42");

        assertThat(event.getTxn()).isEqualTo("tx-42");
    }
}
