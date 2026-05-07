package com.independentid.signals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.Key;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * PRD-B slice #79 — proactive PEM-file watcher ("N5 PemReloadWatcher").
 *
 * <p>Watches an issuer-key PEM file for out-of-band rotations and invokes
 * {@code onChange} so the holder can invalidate cached key material before
 * the next push, instead of waiting for a {@code jws_signature_failed}
 * response from the receiver.
 */
public final class PemReloadWatcher implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PemReloadWatcher.class);

    public enum Mode { NATIVE, POLL }

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(5);

    private final Path file;
    private final Runnable onChange;
    private final Mode mode;
    private final Duration pollInterval;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private WatchService watchService;
    private Thread thread;

    public PemReloadWatcher(Path file, Runnable onChange) {
        this(file, onChange, Mode.NATIVE, DEFAULT_POLL_INTERVAL);
    }

    public PemReloadWatcher(Path file, Runnable onChange, Mode mode, Duration pollInterval) {
        this.file = file.toAbsolutePath();
        this.onChange = onChange;
        this.mode = mode;
        this.pollInterval = pollInterval;
    }

    public void start() {
        if (mode == Mode.POLL) {
            startPollLoop();
            return;
        }
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            Path dir = this.file.getParent();
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
            this.thread = new Thread(this::watchLoop, "pem-reload-watcher");
            this.thread.setDaemon(true);
            this.thread.start();
        } catch (IOException e) {
            logger.error("Failed to start PEM watcher for {}: {}", file, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void startPollLoop() {
        this.thread = new Thread(this::pollLoop, "pem-reload-watcher-poll");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    private void pollLoop() {
        long lastMtime = currentMtime();
        while (!closed.get()) {
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                return;
            }
            long now = currentMtime();
            if (now != lastMtime) {
                lastMtime = now;
                try {
                    onChange.run();
                } catch (RuntimeException re) {
                    logger.error("PEM onChange handler threw: {}", re.getMessage(), re);
                }
            }
        }
    }

    private long currentMtime() {
        try {
            return Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException e) {
            return -1L;
        }
    }

    private void watchLoop() {
        Path fileName = file.getFileName();
        while (!closed.get()) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }
            for (var event : key.pollEvents()) {
                Object ctx = event.context();
                if (ctx instanceof Path p && p.getFileName().equals(fileName)) {
                    try {
                        onChange.run();
                    } catch (RuntimeException re) {
                        logger.error("PEM onChange handler threw: {}", re.getMessage(), re);
                    }
                }
            }
            if (!key.reset()) return;
        }
    }

    /**
     * Install a watcher for the given push stream's issuer PEM, gated on the
     * PRD-B "N5" rules: active only when {@code pubPemPath != "NONE"} AND
     * {@code pubPemValue == "NONE"} AND {@code watchEnabled} AND the stream is
     * itself enabled. On detected change the cached {@code issuerKey} is
     * replaced via the supplied reloader so the next push uses the new key
     * without restart. Returns {@code null} when any gate fails, leaving the
     * caller with no resource to close.
     */
    public static PemReloadWatcher maybeInstall(
            String pubPemPath,
            String pubPemValue,
            boolean watchEnabled,
            PushStream push,
            Supplier<Key> issuerKeyReloader) {
        if (!watchEnabled) return null;
        if (pubPemPath == null || pubPemPath.equals("NONE")) return null;
        if (pubPemValue != null && !pubPemValue.equals("NONE")) return null;
        if (push == null || !push.enabled) return null;
        if (issuerKeyReloader == null) return null;

        Path pemPath = Paths.get(pubPemPath).toAbsolutePath();
        Path parent = pemPath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            logger.warn("PEM reload watcher not installed: parent directory not found for {}",
                    pemPath);
            return null;
        }
        PemReloadWatcher watcher = new PemReloadWatcher(pemPath, () -> {
            Key replacement = issuerKeyReloader.get();
            if (replacement != null) push.issuerKey = replacement;
        });
        try {
            watcher.start();
        } catch (RuntimeException e) {
            logger.warn("PEM reload watcher could not start for {}: {}",
                    pemPath, e.getMessage());
            return null;
        }
        logger.info("PEM reload watcher installed for {}", pemPath);
        return watcher;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (watchService != null) {
            try { watchService.close(); } catch (IOException ignore) {}
        }
        if (thread != null) thread.interrupt();
    }
}
