package com.independentid.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StatusEndpointResolver {

    private static final Logger logger = LoggerFactory.getLogger(StatusEndpointResolver.class);
    private static final String WELL_KNOWN_PATH = "/.well-known/ssf-configuration";

    private final CloseableHttpClient client;
    private final Map<String, URI> cache = new ConcurrentHashMap<>();
    private final Set<String> unsupported = ConcurrentHashMap.newKeySet();

    public StatusEndpointResolver(CloseableHttpClient client) {
        this.client = client;
    }

    public Optional<URI> resolve(URI receiverHost, URI fallbackEndpoint) {
        String key = hostKey(receiverHost);
        if (unsupported.contains(key)) {
            return Optional.empty();
        }
        URI cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<URI> wellKnown = fetchWellKnownStatusEndpoint(receiverHost);
        if (wellKnown.isPresent()) {
            cache.put(key, wellKnown.get());
            return wellKnown;
        }
        if (unsupported.contains(key)) {
            return Optional.empty();
        }
        Optional<URI> derived = deriveStatusUrl(fallbackEndpoint);
        if (derived.isPresent()) {
            cache.put(key, derived.get());
        }
        return derived;
    }

    public void markUnsupported(URI receiverHost) {
        unsupported.add(hostKey(receiverHost));
    }

    public void invalidate(URI receiverHost) {
        String key = hostKey(receiverHost);
        cache.remove(key);
        unsupported.remove(key);
    }

    public boolean isMarkedUnsupported(URI receiverHost) {
        return unsupported.contains(hostKey(receiverHost));
    }

    private Optional<URI> fetchWellKnownStatusEndpoint(URI receiverHost) {
        URI wellKnownUri = receiverHost.resolve(WELL_KNOWN_PATH);
        HttpGet req = new HttpGet(wellKnownUri);
        try (CloseableHttpResponse resp = client.execute(req)) {
            int code = resp.getCode();
            if (code < 200 || code >= 300) {
                logger.warn("Well-known fetch returned {} for {}", code, wellKnownUri);
                return Optional.empty();
            }
            HttpEntity entity = resp.getEntity();
            if (entity == null) {
                markUnsupported(receiverHost);
                return Optional.empty();
            }
            byte[] body = entity.getContent().readAllBytes();
            JsonNode root = JsonUtil.getJsonTree(body);
            JsonNode statusNode = root.get("status_endpoint");
            if (statusNode == null || !statusNode.isTextual()) {
                logger.info("Well-known at {} has no status_endpoint; marking T1-unsupported for this stream",
                        wellKnownUri);
                markUnsupported(receiverHost);
                return Optional.empty();
            }
            return Optional.of(URI.create(statusNode.asText()));
        } catch (Exception e) {
            logger.warn("Well-known fetch failed for {}: {}", wellKnownUri, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<URI> deriveStatusUrl(URI endpoint) {
        if (endpoint == null) return Optional.empty();
        String path = endpoint.getPath();
        if (path == null || path.isEmpty()) return Optional.empty();
        int lastSlash = path.lastIndexOf('/');
        String parent = lastSlash >= 0 ? path.substring(0, lastSlash) : "";
        String derivedPath = parent + "/status";
        try {
            return Optional.of(new URI(
                    endpoint.getScheme(),
                    endpoint.getAuthority(),
                    derivedPath,
                    null,
                    null
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String hostKey(URI receiverHost) {
        return receiverHost.getScheme() + "://" + receiverHost.getAuthority();
    }
}
