package com.independentid.signals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Filesystem-backed {@link PendingPushStore} for the in-memory SCIM provider. Mirrors
 * the contract of {@link MongoPendingPushStore}. Layout under {@code <root>/events/}:
 *
 * <pre>
 *   events/pending/{urlencoded streamId}/{urlencoded jti}.set
 *   events/preregister/{urlencoded streamId}/{urlencoded jti}.set
 * </pre>
 *
 * <p>Each {@code .set} file is a JSON metadata line, a {@code \n} separator, then the
 * encoded JWT payload bytes. State is encoded by directory; transitionState moves the
 * file between state directories atomically. All writes go through a sibling
 * {@code .set.tmp} + {@link Files#move ATOMIC_MOVE}, so partially-written files never
 * appear in {@code peekOldest} listings.
 */
public final class FilePendingPushStore implements PendingPushStore {

    private static final Logger logger = LoggerFactory.getLogger(FilePendingPushStore.class);

    private static final String EVENTS_DIR = "events";
    private static final String SET_SUFFIX = ".set";
    private static final String TMP_SUFFIX = ".set.tmp";
    private static final int FORMAT_VERSION = 1;

    /** Single-line metadata header — must NEVER pretty-print or the \n separator is ambiguous. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT);

    private final Path root;

    public FilePendingPushStore(Path memoryRootDir) {
        this.root = memoryRootDir;
    }

    /** Create the {@code events/<state>} subtree. Idempotent. */
    public void init() {
        try {
            for (PendingPushState s : PendingPushState.values()) {
                Files.createDirectories(stateDir(s));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot init FilePendingPushStore at " + root, e);
        }
    }

    @Override
    public void enqueue(PendingPushRecord record) {
        Path target = pathFor(record.streamId(), record.jti(), record.state());
        try {
            Files.createDirectories(target.getParent());
            byte[] bytes = serialize(record);
            Path tmp = target.resolveSibling(target.getFileName().toString().replace(SET_SUFFIX, TMP_SUFFIX));
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("enqueue failed for " + record.streamId() + "/" + record.jti(), e);
        }
    }

    @Override
    public List<PendingPushRecord> peekOldest(String streamId, PendingPushState state, int limit) {
        Path streamDir = pathFor(streamId, state);
        if (!Files.isDirectory(streamDir)) return List.of();
        List<PendingPushRecord> records = new ArrayList<>();
        try (Stream<Path> stream = Files.list(streamDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(SET_SUFFIX))
                    .forEach(p -> {
                        PendingPushRecord r = readSilently(p, state);
                        if (r != null) records.add(r);
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("peekOldest failed for " + streamId, e);
        }
        records.sort(Comparator.comparing(PendingPushRecord::queuedAt));
        if (records.size() > limit) return records.subList(0, limit);
        return records;
    }

    @Override
    public void markAttempted(String streamId, String jti, Instant attemptedAt, String errorMsg) {
        for (PendingPushState s : PendingPushState.values()) {
            Path p = pathFor(streamId, jti, s);
            if (!Files.exists(p)) continue;
            PendingPushRecord existing = readSilently(p, s);
            if (existing == null) return;
            PendingPushRecord updated = new PendingPushRecord(
                    existing.streamId(), existing.jti(), existing.payload(), existing.state(),
                    existing.queuedAt(), existing.attemptCount() + 1, attemptedAt, errorMsg, existing.bytes()
            );
            enqueue(updated);
            return;
        }
    }

    @Override
    public void transitionState(String streamId, String jti, PendingPushState newState) {
        for (PendingPushState s : PendingPushState.values()) {
            if (s == newState) continue;
            Path src = pathFor(streamId, jti, s);
            if (!Files.exists(src)) continue;
            Path dest = pathFor(streamId, jti, newState);
            try {
                Files.createDirectories(dest.getParent());
                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "transitionState failed for " + streamId + "/" + jti, e);
            }
            return;
        }
    }

    @Override
    public void delete(String streamId, String jti) {
        for (PendingPushState s : PendingPushState.values()) {
            Path p = pathFor(streamId, jti, s);
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                logger.warn("delete failed for {}/{} state={}: {}",
                        streamId, jti, s, e.getMessage());
            }
        }
    }

    @Override
    public long count(String streamId, PendingPushState state) {
        Path streamDir = pathFor(streamId, state);
        if (!Files.isDirectory(streamDir)) return 0L;
        try (Stream<Path> stream = Files.list(streamDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(SET_SUFFIX)).count();
        } catch (IOException e) {
            throw new UncheckedIOException("count failed for " + streamId, e);
        }
    }

    @Override
    public long totalBytes(String streamId) {
        long total = 0L;
        for (PendingPushState s : PendingPushState.values()) {
            for (PendingPushRecord r : peekOldest(streamId, s, Integer.MAX_VALUE)) {
                total += r.bytes();
            }
        }
        return total;
    }

    private Path stateDir(PendingPushState state) {
        return root.resolve(EVENTS_DIR).resolve(state.name());
    }

    private Path pathFor(String streamId, PendingPushState state) {
        return stateDir(state).resolve(encode(streamId));
    }

    private Path pathFor(String streamId, String jti, PendingPushState state) {
        return pathFor(streamId, state).resolve(encode(jti) + SET_SUFFIX);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private byte[] serialize(PendingPushRecord r) throws IOException {
        ObjectNode meta = MAPPER.createObjectNode();
        meta.put("v", FORMAT_VERSION);
        meta.put("streamId", r.streamId());
        meta.put("jti", r.jti());
        meta.put("queuedAt", r.queuedAt().toString());
        meta.put("attemptCount", r.attemptCount());
        if (r.lastAttemptAt() != null) meta.put("lastAttemptAt", r.lastAttemptAt().toString());
        else meta.putNull("lastAttemptAt");
        if (r.lastError() != null) meta.put("lastError", r.lastError());
        else meta.putNull("lastError");
        meta.put("bytes", r.bytes());
        byte[] header = MAPPER.writeValueAsBytes(meta);
        byte[] payload = r.payload() == null ? new byte[0] : r.payload().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[header.length + 1 + payload.length];
        System.arraycopy(header, 0, out, 0, header.length);
        out[header.length] = (byte) '\n';
        System.arraycopy(payload, 0, out, header.length + 1, payload.length);
        return out;
    }

    private PendingPushRecord readSilently(Path file, PendingPushState state) {
        try {
            byte[] all = Files.readAllBytes(file);
            int sep = -1;
            for (int i = 0; i < all.length; i++) {
                if (all[i] == '\n') { sep = i; break; }
            }
            if (sep < 0) {
                logger.warn("Skipping malformed pending-push file (no separator): {}", file);
                return null;
            }
            byte[] headerBytes = new byte[sep];
            System.arraycopy(all, 0, headerBytes, 0, sep);
            byte[] payloadBytes = new byte[all.length - sep - 1];
            System.arraycopy(all, sep + 1, payloadBytes, 0, payloadBytes.length);

                JsonNode meta = MAPPER.readTree(headerBytes);

            Instant lastAttemptAt = meta.hasNonNull("lastAttemptAt") ? Instant.parse(meta.get("lastAttemptAt").asText()) : null;
            String lastError = meta.hasNonNull("lastError") ? meta.get("lastError").asText() : null;

            return new PendingPushRecord(
                    meta.get("streamId").asText(),
                    meta.get("jti").asText(),
                    new String(payloadBytes, StandardCharsets.UTF_8),
                    state,
                    Instant.parse(meta.get("queuedAt").asText()),
                    meta.get("attemptCount").asInt(),
                    lastAttemptAt,
                    lastError,
                    meta.get("bytes").asLong()
            );
        } catch (IOException e) {
            logger.warn("Failed to read pending-push file {}: {}", file, e.getMessage());
            return null;
        }
    }
}
