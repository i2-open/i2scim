package com.independentid.scim.test.signals;

import com.independentid.signals.Rfc8935Error;
import com.independentid.signals.Rfc8935ErrorParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class Rfc8935ErrorParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "authentication_failed",
            "invalid_request",
            "invalid_audience",
            "invalid_issuer",
            "invalid_key",
            "jws_signature_failed",
            "jwe_decryption_failed"
    })
    void parsesAllKnownCodes(String code) {
        String body = "{\"err\":\"" + code + "\",\"description\":\"why\"}";

        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse(body.getBytes());

        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo(code);
        assertThat(result.get().description()).isEqualTo("why");
    }

    @Test
    void parsesJtiWhenPresent() {
        String body = "{\"err\":\"invalid_audience\",\"description\":\"d\",\"jti\":\"abc-123\"}";

        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse(body.getBytes());

        assertThat(result).isPresent();
        assertThat(result.get().jti()).contains("abc-123");
    }

    @Test
    void missingDescriptionStillParses() {
        String body = "{\"err\":\"invalid_audience\"}";

        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse(body.getBytes());

        assertThat(result).isPresent();
        assertThat(result.get().description()).isNull();
    }

    @Test
    void unknownErrCodeReturnsEmpty() {
        String body = "{\"err\":\"some_new_code\"}";

        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse(body.getBytes());

        assertThat(result).isEmpty();
    }

    @Test
    void nonJsonBodyReturnsEmpty() {
        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse("not-json".getBytes());

        assertThat(result).isEmpty();
    }

    @Test
    void jsonWithoutErrFieldReturnsEmpty() {
        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse("{\"foo\":\"bar\"}".getBytes());

        assertThat(result).isEmpty();
    }

    @Test
    void nullBodyReturnsEmpty() {
        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse(null);

        assertThat(result).isEmpty();
    }

    @Test
    void emptyBodyReturnsEmpty() {
        Optional<Rfc8935Error> result = Rfc8935ErrorParser.parse(new byte[0]);

        assertThat(result).isEmpty();
    }
}
