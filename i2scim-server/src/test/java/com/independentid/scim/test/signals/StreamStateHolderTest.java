package com.independentid.scim.test.signals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.independentid.signals.StreamStateHolder;
import com.independentid.signals.StreamStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamStateHolderTest {

    @Test
    void newHolderIsEnabledWithNoErrorMsg() {
        StreamStateHolder holder = new StreamStateHolder();

        assertThat(holder.getStatus()).isEqualTo(StreamStatus.ENABLED);
        assertThat(holder.getErrorMsg()).isNull();
    }

    @Test
    void transitionToDisabledUpdatesStatusAndErrorMsg() {
        StreamStateHolder holder = new StreamStateHolder();

        holder.transitionTo(StreamStatus.DISABLED, "403 Forbidden");

        assertThat(holder.getStatus()).isEqualTo(StreamStatus.DISABLED);
        assertThat(holder.getErrorMsg()).isEqualTo("403 Forbidden");
    }

    @Test
    void sameStateTransitionDoesNotFireListener() {
        StreamStateHolder holder = new StreamStateHolder();
        List<String> events = new ArrayList<>();
        holder.addTransitionListener((oldS, newS) -> events.add(oldS + "->" + newS));

        holder.transitionTo(StreamStatus.ENABLED, "still enabled");

        assertThat(events).isEmpty();
        assertThat(holder.getStatus()).isEqualTo(StreamStatus.ENABLED);
    }

    @Test
    void pausedStatusSerializesAsEnabledButRuntimeFieldUnchanged() throws Exception {
        StreamStateHolder holder = new StreamStateHolder();
        holder.transitionTo(StreamStatus.PAUSED, "remote paused");

        String json = new ObjectMapper().writeValueAsString(holder);

        assertThat(json).contains("\"status\":\"ENABLED\"");
        assertThat(json).doesNotContain("PAUSED");
        assertThat(holder.getStatus()).isEqualTo(StreamStatus.PAUSED);
    }

    @Test
    void differentStateTransitionFiresListenerWithOldAndNew() {
        StreamStateHolder holder = new StreamStateHolder();
        List<String> events = new ArrayList<>();
        holder.addTransitionListener((oldS, newS) -> events.add(oldS + "->" + newS));

        holder.transitionTo(StreamStatus.PAUSED, "remote paused");
        holder.transitionTo(StreamStatus.ENABLED, "remote re-enabled");

        assertThat(events).containsExactly("ENABLED->PAUSED", "PAUSED->ENABLED");
    }
}
