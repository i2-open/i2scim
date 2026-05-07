package com.independentid.scim.test.signals;

import com.independentid.signals.FilePendingAckStore;
import com.independentid.signals.PendingAckRecord;
import com.independentid.signals.PendingAckStore;
import com.independentid.signals.PollStream;
import com.independentid.signals.SignalsEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PRD-B slice #78: with {@link PollStream#ackStore} set, a successful poll/ack
 * call deletes the acknowledged JTIs from the durable store rather than
 * mutating the legacy static {@code SignalsEventHandler.acksPending} list.
 */
class PollStreamAckStoreTest {

    private static final String STREAM_ID = "stream-poll-it";

    @TempDir
    Path tmp;

    private FilePendingAckStore ackStore;

    @BeforeEach
    void setup() {
        ackStore = new FilePendingAckStore(tmp);
        ackStore.init();
    }

    @Test
    void recordAckHelperWritesIntoStoreUnderStreamId() throws Exception {
        PollStream stream = newStream();

        invokeRecordAck(ackStore, stream, "jti-X");

        assertThat(ackStore.count(STREAM_ID)).isEqualTo(1L);
        assertThat(ackStore.peekAll(STREAM_ID)).extracting(PendingAckRecord::jti).containsExactly("jti-X");
    }

    @Test
    void recordAckIsNoOpWhenStoreIsNull() throws Exception {
        PollStream stream = newStream();
        invokeRecordAck(null, stream, "jti-X"); // must not throw
    }

    @Test
    void recordAckIsNoOpWhenStreamIdIsNull() throws Exception {
        PollStream stream = newStream();
        stream.streamId = null;
        invokeRecordAck(ackStore, stream, "jti-X");
        assertThat(ackStore.count(STREAM_ID)).isZero();
    }

    private static void invokeRecordAck(PendingAckStore store, PollStream poll, String jti) throws Exception {
        java.lang.reflect.Method m = SignalsEventHandler.class.getDeclaredMethod(
                "recordAck", PendingAckStore.class, PollStream.class, String.class);
        m.setAccessible(true);
        m.invoke(null, store, poll, jti);
    }

    @Test
    void noListOverloadSourcesAcksFromStoreAndSendsThemInRequestBody() throws Exception {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        ackStore.enqueue(STREAM_ID, "jti-A", t);
        ackStore.enqueue(STREAM_ID, "jti-B", t.plusSeconds(1));

        PollStream stream = newStream();
        stream.ackStore = ackStore;

        CloseableHttpResponse okResp = mock(CloseableHttpResponse.class);
        when(okResp.getCode()).thenReturn(200);
        HttpEntity entity = new StringEntity("{\"sets\":{}}", ContentType.APPLICATION_JSON);
        when(okResp.getEntity()).thenReturn(entity);

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        ArgumentCaptor<HttpPost> captor = ArgumentCaptor.forClass(HttpPost.class);
        when(mockClient.execute(captor.capture())).thenReturn(okResp);
        stream.client = mockClient;

        stream.pollEvents(true, 0);

        HttpPost sent = captor.getValue();
        byte[] body = sent.getEntity().getContent().readAllBytes();
        JsonNode req = new ObjectMapper().readTree(body);
        JsonNode ackArray = req.get("ack");
        assertThat(ackArray.isArray()).isTrue();
        java.util.List<String> jtis = new java.util.ArrayList<>();
        ackArray.forEach(n -> jtis.add(n.asText()));
        assertThat(jtis).containsExactlyInAnyOrder("jti-A", "jti-B");
        assertThat(ackStore.count(STREAM_ID)).isZero();
    }

    @Test
    void successfulAckCallDeletesEachJtiFromStore() throws Exception {
        Instant t = Instant.parse("2026-05-06T10:00:00Z");
        ackStore.enqueue(STREAM_ID, "jti-1", t);
        ackStore.enqueue(STREAM_ID, "jti-2", t.plusSeconds(1));
        ackStore.enqueue(STREAM_ID, "jti-3", t.plusSeconds(2));

        PollStream stream = newStream();
        stream.ackStore = ackStore;

        CloseableHttpResponse okResp = mock(CloseableHttpResponse.class);
        when(okResp.getCode()).thenReturn(200);
        HttpEntity entity = new StringEntity("{\"sets\":{}}", ContentType.APPLICATION_JSON);
        when(okResp.getEntity()).thenReturn(entity);

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(mockClient.execute(any(HttpPost.class))).thenReturn(okResp);
        stream.client = mockClient;

        stream.pollEvents(List.of("jti-1", "jti-2", "jti-3"), true, 0);

        assertThat(ackStore.count(STREAM_ID)).isZero();
    }

    private PollStream newStream() {
        PollStream stream = new PollStream();
        stream.streamId = STREAM_ID;
        stream.endpointUrl = "https://example.com/poll";
        stream.authorization = "NONE";
        stream.iss = "test-issuer";
        stream.aud = "test-audience";
        stream.maxRetries = 0;
        stream.initialDelay = 0;
        stream.maxDelay = 0;
        return stream;
    }
}
