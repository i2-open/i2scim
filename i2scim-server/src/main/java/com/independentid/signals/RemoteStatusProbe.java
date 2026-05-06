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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class RemoteStatusProbe {

    private static final Logger logger = LoggerFactory.getLogger(RemoteStatusProbe.class);

    private RemoteStatusProbe() {
    }

    public static RemoteStatus probe(CloseableHttpClient client, URI statusUrl, String streamId, String authorization) {
        if (streamId == null || streamId.isEmpty()) {
            logger.warn("Skipping /status probe at {}: missing stream_id (required by SSF §7.1.2)", statusUrl);
            return RemoteStatus.UNKNOWN;
        }
        URI requestUri = withStreamId(statusUrl, streamId);
        HttpGet req = new HttpGet(requestUri);
        if (authorization != null && !authorization.equals("NONE")) {
            req.setHeader("Authorization", authorization);
        }
        try (CloseableHttpResponse resp = client.execute(req)) {
            int code = resp.getCode();
            if (code < 200 || code >= 300) {
                logger.warn("Remote /status returned {} from {}", code, requestUri);
                return RemoteStatus.UNKNOWN;
            }
            HttpEntity entity = resp.getEntity();
            if (entity == null) {
                return RemoteStatus.UNKNOWN;
            }
            byte[] body = entity.getContent().readAllBytes();
            JsonNode node = JsonUtil.getJsonTree(body);
            JsonNode statusNode = node.get("status");
            if (statusNode == null || !statusNode.isTextual()) {
                return RemoteStatus.UNKNOWN;
            }
            return parseStatus(statusNode.asText());
        } catch (Exception e) {
            logger.warn("Remote /status probe failed for {}: {}", requestUri, e.getMessage());
            return RemoteStatus.UNKNOWN;
        }
    }

    static URI withStreamId(URI base, String streamId) {
        String encoded = URLEncoder.encode(streamId, StandardCharsets.UTF_8);
        String existingQuery = base.getRawQuery();
        String separator = (existingQuery == null || existingQuery.isEmpty()) ? "?" : "&";
        String suffix = base.getRawFragment() == null ? "" : "#" + base.getRawFragment();
        String baseStr = base.toString();
        int fragIdx = baseStr.indexOf('#');
        String withoutFrag = fragIdx < 0 ? baseStr : baseStr.substring(0, fragIdx);
        return URI.create(withoutFrag + separator + "stream_id=" + encoded + suffix);
    }

    private static RemoteStatus parseStatus(String value) {
        if (value == null) return RemoteStatus.UNKNOWN;
        return switch (value.toLowerCase()) {
            case "enabled" -> RemoteStatus.ENABLED;
            case "paused" -> RemoteStatus.PAUSED;
            case "disabled" -> RemoteStatus.DISABLED;
            default -> RemoteStatus.UNKNOWN;
        };
    }
}
