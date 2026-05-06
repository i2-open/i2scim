package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.PushStream;
import com.independentid.signals.VerifyEventBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyEventBuilderTest {

    @Test
    void buildProducesEventWithSsfVerificationEventType() throws Exception {
        PushStream stream = newStream();

        SecurityEventToken event = VerifyEventBuilder.build(stream);

        String json = event.toJsonString();
        assertThat(json).contains("https://schemas.openid.net/secevent/ssf/event-type/verification");
    }

    @Test
    void buildPopulatesIssuerFromStream() throws Exception {
        PushStream stream = newStream();

        SecurityEventToken event = VerifyEventBuilder.build(stream);

        assertThat(event.getIssuer()).isEqualTo("test-issuer");
    }

    @Test
    void buildPopulatesAudienceFromStream() throws Exception {
        PushStream stream = newStream();

        SecurityEventToken event = VerifyEventBuilder.build(stream);

        assertThat(event.getAud()).contains("test-audience");
    }

    @Test
    void buildPopulatesJtiAndIat() throws Exception {
        PushStream stream = newStream();

        SecurityEventToken event = VerifyEventBuilder.build(stream);

        assertThat(event.getJti()).isNotBlank();
        assertThat(event.getIssuedAt()).isNotNull();
    }

    @Test
    void buildProducesUniqueJtiPerCall() throws Exception {
        PushStream stream = newStream();

        SecurityEventToken first = VerifyEventBuilder.build(stream);
        SecurityEventToken second = VerifyEventBuilder.build(stream);

        assertThat(first.getJti()).isNotEqualTo(second.getJti());
    }

    private PushStream newStream() {
        PushStream stream = new PushStream();
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        return stream;
    }
}
