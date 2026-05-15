![i2scim.io](github-logo-i2scim.png)

# What is **i2scim**?

**i2scim** is a Kubernetes (K8S) deployable server implementation of the IETF SCIM 
specification for provisioning of 
Identities as an directory service. **i2scim** is as a generalized SCIM engine that supports configured endpoints 
and schemas defined in json. Unlike other SCIM implementations, **i2scim** does not have fixed resource types.
**i2scim** reads a K8S `configMap` containing JSON formatted definitions of resources and attributes (aka SCIM Schema).
At its core, **i2scim** is a JSON document centric engine that converts from the SCIM Restful HTTP API to backend 
persistence services such as MongoDb.

This open source project licensed under the Apache License 2.0.

**i2scim** is extensible in key ways:
* It can configured to support custom attributes/claims, and object types for special purposes
* For deployers looking to implement SCIM as a standard User provisioning API for their application, **i2scim** may be
adapted to act as a gateway to internal proprietary identity APIs by implementing a custom provider.
* It has a built in events interface that can be used to trigger async events and notifications (more to come).

- For more information on System for Cross-domain Identify Management(SCIM), See "What is SCIM?" below.

## Recent Updates

# Release 0.10.2

This release wires i2scim into the i2goSignals observability stack
(Loki + Prometheus) so dev operators get unified logs and metrics for
SCIM peers alongside goSignals services. JSON logging is env-gated; the
prod default (text format on stdout) is unchanged.

* **Env-gated JSON console logging** — Set `QUARKUS_LOG_CONSOLE_JSON=true`
  to emit JSON records whose keys match the i2goSignals Loki schema:
  `time`, `level`, `msg`, `component`, plus the static fields `service`
  (`"i2scim"`) and `version`. Default off; prod stdout stays text.
* **Per-container identity** — `NODE_ID` and `CLUSTER_NAME` env vars are
  appended to every JSON record so `{node_id="..."}` and
  `{cluster_name="..."}` Loki/LogQL queries select individual peers.
  Unset → `"unknown"` sentinel (SmallRye Config rejects an empty
  `additional-field.value`).
* **Prometheus metrics at `/q/metrics`** — The Micrometer endpoint moves
  from `/metrics` to `/q/metrics` so it sits under Quarkus's
  non-application root and is reachable anonymously (matching the
  `/q/health` posture). Use `Accept: text/plain;version=0.0.4` for the
  legacy Prometheus text format; the default Accept negotiation returns
  OpenMetrics 1.0.0. K8s manifests updated to point
  `prometheus.io/path` at the new path.
* **Startup banner** — The server logs `i2scim server v<version>` on
  startup so the version is visible in the unified log stream without
  needing to inspect the image tag.

# Release 0.10.1

This release makes i2scim's Signals client behave the way operators of
[goSignals](https://github.com/i2-open/i2goSignals/blob/master/docs/operations.md)
expect, and ensures pending events and acks survive a restart.

* **Streams now report a clear lifecycle state**
    * Streams are `enabled`, `paused`, or `disabled` instead of a single
      error flag. `paused` self-recovers; `disabled` means an operator
      needs to look. Existing `ssfConfig.json` files upgrade automatically.
    * HTTP failures are handled by code: 401 retries briefly, 403 stops
      immediately, 429 honors `Retry-After` and never auto-disables, 5xx
      and network errors back off and retry.
    * On push or poll failure, i2scim asks the remote's `/status` endpoint
      whether it has paused itself — distinguishing a remote outage from
      a remote that's deliberately quiet.
    * A keepalive event is sent on idle push streams, so a broken path is
      caught before real events pile up.
    * Protocol errors from the receiver (RFC 8935 §2.4) are recorded with
      the specific error code and JTI in `errorMsg`. A signature failure
      automatically reloads the issuer PEM and retries once.
    * New config: `scim.signals.pub.unauthorized.retry.{max,delay}`,
      `scim.signals.pub.status.check.interval`,
      `scim.signals.pub.idle.verify.interval`, plus `rcv.*` equivalents.

* **Pending events and acks survive restart**
    * Failed push events are persisted — to MongoDB on the Mongo backend,
      to `<scim.prov.memory.dir>/events/` on the memory backend. Restarts
      no longer lose them, and a `disabled` stream keeps its queue for
      automatic replay when re-enabled.
    * SCIM operations no longer wait on push outcome. A slow receiver
      can no longer slow down the SCIM API.
    * Retry budgets are now wall-clock based (default 6 hours) instead
      of attempt counts.
    * Poll-side acks are persisted too, so a restart between "applied
      locally" and "ack delivered" doesn't cause re-delivery.
    * The issuer PEM file is watched for changes — out-of-band key
      rotations are picked up automatically.
    * Events generated before stream registration completes are buffered
      and replayed once registration succeeds. A persistent failure
      raises clear warnings.
    * Disk and queue usage is monitored with configurable
      warn / critical / fatal thresholds.
    * New config: `scim.signals.pub.retry.elapsed.limit`,
      `scim.signals.pub.pem.watch`,
      `scim.signals.pub.storage.{warn,crit,fatal}.pct`,
      `scim.signals.pub.preregister.warn.minutes`.

* **Security and fixes**
    * Patched CVE-2026-39852 and CVE-2026-41417; refreshed dependencies.
    * JWKS load failures at boot are now non-fatal — a temporarily
      unreachable JWKS no longer blocks startup.
    * `/status` probes append the `stream_id` query parameter per
      SSF §7.1.2.
    * Fixed NPE on receipt of inbound verify events.
    * Quieted noisy CycloneDX and retry-worker shutdown warnings.

# Release 0.10.0

This release collapses the build from ten Maven modules into three (`i2scim-core`, `i2scim-client`, `i2scim-server`) and hardens the deployment image.

* **Three-module structure** — Provider, signals, packaging, and tests are now part of `i2scim-server`. Root `mvn install` works without the prior `-N` workaround. See [DECISIONS.md](../DECISIONS.md), 2026-05-04 entry.
* **Single Docker image** — `independentid/i2scim-universal:<tag>` is the only published image. Backend selection is runtime via `scim.prov.providerClass`. The previous per-backend images (`i2scim-mem`, `i2scim-mongo`) are no longer built.
* **Supply-chain hardening** — Image carries OCI labels (`org.opencontainers.image.*`), an embedded CycloneDX SBOM at `/sbom/i2scim.cdx.json`, and SLSA build provenance. `build.sh -p` produces multi-arch (linux/amd64, linux/arm64) attested builds.
* **Maven Central publishing dormant** — Release plugins removed; `i2scim-server` sets `maven.deploy.skip=true`. To restore, follow [docs/publishing.md](publishing.md).
* **Canonical SCIM schemas** in `i2scim-core` — `scimSchema.json`, `scimCommonSchema.json`, `scimFixedSchema.json`, `resourceTypes.json` are loaded from the core JAR's classpath instead of duplicated in each module.

# Release 0.9.1

* Updated org.apache.httpcomponents.client5.httpclient5 to 5.4.3 to address CVE-2025-27820

# Release 0.9.0

This release introduces several enhancements and bug fixes, including support for RESTEasy Reactive and improved test stability.

*   **New Features**:
  * TLS enhancements to support SPIFFE compatibility for SSF communications
  * Ability to load CA and trust certificates from environment variables
    * `scim.signals.ssf.trust.certs.path` and `scim.signals.ssf.trust.certs.value` configuration properties.
*   **Updates**: Updated to Quarkus 3.34.3 and Java 25.  Updated dependencies to latest compatible versions.
*   **RESTEasy Reactive Support**: Standardized on RESTEasy Reactive for improved performance and reduced resource consumption.
*   **Test Stability**: Resolved `ConcurrentModificationException` in `PollStream.pollEvents` by using `CopyOnWriteArrayList` for tracking acknowledgments and pending operations in `SignalsEventHandler`, and reduced initialization sleep during tests to prevent stalls.

# Release 0.8.1

This release adds support for specifying CA trust certificate roots for the SSF (Shared Signals Framework) server via environment variables and updates the project to Quarkus 3.30.8.

*   **SSF CA Trust Support**: Added `scim.signals.ssf.trust.certs.path` and `scim.signals.ssf.trust.certs.value` configuration properties. This enables secure HTTPS connections to SSF servers using self-signed or SPIFFE cluster certificates for discovery, JWKS retrieval, and event streams.
*   **Quarkus Update**: Upgraded the project to Quarkus platform version `3.30.8` and resolved compatibility issues for Java 17/21.
*   **Bug Fixes**: Resolved `InaccessibleObjectException` failures in `i2scim-signals` tests by adding necessary JVM `--add-opens` exports for SSL context introspection.

# Release 0.8.0

I2 SCIM has been updated to support the latest[ SCIM Events draft](https://www.ietf.org/archive/id/draft-ietf-scim-events-16.html) which includes:
* Updated Event URIs
* Support for Asynchronous Event Processing

Other bug fixes include:
* Improved connection handling when pushing or polling for events with an SSF server (e.g. i2gosignals).
* Updated to Java 21 and Eclipse Temurin hardened image

# Release 0.7.0

* *New* Support for Security Events. For more information see the [Signals documentation](Signals.md).
    * Support for [SCIM-Events](https://datatracker.ietf.org/doc/draft-ietf-scim-events/) draft
    * Initial implementation
      of [OpenID Shared Signals Framework SSF draft 02.](https://openid.net/specs/openid-sharedsignals-framework-1_0-02.html)
* Updated to recent Quarkus Platform (3.1.1.Final)
* Combined universal distribution allowing selection of backend store by environment settings
* Improved Docker compose compatibility

# Release 0.6.1

* Fixes for Javadocs and related CVE
* Amended testing code for JWT signing keys
* All i2scim modules available in maven

# Release 0.6.0-Alpha

* *New* Support for Open Policy Agent adding externalized access policy for i2scim.
  See [i2scim Access Control With OPA](OPA_AccessControl.md).

# Release 0.5.0-Alpha

* *Initial Public Release* of i2scim. First public "alpha" release of i2scim for preview purposes. Server can be deployed using K8S using a mongo database or built-in memory based provider.

In this release:
* Basic SCIM Protocol functionality except for Bulk requests per RFC7644
* Configurable resource types and schema and support for RFC7643
* Support for HTTP Conditionals as per RFC7232
* Access control system evolved from LDAP access control models (see AccessControl.md)
* Basic and JWT based authentication via the Quarkus SmallRye JWT module
* Quarkus platform docker modules

## What is i2scim useful for?
**i2scim** is a K8S deployable service that supports scenarios such as:
* An extensible identity data store for customer/user accounts shared by one or more services in a K8S cluster.
* An account provisioning service for integration with enterprise provisioning connectors.
* A standardized, interoperable web gateway for an internal database or API.
* An event engine that can be used to trigger and receive asynchronous events via message queues such as Apache Kafka.
  
## How do I get started?

* Github
    * [GitHub Repository](https://github.com/i2-open/i2scim)
    * [Discussions](https://github.com/i2-open/i2scim/discussions)
    * [Issues](https://github.com/i2-open/i2scim/issues)
    * [Contributing](CONTRIBUTING.md)
    * [Notes for developers](DeveloperNotes.md)
* Quick Starts
    * [Building and running locally](#building-and-running) — see below.
    * [Kubernetes deployment (memory or MongoDB backend)](../i2scim-server/k8s/README.md).
* General Documentation
    * [Configuration](Configuration.md) - i2scim Configuration Properties
    * [i2scim Access Control](AccessControl.md) - Standalone access control using i2scim
    * Open Policy Agent [Integrated Access Control](OPA_AccessControl.md) - Access control using an external [OPA Agent](https://www.openpolicyagent.org).

## Building and Running

i2scim is a three-module Maven project (`i2scim-core`, `i2scim-client`, `i2scim-server`) on Java 25 and Quarkus 3.34.x.

```bash
# Build everything (skips tests by default):
mvn install

# Build + run tests (requires MongoDB on localhost:27017 with admin/t0p-Secret):
mvn install -DskipTests=false

# Run the server in dev mode at http://localhost:8080/ :
mvn -pl i2scim-server quarkus:dev

# Build a multi-arch Docker image and push to docker.io/independentid:
./build.sh -p --tag <ver>
```

The published Docker image is `independentid/i2scim-universal:<tag>`. The same image runs against the in-memory backend or MongoDB; the choice is made at runtime via `scim.prov.providerClass`. See [Configuration](Configuration.md) for the full property list and [k8s/README.md](../i2scim-server/k8s/README.md) for cluster deployment.

## i2scim Feature Details

* Configurable schema support - i2scim supports resource type schema definitions (as described in RFC7643) loaded 
  through K8S ConfigMap definitions. 
* Full SCIM V2 (RFC7644) protocol support including JSON Patch. Bulk support is planned for a future 
  release.
* Support for HTTP HEAD and HTTP Conditional [RFC7232](https://datatracker.ietf.org/doc/html/rfc7232) qualifiers.
* Kubernetes deployment using docker on Intel and ARM64 (e.g. Raspberry Pi).
* SmallRye DevOps Health, Liveness and performance interceptor support ready (e.g. grafana).
* Event system enables support for enhancements such as Apache Kafka and server-to-server multi-master replication (see
  other).
* Security features
    * [Access Control](AccessControl.md) support - acis are defined in json format (as a configuration file) and are an evolved  
      version of many popular LDAP server ACI formats. i2scim acis are intended ot support the requirements defined in:
      [RFC2820](https://datatracker.ietf.org/doc/rfc2820/).
    * HTTP Authentication Mechanisms
        * [RFC7523](https://tools.ietf.org/html/rfc7523) JWT Bearer tokens - i2scim uses
          the [Quarkus SmallRye JWT](https://quarkus.io/guides/security-jwt) libraries for authentication.
        * [RFC7617](https://tools.ietf.org/html/rfc7617) HTTP Basic - i2scim supports HTTP basic authentication of users
          against Users stored in i2scim.
    * Secure password support using PBKDF2 (Password Basked Key Derivation Function 2) with salt and pepper hash for
      FIPS 140 compliance.
    * Note: at this time, i2scim does not support a web (html) interface and does not have built in support for
      session control (cookies) for browsers. Each HTTP request is individually authenticated and authorized.
* Other features:
    * i2scim may be adapted to act as a gateway (by implelementing the IScimProvider interface) databases and API 
      services.
    * Supports "virtual" attribute extensions enabling custom mapping and handling (e.g. password policy).
    * `IScimPlugin` interface enables pre and post transaction custom actions.
    * `IEventHandler` interface enables deployment of asynchronous event handlers (e.g. for replication or security
      events)
      `IVirtualValue` enables support for derived or calculated values.
    * Built on the [Quarkus](https://quarkus.io) platform version 2.16.3.Final for smaller deployments with faster 
      startup
      running in Docker containers.

Note: Inter-SCIM server replication services are not currently part of this project and are currently only supported as
part of a database cluster. For fault-tolerant scaled systems use i2scim deployed
with a [MongoDB cluster on K8S](../i2scim-server/k8s/README.md) along with an enterprise MongoDB
deployment.

## Where can I get more help if needed?
Open Source i2scim is maintained by Independent Identity Incorporated on a best effort sponsored basis.
For more information, please email [info@independentid.com](mailto:pinfo@independentid.com).
-----
## What is SCIM?

SCIM (System for Cross-domain Identity Management) is an IETF specified protocol and schema designed to support 
simple cloud identity management over a REST-ful HTTP service.
See: 
 * [Introduction to SCIM](Intro-to-SCIM.md).
 * [SimpleCloud SCIM Information](https://simplecloud.info). 

In SCIM, objects are called `Resources` which have an identified schema. Like XML, a SCIM Schema describes an object,
the attributes contained, along with their syntax, mutability, etc. For example a username is usually unique across 
a domain. Unlike XML, SCIM schema is not used as a strict enforcement mechanism. After-all JSON is just JSON. 
However Schema definitions help inform parties on how to parse and use data discovered in an endpoint. These can be 
discovered using the `/Schemas` endpoint. To help SCIM protocol clients understand what resources types are 
available, SCIM servers provide and endpoint called 
`Resourcetypes` that lists the resources available on the server.

### JSON and Schema? What?
At the time of writing the SCIM protocols, REST-ful APIs were in vogue. One of the observations of the SCIM Working 
Group, is that SCIM was an HTTP based service that would be implemented by many different developers and 
organizations. This stood in stark contrast to services like the Facebook API. There were many client implementers 
but only 1 organization supporting Facebook's API. Unlike most APIs, SCIM needed mutual interoperability. WG members 
recognized that every SCIM service provider would likely be somewhat different. In order to make interop possible, 
the SCIM schema was developed. 

## Supply Chain Security

i2scim provides proper supply chain attestations to ensure the integrity and provenance of its builds.

*   **SBOM (Software Bill of Materials)**: A CycloneDX SBOM is generated during the build process, providing a comprehensive list of all dependencies.
*   **Build Provenance**: Artifact attestations are generated using GitHub's native support for SLSA-compliant build provenance. This allows users to verify that the artifacts were built in a trusted environment.
*   **Artifact Signing**: JARs and Docker images are attested using Sigstore, enabling cryptographic verification without the need for manual GPG key management (though GPG signing is still supported in the release profile).

How SCIM and XML are alike:
* Schema defines attributes, their syntax, mutability, optionality, visibility, etc.
* The ability to register attribute names and their meanings (with IANA).

How SCIM and XML are NOT alike:
* All SCIM messages and data are just JSON
* No schema enforcement of JSON payloads. For example, undefined attributes are allowed and free to be ignored.
* SCIM follows [Postel's Law - The Robustness Principal](https://en.wikipedia.org/wiki/Robustness_principle).
What this means in practical terms, is that SCIM protocol clients are allowed to send non-conforming messages to 
  SCIM service providers. Service providers are allowed to accept what they can understand. Likewise, in their 
  response, service providers indicate what was accepted and clients must accept the response. For example, if a 
  service provider does not support a particular attribute, the service provider is free to ignore attempts to set a 
  value for the attribute. Even though there may be a broad dictionary of attributes about all people, applications 
  are free to take what they need. 

  
