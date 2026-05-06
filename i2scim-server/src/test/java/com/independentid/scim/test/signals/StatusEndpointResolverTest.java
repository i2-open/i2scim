package com.independentid.scim.test.signals;

import com.independentid.signals.StatusEndpointResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusEndpointResolverTest {

    @Test
    void wellKnownAdvertisesStatusEndpointReturnsThatUrl() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        stubResponse(client, 200, "{\"status_endpoint\":\"https://recv.example.com/ssf/status\"}");

        StatusEndpointResolver resolver = new StatusEndpointResolver(client);

        Optional<URI> result = resolver.resolve(URI.create("https://recv.example.com"),
                URI.create("https://recv.example.com/ssf/events"));

        assertThat(result).contains(URI.create("https://recv.example.com/ssf/status"));
    }

    @Test
    void wellKnownReachableWithoutStatusEndpointMarksUnsupportedAndReturnsEmpty() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        stubResponse(client, 200, "{\"issuer\":\"foo\"}");

        StatusEndpointResolver resolver = new StatusEndpointResolver(client);

        Optional<URI> result = resolver.resolve(URI.create("https://recv.example.com"),
                URI.create("https://recv.example.com/ssf/events"));

        assertThat(result).isEmpty();
        assertThat(resolver.isMarkedUnsupported(URI.create("https://recv.example.com"))).isTrue();
    }

    @Test
    void wellKnownUnreachableFallsBackToDerivedUrl() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        when(client.execute(any(HttpGet.class))).thenThrow(new IOException("connection refused"));

        StatusEndpointResolver resolver = new StatusEndpointResolver(client);

        Optional<URI> result = resolver.resolve(URI.create("https://recv.example.com"),
                URI.create("https://recv.example.com/ssf/events"));

        assertThat(result).contains(URI.create("https://recv.example.com/ssf/status"));
        assertThat(resolver.isMarkedUnsupported(URI.create("https://recv.example.com"))).isFalse();
    }

    @Test
    void resolveCachesSuccessfulLookup() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        stubResponse(client, 200, "{\"status_endpoint\":\"https://recv.example.com/ssf/status\"}");

        StatusEndpointResolver resolver = new StatusEndpointResolver(client);
        URI host = URI.create("https://recv.example.com");
        URI endpoint = URI.create("https://recv.example.com/ssf/events");

        resolver.resolve(host, endpoint);
        resolver.resolve(host, endpoint);

        verify(client, times(1)).execute(any(HttpGet.class));
    }

    @Test
    void invalidateForcesFreshLookup() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        stubResponse(client, 200, "{\"status_endpoint\":\"https://recv.example.com/ssf/status\"}");

        StatusEndpointResolver resolver = new StatusEndpointResolver(client);
        URI host = URI.create("https://recv.example.com");
        URI endpoint = URI.create("https://recv.example.com/ssf/events");

        resolver.resolve(host, endpoint);
        resolver.invalidate(host);
        resolver.resolve(host, endpoint);

        verify(client, times(2)).execute(any(HttpGet.class));
    }

    @Test
    void unsupportedMarkSticksAcrossSubsequentResolves() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        stubResponse(client, 200, "{\"issuer\":\"foo\"}");

        StatusEndpointResolver resolver = new StatusEndpointResolver(client);
        URI host = URI.create("https://recv.example.com");
        URI endpoint = URI.create("https://recv.example.com/ssf/events");

        resolver.resolve(host, endpoint);
        resolver.resolve(host, endpoint);

        // Once unsupported, no further well-known fetches
        verify(client, times(1)).execute(any(HttpGet.class));
    }

    private static void stubResponse(CloseableHttpClient client, int code, String body) throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        HttpEntity entity = mock(HttpEntity.class);
        when(response.getCode()).thenReturn(code);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(body.getBytes()));
        when(client.execute(any(HttpGet.class))).thenReturn(response);
    }
}
