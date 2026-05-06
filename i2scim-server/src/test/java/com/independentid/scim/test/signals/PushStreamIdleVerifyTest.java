package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.PushStream;
import com.independentid.signals.VerifyEventBuilder;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushStreamIdleVerifyTest {

    @Test
    void successfulPushUpdatesLastSuccessfulPushTimestamp() throws Exception {
        PushStream stream = newStream();
        Instant before = stream.getLastSuccessfulPush();
        Thread.sleep(20);

        stream.client = mockClientReturning(202);

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isTrue();
        assertThat(stream.getLastSuccessfulPush()).isAfter(before);
    }

    @Test
    void verifyEventFlowsThroughPushEventAsRegularSet() throws Exception {
        PushStream stream = newStream();
        stream.client = mockClientReturning(202);

        SecurityEventToken verify = VerifyEventBuilder.build(stream);
        boolean pushed = stream.pushEvent(verify);

        assertThat(pushed).isTrue();
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

    private CloseableHttpClient mockClientReturning(int code) throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getCode()).thenReturn(code);
        when(response.getReasonPhrase()).thenReturn("OK");
        when(client.execute(any(HttpPost.class))).thenReturn(response);
        return client;
    }
}
