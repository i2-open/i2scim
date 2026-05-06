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
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "stream-1", "NONE");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void probeReturnsUnknownOn500() throws Exception {
        CloseableHttpClient client = mockClientReturning(500, "{}");

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "stream-1", "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    @Test
    void probeReturnsUnknownOnMalformedJson() throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "not-json");

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "stream-1", "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    @Test
    void probeReturnsUnknownOnIOException() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class))).thenThrow(new IOException("connection refused"));

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "stream-1", "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    @Test
    void probeReturnsUnknownOnUnknownStatusValue() throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "{\"status\":\"nonsense\"}");

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "stream-1", "NONE");

        assertThat(result).isEqualTo(RemoteStatus.UNKNOWN);
    }

    @Test
    void probeAppendsStreamIdQueryParamPerSsfSpec() throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "{\"status\":\"enabled\"}");

        RemoteStatusProbe.probe(client, URI.create("https://example.com/ssf/status"), "abc-123", "NONE");

        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getUri().toString()).isEqualTo("https://example.com/ssf/status?stream_id=abc-123");
    }

    @Test
    void probeAppendsStreamIdToExistingQueryParams() throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "{\"status\":\"enabled\"}");

        RemoteStatusProbe.probe(client, URI.create("https://example.com/status?other=1"), "s2", "NONE");

        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getUri().toString()).isEqualTo("https://example.com/status?other=1&stream_id=s2");
    }

    @Test
    void probeUrlEncodesStreamId() throws Exception {
        CloseableHttpClient client = mockClientReturning(200, "{\"status\":\"enabled\"}");

        RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), "id with space&amp", "NONE");

        ArgumentCaptor<HttpGet> captor = ArgumentCaptor.forClass(HttpGet.class);
        verify(client).execute(captor.capture());
        assertThat(captor.getValue().getUri().toString()).isEqualTo("https://example.com/status?stream_id=id+with+space%26amp");
    }

    @Test
    void probeReturnsUnknownWhenStreamIdIsMissing() {
        CloseableHttpClient client = mock(CloseableHttpClient.class);

        RemoteStatus result = RemoteStatusProbe.probe(client, URI.create("https://example.com/status"), null, "NONE");

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
