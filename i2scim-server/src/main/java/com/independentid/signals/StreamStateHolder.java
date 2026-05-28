package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class StreamStateHolder {

    private static final Logger logger = LoggerFactory.getLogger(StreamStateHolder.class);

    @JsonIgnore
    private StreamStatus status = StreamStatus.ENABLED;

    private String errorMsg;

    @JsonIgnore
    private String label;

    @JsonIgnore
    private final List<BiConsumer<StreamStatus, StreamStatus>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Optional human-readable label included in state-transition logs
     * (e.g. {@code "push:stream-abc"}). Set by the owning stream so operators
     * can tell which stream changed without grepping by thread/jti.
     */
    public void setLabel(String label) {
        this.label = label;
    }

    public StreamStatus getStatus() {
        return status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public synchronized void transitionTo(StreamStatus newStatus, String reason) {
        StreamStatus oldStatus = this.status;
        this.status = newStatus;
        this.errorMsg = reason;
        if (oldStatus != newStatus) {
            String who = label == null ? "<unlabeled>" : label;
            if (newStatus == StreamStatus.DISABLED) {
                logger.error("Stream {} state {} -> {} ({})", who, oldStatus, newStatus,
                        reason == null ? "no reason given" : reason);
            } else {
                logger.info("Stream {} state {} -> {}{}", who, oldStatus, newStatus,
                        reason == null ? "" : " (" + reason + ")");
            }
            for (BiConsumer<StreamStatus, StreamStatus> listener : listeners) {
                listener.accept(oldStatus, newStatus);
            }
        }
    }

    public void addTransitionListener(BiConsumer<StreamStatus, StreamStatus> listener) {
        listeners.add(listener);
    }

    @JsonProperty("status")
    StreamStatus getPersistedStatus() {
        return status == StreamStatus.PAUSED ? StreamStatus.ENABLED : status;
    }

    @JsonProperty("status")
    void setStatusFromJson(StreamStatus s) {
        this.status = s;
    }
}
