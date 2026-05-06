package com.independentid.scim.test.signals;

import com.independentid.signals.RemoteStatus;
import com.independentid.signals.RemoteStatusProbe;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RemoteStatusProbeTest {

    @ParameterizedTest
    @CsvSource({
            "enabled, ENABLED",
            "ENABLED, ENABLED",
            "paused, PAUSED",
            "disabled, DISABLED"
    })
    void probeReturnsParsedStatusFor200Response(String body, RemoteStatus expected) throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "{\"status\":\"" + body + "\"}");

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "NONE");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void probeReturnsUnknownOn500() throws Exception {
        CloseableHttpClient client = mockClientReturning(500, "{}");

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    @Test
    void probeReturnsUnknownOnMalformedJson() throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "not-json");

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    @Test
    void probeReturnsUnknownOnIOException() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class))).thenThrow(new IOException("connection refused"));

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    @Test
    void probeReturnsUnknownOnUnknownStatusValue() throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "{\"status\":\"nonsense\"}");

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    private CloseableHttpClient mockClientReturning(int code, String body) throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        when(response.getCode()).thenReturn(code);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));
        when(client.execute(any(HttpGet.class))).thenReturn(response);
        return client;
    }
}
