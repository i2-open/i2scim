package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.PushStream;
import com.independentid.signals.StreamStatus;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.Key;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushStreamRfc8935Test {

    @Test
    void invalidAudienceDisablesWithStructuredErrorMsgIncludingJti() throws Exception {
        PushStream stream = newStream();

        String body = "{\"err\":\"invalid_audience\",\"description\":\"audience mismatch\",\"jti\":\"jti-1\"}";
        stream.client = mockClient400With(body);

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("RFC8935 invalid_audience");
        assertThat(stream.state.getErrorMsg()).contains("audience mismatch");
        assertThat(stream.state.getErrorMsg()).contains("jti=jti-1");
    }

    @Test
    void jwsSignatureFailedWithSuccessfulReloadRetriesOnceAndSucceeds() throws Exception {
        PushStream stream = newStream();
        AtomicInteger reloadCount = new AtomicInteger();
        stream.issuerKeyReloader = () -> {
            reloadCount.incrementAndGet();
            return null; // null is fine; signing path tolerates null (unsigned)
        };

        AtomicInteger pushCallCount = new AtomicInteger();
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpUriRequestBase.class))).thenAnswer(inv -> {
            int calls = pushCallCount.incrementAndGet();
            if (calls == 1) {
                return jsonResponse(400, "{\"err\":\"jws_signature_failed\",\"description\":\"bad sig\",\"jti\":\"jti-1\"}");
            }
            CloseableHttpResponse ok = mock(CloseableHttpResponse.class);
            when(ok.getCode()).thenReturn(202);
            when(ok.getReasonPhrase()).thenReturn("Accepted");
            return ok;
        });
        stream.client = client;

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isTrue();
        assertThat(reloadCount.get()).isEqualTo(1);
        assertThat(pushCallCount.get()).isEqualTo(2);
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    @Test
    void jwsSignatureFailedTwiceDisables() throws Exception {
        PushStream stream = newStream();
        stream.issuerKeyReloader = () -> null;

        String body = "{\"err\":\"jws_signature_failed\",\"description\":\"bad sig\",\"jti\":\"jti-1\"}";
        stream.client = mockClient400With(body);

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("RFC8935 jws_signature_failed");
    }

    @Test
    void jwsSignatureFailedOnUnencryptedStreamDisablesWithoutReload() throws Exception {
        PushStream stream = newStream();
        stream.isUnencrypted = true;
        AtomicInteger reloadCount = new AtomicInteger();
        stream.issuerKeyReloader = () -> {
            reloadCount.incrementAndGet();
            return null;
        };

        String body = "{\"err\":\"jws_signature_failed\",\"description\":\"bad sig\",\"jti\":\"jti-1\"}";
        stream.client = mockClient400With(body);

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(reloadCount.get()).isEqualTo(0);
    }

    private PushStream newStream() {
        PushStream stream = new PushStream();
        stream.endpointUrl = "https://recv.example.com/events";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.issuerKey = null;
        stream.maxRetries = 0;
        stream.initialDelay = 0;
        return stream;
    }

    private CloseableHttpClient mockClient400With(String body) throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpUriRequestBase.class))).thenAnswer(inv -> jsonResponse(400, body));
        return client;
    }

    private CloseableHttpResponse jsonResponse(int code, String body) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        when(response.getCode()).thenReturn(code);
        when(response.getReasonPhrase()).thenReturn(code == 400 ? "Bad Request" : "OK");
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));
        return response;
    }
}
