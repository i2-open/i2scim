package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class StreamStateHolder {

    @JsonIgnore
    private StreamStatus status = StreamStatus.ENABLED;

    private String errorMsg;

    @JsonIgnore
    private final List<BiConsumer<StreamStatus, StreamStatus>> listeners = new CopyOnWriteArrayList<>();

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
