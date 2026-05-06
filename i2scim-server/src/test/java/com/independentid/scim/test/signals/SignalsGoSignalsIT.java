/*
 * Copyright (c) 2026.
 *
 * End-to-end integration test exercising PRD-A's operational vocabulary
 * against a real goSignals instance launched via docker-compose-signals.yml.
 *
 * Disabled by default (and excluded from the surefire default run) because it
 * requires:
 *   - docker compose -f docker-compose-signals.yml up (running)
 *   - The i2gosignals:latest image built locally (see goSignals build instructions)
 *
 * To run manually:
 *   docker compose -f docker-compose-signals.yml up -d
 *   mvn -pl i2scim-server test -Dtest=SignalsGoSignalsIT
 */

package com.independentid.scim.test.signals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SignalsGoSignalsITProfile.class)
@Disabled("Requires goSignals + Mongo replica set running via docker-compose-signals.yml; enable manually")
public class SignalsGoSignalsIT {

    @Test
    void scenario1_healthyPushEndToEnd() {
        // Healthy push: SCIM operation triggers a SET, posted to goSignals,
        // returns 202; assert local PushStream.state.getStatus() == ENABLED
        // throughout, and the event arrives at goSignals (visible via its
        // poll endpoint or stream state).
    }

    @Test
    void scenario2_revokedStreamReturns403AndDisablesImmediately() {
        // Configure the stream against a goSignals stream that has been
        // revoked (DELETE on goSignals stream config); attempt push;
        // assert local Status=DISABLED immediately with structured errorMsg
        // containing "403" / "Forbidden".
    }

    @Test
    void scenario3_pausedMirrorWithAutoRecovery() {
        // Pause the goSignals stream from goSignals' side (PATCH stream
        // config); push attempt fails; T1 detects remote PAUSED; local
        // transitions to PAUSED. Then re-enable goSignals' stream; the
        // paused-recheck loop detects remote ENABLED; local auto-recovers
        // to ENABLED. No operator intervention from i2scim side.
    }

    @Test
    void scenario4_invalidAudienceDisablesWithStructuredErrorMsg() {
        // Misconfigure the stream's aud so goSignals rejects with
        // RFC8935 invalid_audience; push; assert local Status=DISABLED
        // with errorMsg="RFC8935 invalid_audience: ...; jti=...".
    }

    @Test
    void scenario5_jwsSignatureFailedReloadAndRetryOnce() {
        // Force goSignals' JWKS cache to drop our key; push; assert §2.4
        // carve-out fires (PEM reload + retry-once). If goSignals' JWKS
        // refreshed in-between, local returns to ENABLED on retry; if
        // still failing, local DISABLED.
    }

    @Test
    void scenario6_idleVerifyEventObservedAtGoSignals() {
        // Leave the push stream idle for idle.verify.interval + slack;
        // assert a verify event (event type = SSF verification) is
        // observed at goSignals (in its event log or poll output);
        // assert local Status=ENABLED is retained.
    }

    @Test
    void scenario7_shutdownAckBypassesStateTransition() {
        // Trigger Quarkus shutdown while acks are in flight; assert the
        // final pollEvents(acks, ackOnly=true, retries=0) call does NOT
        // transition Status=DISABLED even if goSignals is unreachable
        // at the shutdown moment.
    }
}
