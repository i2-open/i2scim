package com.independentid.scim.test.signals;

import com.independentid.signals.PemReloadWatcher;
import com.independentid.signals.PushStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.security.Key;
import java.util.concurrent.atomic.AtomicReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-B slice #79: proactive PEM watcher. Verifies that out-of-band rotations
 * of an issuer-key PEM are picked up without waiting for a {@code
 * jws_signature_failed} from the receiver.
 */
class PemReloadWatcherTest {

    @TempDir
    Path dir;

    @Test
    void nativeWatcher_firesOnFileModify() throws Exception {
        Path pem = dir.resolve("issuer.pem");
        Files.writeString(pem, "old");

        CountDownLatch fired = new CountDownLatch(1);
        try (PemReloadWatcher watcher = new PemReloadWatcher(pem, fired::countDown)) {
            watcher.start();
            // Slight pause so the WatchService is registered before the write.
            Thread.sleep(200);
            Files.writeString(pem, "new", StandardOpenOption.TRUNCATE_EXISTING);
            assertThat(fired.await(5, TimeUnit.SECONDS))
                    .as("onChange must fire within 5s of native file modify")
                    .isTrue();
        }
    }

    @Test
    void install_returnsNullWhenGatesFail() {
        Path pem = dir.resolve("issuer.pem");
        PushStream push = new PushStream();
        push.enabled = true;

        // pubPemPath = "NONE" → no watcher
        assertThat(PemReloadWatcher.maybeInstall(
                "NONE", "NONE", true, push, () -> stubKey("k")))
                .as("no watcher when pubPemPath is NONE")
                .isNull();

        // pubPemValue set (env-value mode) → no watcher
        assertThat(PemReloadWatcher.maybeInstall(
                pem.toString(), "BASE64==", true, push, () -> stubKey("k")))
                .as("no watcher when pubPemValue is provided")
                .isNull();

        // watch flag disabled → no watcher
        assertThat(PemReloadWatcher.maybeInstall(
                pem.toString(), "NONE", false, push, () -> stubKey("k")))
                .as("no watcher when scim.signals.pub.pem.watch=false")
                .isNull();

        // disabled push stream → no watcher
        PushStream disabled = new PushStream();
        disabled.enabled = false;
        assertThat(PemReloadWatcher.maybeInstall(
                pem.toString(), "NONE", true, disabled, () -> stubKey("k")))
                .as("no watcher when push stream is disabled")
                .isNull();
    }

    @Test
    void install_returnsNullWhenParentDirMissing() {
        // Common in tests / misconfigured deployments: the configured pubPemPath
        // points to a directory that does not exist (e.g. a Kubernetes mount path
        // like /data on a developer machine). The watcher must degrade cleanly
        // and let the server boot — the reactive jws_signature_failed reload
        // path remains as a fallback.
        PushStream push = new PushStream();
        push.enabled = true;
        Path missing = Path.of("/this/path/should/never/exist/issuer.pem");
        assertThat(PemReloadWatcher.maybeInstall(
                missing.toString(), "NONE", true, push, () -> stubKey("k")))
                .as("missing parent directory must not throw at boot")
                .isNull();
    }

    @Test
    void install_swapsIssuerKeyOnFile() throws Exception {
        Path pem = dir.resolve("issuer.pem");
        Files.writeString(pem, "v1");

        PushStream push = new PushStream();
        push.enabled = true;
        Key oldKey = stubKey("old");
        Key newKey = stubKey("new");
        push.issuerKey = oldKey;

        AtomicReference<Key> nextKey = new AtomicReference<>(newKey);

        try (PemReloadWatcher watcher = PemReloadWatcher.maybeInstall(
                pem.toString(),    // pubPemPath
                "NONE",            // pubPemValue
                true,              // watchEnabled
                push,
                nextKey::get)) {
            assertThat(watcher).as("install must succeed when gates pass").isNotNull();
            Thread.sleep(200);
            Files.writeString(pem, "v2", StandardOpenOption.TRUNCATE_EXISTING);
            // Wait up to 5s for the cached key to flip.
            long deadline = System.currentTimeMillis() + 5_000;
            while (push.issuerKey == oldKey && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertThat(push.issuerKey)
                    .as("watcher must replace the cached issuer key")
                    .isSameAs(newKey);
        }
    }

    private static Key stubKey(String label) {
        return new Key() {
            @Override public String getAlgorithm() { return "RSA"; }
            @Override public String getFormat() { return "PKCS#8"; }
            @Override public byte[] getEncoded() { return label.getBytes(); }
        };
    }

    @Test
    void nativeWatcher_firesOnAtomicReplace() throws Exception {
        Path pem = dir.resolve("issuer.pem");
        Path tmp = dir.resolve("issuer.pem.tmp");
        Files.writeString(pem, "old");

        CountDownLatch fired = new CountDownLatch(1);
        try (PemReloadWatcher watcher = new PemReloadWatcher(pem, fired::countDown)) {
            watcher.start();
            Thread.sleep(200);
            // Common rotator pattern: write tmp, then atomically rename over the live file.
            Files.writeString(tmp, "new");
            Files.move(tmp, pem,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            assertThat(fired.await(5, TimeUnit.SECONDS))
                    .as("watcher must observe tmp+rename rotations")
                    .isTrue();
        }
    }

    @Test
    void close_stopsFurtherFires() throws Exception {
        Path pem = dir.resolve("issuer.pem");
        Files.writeString(pem, "v1");

        AtomicInteger fires = new AtomicInteger(0);
        PemReloadWatcher watcher = new PemReloadWatcher(
                pem, fires::incrementAndGet,
                PemReloadWatcher.Mode.POLL,
                Duration.ofMillis(50));
        watcher.start();
        Files.setLastModifiedTime(pem,
                java.nio.file.attribute.FileTime.fromMillis(
                        Files.getLastModifiedTime(pem).toMillis() + 5_000));
        // Wait up to 2s for at least one fire — the test only cares that a
        // pre-close mtime change is observed, not the exact polling cadence.
        long deadline = System.currentTimeMillis() + 2_000;
        while (fires.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        int firesBeforeClose = fires.get();
        assertThat(firesBeforeClose).isGreaterThan(0);

        watcher.close();
        // Mutate again after close. No fire should be observed.
        Files.setLastModifiedTime(pem,
                java.nio.file.attribute.FileTime.fromMillis(
                        Files.getLastModifiedTime(pem).toMillis() + 10_000));
        Thread.sleep(300);
        assertThat(fires.get())
                .as("close() must terminate the watcher loop")
                .isEqualTo(firesBeforeClose);
    }

    @Test
    void pollWatcher_doesNotFireWhenFileUntouched() throws Exception {
        Path pem = dir.resolve("issuer.pem");
        Files.writeString(pem, "stable");

        AtomicInteger fires = new AtomicInteger(0);
        try (PemReloadWatcher watcher = new PemReloadWatcher(
                pem, fires::incrementAndGet,
                PemReloadWatcher.Mode.POLL,
                Duration.ofMillis(50))) {
            watcher.start();
            Thread.sleep(400); // ~8 polling intervals
        }
        assertThat(fires.get())
                .as("poll-fallback path must not fire when mtime is unchanged")
                .isZero();
    }

    @Test
    void pollWatcher_firesWhenMtimeChanges() throws Exception {
        Path pem = dir.resolve("issuer.pem");
        Files.writeString(pem, "old");

        CountDownLatch fired = new CountDownLatch(1);
        try (PemReloadWatcher watcher = new PemReloadWatcher(
                pem, fired::countDown,
                PemReloadWatcher.Mode.POLL,
                Duration.ofMillis(100))) {
            watcher.start();
            // Bump mtime past the watcher's recorded baseline.
            Files.setLastModifiedTime(pem,
                    java.nio.file.attribute.FileTime.fromMillis(
                            Files.getLastModifiedTime(pem).toMillis() + 5_000));
            assertThat(fired.await(2, TimeUnit.SECONDS))
                    .as("poll-fallback path must fire within ~2 polling intervals")
                    .isTrue();
        }
    }
}
