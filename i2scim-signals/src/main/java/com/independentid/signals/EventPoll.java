package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.independentid.scim.serializer.JsonUtil;
import org.apache.http.HttpEntity;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EventPoll {

    private final static Logger logger = LoggerFactory.getLogger(EventPoll.class);

    public static class PollRequest {
        @JsonProperty(value = "maxEvents")
        public int MaxEvents;

        @JsonProperty(value = "returnImmediately")
        public boolean ReturnImmediately;

        @JsonProperty(value="acks")
        public List<String> Acks;

        @JsonProperty(value="setErrors")
        public List<String> SetErrors;

        @JsonProperty(value="timeoutSecs") public int TimeoutSecs;

        public synchronized void PrepareAcknowledgments(List<String> acknowledgments) {
            List<String> acks = new ArrayList<String>();
            Iterator<String> ackIter = acknowledgments.listIterator();

            while (ackIter.hasNext()) {
                acks.add(ackIter.next());
                ackIter.remove();
            }
            this.Acks = acks;
        }

        public void RestoreAcks(List<String> serverAcks) {
            if (this.Acks != null)
                serverAcks.addAll(0,this.Acks);
        }

        public HttpEntity toEntity() throws JsonProcessingException, UnsupportedEncodingException {
            String requestString = JsonUtil.getMapper().writeValueAsString(this);
            return new StringEntity(requestString);

        }
    }

    public static PollResponse Parse(HttpEntity body) throws IOException {

        return JsonUtil.getMapper().readValue(body.getContent(), PollResponse.class);


    }
    public static class PollResponse {
        @JsonProperty(value="sets",required = true)
        public List<String> Sets;

        @JsonProperty(value="moreAvailable")
        public boolean MoreAvailble;


    }



}
