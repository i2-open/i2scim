package com.independentid.signals;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;


@JsonInclude(value = JsonInclude.Include.NON_NULL, content = JsonInclude.Include.NON_EMPTY)
public class StreamModels {
    public static final String DeliveryPoll = "urn:ietf:rfc:8936";
    public static final String DeliveryPush = "urn:ietf:rfc:8935";
    public static final String ReceivePoll = "urn:ietf:rfc:8936:receive";
    public static final String ReceivePush = "urn:ietf:rfc:8935:receive";

    public static final String EventScimFeedAdd = EventTypes.FEED_ADD;
    public static final String EventScimFeedRemove = EventTypes.FEED_REMOVE;
    public static final String EventScimCreateFull = EventTypes.PROV_CREATE_FULL;
    public static final String EventScimPutFull = EventTypes.PROV_PUT_FULL;
    public static final String EventScimPatchFull = EventTypes.PROV_PATCH_FULL;
    public static final String EventScimCreateNotice = EventTypes.PROV_CREATE_NOTICE;
    public static final String EventScimPatchNotice = EventTypes.PROV_PATCH_NOTICE;
    public static final String EventScimPutNotice = EventTypes.PROV_PUT_NOTICE;
    public static final String EventScimDelete = EventTypes.PROV_DELETE;
    public static final String EventScimActivate = EventTypes.PROV_ACTIVATE;
    public static final String EventScimDeactivate = EventTypes.PROV_DEACTIVATE;
    public static final String EventScimAsyncResp = EventTypes.MISC_ASYNC_RESP;

    public static ArrayList<String> GetScimEventTypes(boolean inputEvents) {
        ArrayList<String> events = new ArrayList<>();
        // events.add(EventScimFeedAdd);
        // events.add(EventScimFeedRemove);
        events.add(EventScimCreateFull);
        events.add(EventScimPutFull);
        events.add(EventScimPatchFull);
        events.add(EventScimDelete);
        // events.add(EventScimActivate);
        // events.add(EventScimDeactivate);
        // events.add(EventScimSigAuthMethod);
        // events.add(EventScimSigPwdReset);

        if (!inputEvents) {
            events.add(EventScimCreateNotice);
            events.add(EventScimPutNotice);
            events.add(EventScimPatchNotice);
        }
        return events;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegisterResponse {
        @JsonProperty("token")
        public String token;
    }

    public static class RegisterParameters {
        @JsonProperty("scopes")
        public String[] scopes;
        @JsonProperty("email")
        public String email;
        @JsonProperty("description")
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamConfig {
        @JsonProperty("stream_id")
        public String Id;
        @JsonProperty("iss")
        public String Iss;
        @JsonProperty("aud")
        public ArrayList<String> Aud;
        @JsonProperty("events_supported")
        public ArrayList<String> EventsSupported;
        @JsonProperty("events_requested")
        public ArrayList<String> EventsRequested;
        @JsonProperty("events_delivered")
        public ArrayList<String> EventsDelivered;
        @JsonProperty("delivery")
        public Delivery Delivery;
        @JsonProperty("receiverJWKSUrl")
        public String ReceiverJwksUrl;
        @JsonProperty("issuerJWKSUrl")
        public String IssuerJwksUrl;
        @JsonProperty("resetDate")
        public Date ResetDate;
        @JsonProperty("resetJti")
        public String ResetJti;
        @JsonProperty("route_mode")
        public String RouteMode;
    }

    public static class Delivery {
        @JsonProperty("method")
        public String Method;
        @JsonProperty("endpoint_url")
        public String EndpointUrl;
        @JsonProperty("authorization_header")
        public String AuthorizationHeader;
        @JsonProperty("poll_config")
        public PollConfig PollConfig;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PollConfig {
        @JsonProperty("maxEvents")
        public int MaxEvents;
        @JsonProperty("returnImmediately")
        public boolean ReturnImmediately;
        @JsonProperty("ack")
        public ArrayList<String> Acks;
        @JsonProperty("setErrs")
        public Map<String, SetErrorType> SetErrs;
        @JsonProperty("timeoutSecs")
        public int TimeoutSecs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PollResponse {
        @JsonProperty("sets")
        public Map<String, String> Sets;
        @JsonProperty("moreAvailable")
        public boolean MoreAvailable;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SetErrorType {
        @JsonProperty("err")
        public String Error;
        @JsonProperty("description")
        public String Description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransmitterConfig {
        @JsonProperty("issuer")
        public String issuer;
        @JsonProperty("jwks_uri")
        public String jwks_uri;
        @JsonProperty("delivery_methods")
        public ArrayList<String> delivery_methods_supported;
        @JsonProperty("configuration_endpoint")
        public String configuration_endpoint;
        @JsonProperty("status_endpoint")
        public String status_endpoint;
        @JsonProperty("client_registration_endpoint")
        public String client_registration_endpoint;
    }
}
