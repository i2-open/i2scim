package com.independentid.signals;

import java.io.IOException;

public final class PushFailureClassifier {

    private PushFailureClassifier() {
    }

    public static FailureClassification classify(int httpCode, String reason, byte[] body, String retryAfterHeader) {
        if (httpCode == 400) {
            return Rfc8935ErrorParser.parse(body)
                    .map(err -> (FailureClassification) new FailureClassification.Rfc8935(err))
                    .orElseGet(() -> HttpFailureClassifier.classifyHttp(httpCode, reason, body, retryAfterHeader));
        }
        return HttpFailureClassifier.classifyHttp(httpCode, reason, body, retryAfterHeader);
    }

    public static FailureClassification classify(IOException cause) {
        return HttpFailureClassifier.classifyTransport(cause);
    }
}
