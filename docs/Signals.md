# i2scim Security Signals Support

Security Signals are statements about something that has occurred that may be shared asynchronously between a publisher
and a receiver over what is commonly known as an event "stream".

In traditional SIEM (Security Information and Event Management) systems, events are inferred through the sharing of
information such as access logs where an AI system
uses pattern recognition to infer something has occurred. More recently, the IETF defined a universal message format
called a ["Security Event Token" RFC8417](https://www.rfc-editor.org/rfc/rfc8417). The format enables various event
types to be expressed that can be signed and optionally encrypted as a message between parties. For example, an event
may express that a user session was cancelled (logged out), an authentication factor was changed, or an account was
provisioned. In contrast to SIEM, Security Events express distinct conclusions about something that has occurred
allowing
a receiver to take independent action.

## How are Events transferred?

i2scim.io supports two standard mechanisms for point-to-point transfer have been developed:

* [Push-Based Security Event Token (SET) Delivery Using HTTP RFC8935](https://datatracker.ietf.org/doc/html/rfc8935) -
  to transmit events
* [Poll-Based Security Event Token (SET) Delivery Using HTTP RFC8936](https://datatracker.ietf.org/doc/html/rfc8936) -
  to receive events

## What if I want to deliver events to more than one receiver?

Use an SSF service capable of routing and retransmitting events to multiple receivers. i2goSignals is intended for this
purpose and provides additional functionality such as routing and event recovery.

## What is a stream?

When a series of events are shared over a pre-configured Push or Polling connection, this is referred to as a "stream".

Because it becomes possible to have multiple stream relationships, the OpenID Foundation has developed a specification
called the  
[Shared Signal Framework specification](https://openid.net/specs/openid-sharedsignals-framework-1_0-02.html) which
i2scim
is in the process of supporting as an "SSF client".

## How does i2scim support streams?

i2scim can be configured manually to support a single outbound Push Event stream and a single inbound polling event
stream.
It can also talk to an SSF server to automatically configure streams.

## Why doesn't i2scim implement SSF Server functionality?

SSF requires a number of features that are really intended for situations where many streams need to be supported.
Because
i2scim is often deployed in a clustered configuration, no single server processes all events for a single domain
making it impractical for a single receiver to have a single stream from a publishing domain. Because of this, the
decision
was made to develop a Security Event Router called goSignals (soon to be released) which serves the purpose of:

* aggregating feeds from multiple generator nodes and re-distributing to one or more registered receivers.
* supporting data recovery after an outage
* acting as a gateway to on-board events from an external source
* converting between push and poll modes depending on the needs of an Event Receiver
* serving as a cluster-hub enabling peer-to-peer event communication (e.g. replication)
* bridging between different protocols and systems such as message buses (*future*)

# i2scim Signals Configuration Parameters

| Area / Parameter                         | Description                                                                                                               | Default              |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|----------------------|
| **Common**                               |
| scim.signals.enable                      | Enable the signals extension                                                                                              | false                |
| scim.signals.pub.enable                  | Enable outbound events                                                                                                    | true                 |
| scim.signals.rcv.enable                  | Enable incoming events                                                                                                    | true                 |
| scim.signals.pub.iss                     | The issuer value to be used for transmitted SETs (may need to correspond to token signing key)                            | DEFAULT              |
| scim.signals.pub.aud                     | Value to use for audience (may be a comma separated list)                                                                 | example.com          |
| scim.signals.rcv.iss                     | The issuer value expected for received SETs                                                                               | DEFAULT              |
| scim.signals.rcv.aud                     | The audience value expected for received SETs                                                                             | DEFAULT              |
| **SSF Automatic**                        |
| scim.signals.ssf.configFile              | File where dynamic configuration is stored to support restarts                                                            | /scim/ssfConfig.json |
| scim.signals.ssf.serverUrl               | The URL of an Shared Signals Framework Server.<BR>Note: for push, the SSF server must be able to configure PUSH Receivers | NONE                 |
| scim.signals.ssf.authorization           | The authorization header value to pass that enables automatic registration (e.g. an IAT bearer token)                     | NONE                 |
| **Pre Configured Files**                 |
| scim.signals.pub.config.file             | A file containing transmitter stream information. See Using Pre-config below                                              | NONE                 |
| scim.signals.rcv.config.file             | A file containing the receiver stream configuration information. See using Pre-config below                               | NONE                 |
| **Manual Configuration**                 |
| scim.signals.pub.push.endpoint           | The URL of the endpoint where events are to be pushed using RFC8935.                                                      | NONE                 |
| scim.signals.pub.push.auth               | The authorization header value to be used when pushing events to the endpoint                                             | NONE                 |
| scim.signals.rcv.poll.endpoint           | The URL where i2scim may poll for events using RFC8936                                                                    | NONE                 |
| scim.signals.rcv.poll.auth               | The authorization header value to be used when polling                                                                    | NONE                 |
| **Signing and Encryption Configuration** |
| scim.signals.pub.pem.path                | A path to a local file where a PEM encoded PKCS8 private key may be loaded for issuing signed events                      | NONE                 |
| scim.signals.pub.pem.value               | A PEM PKCS8 encoded private key value minus BEGIN and END text.                                                           | NONE                 |
| scim.signals.pub.issJwksUrl              | A URL where the issuer JWKS public key may be loaded from                                                                 | NONE                 |
| scim.signals.pub.algNone.override        | When set to true, events will not be signed.                                                                              | false                |
| scim.signals.pub.aud.jwksurl             | The URL where the audience public key may be loaded. When set, events are encrypted with JWE                              | NONE                 |
| scim.signals.pub.aud.jwksjson            | The audience public key in JSON form. When set, events are encrypted with JWE<BR>Use either jwksurl or jwksjson           | NONE                 |   
| scim.signals.rcv.iss.jwksUrl             | The URL where a JWKS public key may be loaded to validate signed events                                                   | NONE                 |
| scim.signals.rcv.iss.jwksJson            | The issuer public key in JWKS JSON format used to validate signed events<BR>Use either jwksurl or jwksjson                | NONE                 |
| scim.signals.rcv.pem.path                | The location of an PKCS8 PEM format private key which may be used to decrypt JWE encoded events                           | NONE                 |
| scim.signals.rcv.pem.value               | PKCS8 encoded private key value used to decrypt JWE encoded events                                                        | NONE                 |

## Configuration Options

### Automatic Configuration using SSF

> [!NOTE]
> Because authorization for OpenID SSF is not finalized, this procedure is designed to work with the i2gosignals project
> only.

With i2goSignals, using the gosignals tool to generate an IAT token to allow i2scim to auto register. For example:

```shell
goSignals
goSignals> add server ssf1 https://gosignals.example.com:8888
goSignals> create iat ssf1
eyJhbGciOiJSUzI1NiIsImtpZCI6IkRFRkFVTFQiLCJ0eXAiOiJqd3QifQ.eyJz...
goSignals> exit
```

When using the goSignals tool, a generated IAT can be used in the i2scim `scim.signals.ssf.authorization` parameter.
This is particularly useful when defining a cluster where multiple i2scim instances may be started. Each node will then
register itself
using the same IAT token and receive unique stream endpoints.

When registering each i2scim server instance will:

1. Perform a client registration using the supplied IAT token. The i2goSignals SSF server issues a client token with a
   unique identifier but sharing a common project identifier (originating from the IAT)
2. Each i2scim server then requests a publishing stream and a receiver stream using SSF stream configuration management.

Because all instances share the same iss and aud values and a common project identifier the i2goSignals server will
route events between nodes
making it possible to implement replication. When an event is published on one node, it will be automatically forward to
each other node in the cluster since each has its own stream.  
Since each node has its own stream, each node will receive a copy of every event.

Note that automatic configuration is best used when each i2scim server is deployed standalone (e.g. using the Memory
Provider to store data in a file). Each node in the signals cluster needs
to get transactions from every other node in order to synchronize.

When i2scim is deployed as a cluster against a common database (e.g. Mongo), then event replication using signals is not
needed.
In that case, only one node needs inbound events (if needed) and each node can share a common push stream configuration.
In this scenario Pre-configuration or Manual configuration is recommended.

### Pre-Configuration Using Stream Files

In addition to the common and signing parameters, streams can be configured using JSON files which contain the following
attributes:

* token - An authorization header value (similar to scim.signals.pub.push.auth). For example "Bearer eyJhb..."
* endpoint - The endpoint for event delivery or polling (depending on publish or receive)
* iss - The name of the issuer
* issJwksUrl - A URL for the public key of the issuer
* aud - The audience for the events (comma separated if multiple)

Example publish configuration file (e.g. generated by the goSignals tool):

```json
{
  "alias": "sQs",
  "id": "6525f54e86b849e69ca1a779",
  "description": "Push Receiver",
  "token": "Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IkRFRkFVTFQiLCJ0eXAiOiJqd3QifQ.eyJzaWQiOlsiNjUyNWY1NGU4NmI4NDllNjljYTFhNzc5Il0sInByb2plY3RfaWQiOiJBa0NVIiwicm9sZXMiOlsiZXZlbnQiXSwiaXNzIjoiREVGQVVMVCIsImF1ZCI6WyJERUZBVUxUIl0sImV4cCI6MTcwNDc2MjQ0NiwiaWF0IjoxNjk2OTg2NDQ2LCJqdGkiOiIyV2IxUUNoNTBjc2RBVUIyTFZtOTlnYzFYazMifQ.Tk5M7yn64kkfxr_ds9CJXMcifvefxxftq4e_gX9-KZzViUyd1SBNofz-_Dfzh5zIMsl0XBiLXLRofQU_yhsh_yGKGz6_9TlOzmwA3tNclJEeaCySOvtyUZ39D773u60Ss3ydXvTUtai8WE5PV5Qmu3wvyTSiABrTIbTv260MOLuk1hisPYQmpNE06BMCv3LIeBaMggZrJKJRTkCmgxHlgdVUh4BAPRlqiKG0jiCED1z6PHsMUaocT_1gVQEuchRdGgZTRBglMCAVSQibBLqOA6d1BrLGVGUKOMtJNj4tb59TrKpM--QCqAksNM02Kj1nOiiac7tR1BcnxBULhAj3gA",
  "endpoint": "http://goSignals1:8888/events/6525f54e86b849e69ca1a779",
  "iss": "myissuer.io",
  "aud": "myissuer.io",
  "issJwksUrl": ""
}
```

This mode is best uses in cases where multiple i2scim nodes are using the Mongo Provider to store data and thus share a
common database. In this case, every node
should use the same output push stream but only one node should be receiving events on behalf of the entire cluster.

### Manual Configuration

In manual configuration mode, all stream parameters are configured using environment variables as described in the table
above.




