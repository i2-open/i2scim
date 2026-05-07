package com.independentid.signals;

import java.time.Instant;

public record PendingAckRecord(String streamId, String jti, Instant appliedAt) {
}
