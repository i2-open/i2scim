package com.independentid.signals;

import java.io.IOException;

public final class PollFailureClassifier {

    private PollFailureClassifier() {
    }

    public static FailureClassification classify(int httpCode, String reason, byte[] body, String retryAfterHeader) {
        return HttpFailureClassifier.classifyHttp(httpCode, reason, body, retryAfterHeader);
    }

    public static FailureClassification classify(IOException cause) {
        return HttpFailureClassifier.classifyTransport(cause);
    }
}
