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
import java.util.List;
import java.util.stream.Stream;

/**
 * Filesystem-backed {@link PendingAckStore} for the in-memory SCIM provider.
 * Mirrors the contract of {@link MongoPendingAckStore}. Layout:
 *
 * <pre>
 *   &lt;root&gt;/events/acks/{urlencoded streamId}/{urlencoded jti}.ack
 * </pre>
 *
 * <p>Each {@code .ack} file is a single-line JSON payload with the appliedAt
 * timestamp. Writes go through a sibling {@code .ack.tmp} +
 * {@link Files#move ATOMIC_MOVE} so partially-written files never appear in
 * {@code peekAll} listings.
 */
public final class FilePendingAckStore implements PendingAckStore {

    private static final Logger logger = LoggerFactory.getLogger(FilePendingAckStore.class);

    private static final String EVENTS_DIR = "events";
    private static final String ACKS_DIR = "acks";
    private static final String ACK_SUFFIX = ".ack";
    private static final String TMP_SUFFIX = ".ack.tmp";
    private static final int FORMAT_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT);

    private final Path root;

    public FilePendingAckStore(Path memoryRootDir) {
        this.root = memoryRootDir;
    }

    /** Create the {@code events/acks} subtree. Idempotent. */
    public void init() {
        try {
            Files.createDirectories(acksDir());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot init FilePendingAckStore at " + root, e);
        }
    }

    @Override
    public void enqueue(String streamId, String jti, Instant appliedAt) {
        Path target = pathFor(streamId, jti);
        try {
            Files.createDirectories(target.getParent());
            byte[] bytes = serialize(streamId, jti, appliedAt);
            Path tmp = target.resolveSibling(target.getFileName().toString().replace(ACK_SUFFIX, TMP_SUFFIX));
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("enqueue ack failed for " + streamId + "/" + jti, e);
        }
    }

    @Override
    public List<PendingAckRecord> peekAll(String streamId) {
        Path streamDir = streamDir(streamId);
        if (!Files.isDirectory(streamDir)) return List.of();
        List<PendingAckRecord> records = new ArrayList<>();
        try (Stream<Path> stream = Files.list(streamDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(ACK_SUFFIX))
                    .forEach(p -> {
                        PendingAckRecord r = readSilently(p);
                        if (r != null) records.add(r);
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("peekAll failed for " + streamId, e);
        }
        return records;
    }

    @Override
    public void delete(String streamId, String jti) {
        Path p = pathFor(streamId, jti);
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            logger.warn("delete ack failed for {}/{}: {}", streamId, jti, e.getMessage());
        }
    }

    @Override
    public long count(String streamId) {
        Path streamDir = streamDir(streamId);
        if (!Files.isDirectory(streamDir)) return 0L;
        try (Stream<Path> stream = Files.list(streamDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(ACK_SUFFIX)).count();
        } catch (IOException e) {
            throw new UncheckedIOException("count failed for " + streamId, e);
        }
    }

    private Path acksDir() {
        return root.resolve(EVENTS_DIR).resolve(ACKS_DIR);
    }

    private Path streamDir(String streamId) {
        return acksDir().resolve(encode(streamId));
    }

    private Path pathFor(String streamId, String jti) {
        return streamDir(streamId).resolve(encode(jti) + ACK_SUFFIX);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private byte[] serialize(String streamId, String jti, Instant appliedAt) throws IOException {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("v", FORMAT_VERSION);
        node.put("streamId", streamId);
        node.put("jti", jti);
        node.put("appliedAt", appliedAt.toString());
        return MAPPER.writeValueAsBytes(node);
    }

    private PendingAckRecord readSilently(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            JsonNode node = MAPPER.readTree(bytes);
            return new PendingAckRecord(
                    node.get("streamId").asText(),
                    node.get("jti").asText(),
                    Instant.parse(node.get("appliedAt").asText())
            );
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to read pending-ack file {}: {}", file, e.getMessage());
            return null;
        }
    }
}
