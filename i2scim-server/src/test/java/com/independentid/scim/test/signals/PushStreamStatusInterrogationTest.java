package com.independentid.scim.test.signals;

import com.independentid.set.SecurityEventToken;
import com.independentid.signals.PushStream;
import com.independentid.signals.StatusEndpointResolver;
import com.independentid.signals.StreamStatus;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushStreamStatusInterrogationTest {

    @Test
    void t1On5xxPlusRemotePausedTransitionsToPaused() throws Exception {
        PushStream stream = newStream();
        stream.maxRetries = 5;

        CloseableHttpClient client = mockedRouting(503, "{\"status_endpoint\":\"https://recv.example.com/status\"}", "{\"status\":\"paused\"}");
        stream.client = client;
        stream.statusResolver = new StatusEndpointResolver(client);

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.PAUSED);
        assertThat(stream.state.getErrorMsg()).contains("paused");
    }

    @Test
    void t1On5xxPlusRemoteDisabledTransitionsToDisabled() throws Exception {
        PushStream stream = newStream();
        stream.maxRetries = 5;

        CloseableHttpClient client = mockedRouting(503, "{\"status_endpoint\":\"https://recv.example.com/status\"}", "{\"status\":\"disabled\"}");
        stream.client = client;
        stream.statusResolver = new StatusEndpointResolver(client);

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(stream.state.getErrorMsg()).contains("disabled");
    }

    @Test
    void t2PreFlightWithRemotePausedSkipsAttemptAndTransitionsToPaused() throws Exception {
        PushStream stream = newStream();

        CloseableHttpClient client = mockedRouting(202, "{\"status_endpoint\":\"https://recv.example.com/status\"}", "{\"status\":\"paused\"}");
        stream.client = client;
        stream.statusResolver = new StatusEndpointResolver(client);

        boolean pushed = stream.pushEvent(new SecurityEventToken());

        assertThat(pushed).isFalse();
        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.PAUSED);
        assertThat(stream.state.getErrorMsg()).contains("pre-flight");
    }

    @Test
    void pausedRecheckTransitionsBackToEnabledWhenRemoteRecovers() throws Exception {
        PushStream stream = newStream();

        CloseableHttpClient client = mockedRouting(202, "{\"status_endpoint\":\"https://recv.example.com/status\"}", "{\"status\":\"enabled\"}");
        stream.client = client;
        stream.statusResolver = new StatusEndpointResolver(client);
        // Simulate stream already being in PAUSED state
        stream.state.transitionTo(StreamStatus.PAUSED, "test setup");

        stream.pausedRecheck();

        assertThat(stream.state.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    private PushStream newStream() {
        PushStream stream = new PushStream();
        stream.streamId = "stream-1";
        stream.endpointUrl = "https://recv.example.com/events";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.issuerKey = null;
        stream.maxRetries = 0;
        stream.initialDelay = 0;
        return stream;
    }

    /**
     * Routes mock responses by URL path: push endpoint returns the configured pushCode;
     * /.well-known/ssf-configuration returns wellKnownBody; status endpoint returns statusBody.
     */
    private CloseableHttpClient mockedRouting(int pushCode, String wellKnownBody, String statusBody) throws IOException {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpUriRequestBase.class))).thenAnswer(inv -> {
            HttpUriRequestBase req = inv.getArgument(0);
            String path = req.getRequestUri();
            if (path.contains("/.well-known/ssf-configuration")) {
                return jsonResponse(200, wellKnownBody);
            }
            if (path.contains("/status")) {
                return jsonResponse(200, statusBody);
            }
            // push endpoint
            CloseableHttpResponse pushResp = mock(CloseableHttpResponse.class);
            when(pushResp.getCode()).thenReturn(pushCode);
            when(pushResp.getReasonPhrase()).thenReturn(pushCode == 503 ? "Service Unavailable" : "OK");
            return pushResp;
        });
        return client;
    }

    private CloseableHttpResponse jsonResponse(int code, String body) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        when(response.getCode()).thenReturn(code);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));
        return response;
    }
}
