# i2scim Configuration

i2scim is configured using environment properties and JSON configuration files.

## Configuration files

### acis.json
The `acis.json` contains the access policies for the server. For more information see [Access Control](AccessControl.md).

### resourceTypes.json
Contains the definitions about what resources are defined in the server. The format corresponds to that
returned from the `/ResourceTypes` endpoint and is described in 
[RFC7643 Section 6](https://datatracker.ietf.org/doc/html/rfc7643#section-6).

### scimSchema.json
Contains the attribute definitions and properties for documents within SCIM. The format of this file corresponds 
with the `/Schemas` endpoint of a normal SCIM server and is defined in [RFC7643 Section 7](https://datatracker.ietf.org/doc/html/rfc7643#section-7).

## Environment Properties
The following properties are used to configure the I2 SCIM server:

### Basic

The I2 SCIM server implements a dynamically configured schema system. That is to say, there are no *hard-coded* resource types and new ones may be added on demand. In the current release, the system requires a reboot to load new definitions.

**scim.schema.path** - The path to an initial JSON schema file to load into the server. The default loads the default from [RFC7643](https://tools.ietf.org/html/rfc7643).

**scim.resourcetype.path** - The path to an initial JSON file to load resource type definitions into the server. The file defines endpoints defined in [RFC7643](https://tools.ietf.org/html/rfc7643).

**scim.coreSchema.path** - The path to an initial JSON file that defines common SCIM attributes such as id, externalId, meta that are found on all SCIM resources. Normally this file cannot be changed, however, some attribute qualities such as return-ability can be altered.

**scim.thread.count** - The maximum number of *worker* threads per server. While an I2 server may serve thousands of simultaneous requests, requests are processed through a set of worker threads to optimize throughput to backend persistence services. This number should be tuned based on processor and database characteristics.

**scim.json.pretty** - When true, JSON results are formatted with spacing and line-returns for easy reading.  (DEFAULT: false)

**logging.level.com.independentid.scim** - The console logging level desired. (DEFAULT: info)

### Persistence
As currently implemented, I2 SCIM supports the MongoDB as its persistence database due to it document centric architecture. 

**scim.provider.bean** - Indicates a named bean that implements the IScimProvider interface. (Default: MongoDao / I2 SCIM MongoProvider).

#### Mongo Configuration

**scim.mongodb.uri** - The URI of the Mongo DB service. (Default: mongodb://localhost:27017)

**scim.mongodb.dbname** - The name of the database to use in Mongo. (Default: SCIM)

**scim.mongodb.indexes** - SCIM attributes to index. (Default: User:userName,User:emails.value,Group:displayName)

**scim.mongodb.test** - When enabled, the I2 SCIM Server will re-initialize the database including re-loading the default schema from json files. CAUTION: This will destroy all data identified by scim.mongodb.dbname. (Default: false) 

### Security

**scim.security.acis.path** - The path to JSON file containing the server access control instructions (acis).

**scim.security.enable** - This parameter be used to disable authentication and authorization in the server. This is most often used for protocol testing and in certain deployment configurations where security is applied by another component. (DEFAULT: true)

**scim.security.authen.jwt** - This parameter is used to turn on support for JWT Bearer tokens. See spring.security.oauth2.jwt

**smallrye.jwt.always-check-authorization** - Set to true when JWT enabled.

**mp.jwt.verify.issuer** - The beaer token issuer value

**smallrye.jwt.verify.key.location** - The URI used to locate the JWKS public key set for the token issuer.  This method is preferred as the server can load new keys automatically should the issuer change keys.

### SSF Trust Configuration

These properties allow the SSF client to trust custom CA roots (e.g., for self-signed or internal cluster certificates).

**scim.signals.ssf.trust.certs.path** - The file path to a PEM-encoded CA certificate or bundle used to verify the SSF server's certificate. (DEFAULT: NONE)

**scim.signals.ssf.trust.certs.value** - A PEM-encoded CA certificate or bundle string used to verify the SSF server's certificate. This is specifically designed for providing certificate roots via environment variables. (DEFAULT: NONE)

**scim.signals.ssf.serverUrl** - The base URL of the SSF server. Custom trust roots will be applied to all connections to this host, including `.well-known` configuration and JWKS retrieval. (DEFAULT: NONE)

### SCIM Protocol
**scim.query.max.resultsize** - The maximum number of resources returned in a query (DEFAULT: 1000).

**scim.bulk.max.ops** - The maximum number of operations that can be submitted in a single bulk request (DEFAULT: 1000).

**scim.bulk.max.errors** - The maximum number of errors that can be tolerated in a single bulk request (DEFAULT: 5). Note that the result returned indicates which operations succeeded, which one failed, and those aborted.

### SCIM Signals
**scim.signals.rcv.retry.max** - The maximum number of times to retry a polling connection on error (DEFAULT: 10).

**scim.signals.rcv.retry.interval** - The initial retry interval in milliseconds (DEFAULT: 2000).

**scim.signals.rcv.retry.maxInterval** - The maximum retry interval in milliseconds when using backoff (DEFAULT: 300000).

**scim.signals.pub.retry.max** - **Deprecated** (PRD-B). The maximum number of times to retry a push connection on error (DEFAULT: 10). For the per-stream `PushRetryWorker` (PRD-B), this is now an *attempt-count overlay* on top of `scim.signals.pub.retry.elapsed.limit`: when set to `0`, the worker uses pure elapsed-time semantics (the operations.md `RETRY_LIMIT=6h` model). When `> 0`, retries stop once the cap is reached even if the elapsed budget has time left. The legacy `PushStream.pushEvent` (idle-verify and admin-driven sends) continues to honor this property unchanged.

**scim.signals.pub.retry.interval** - The initial retry interval in milliseconds (DEFAULT: 2000).

**scim.signals.pub.retry.maxInterval** - The maximum retry interval in milliseconds when using backoff (DEFAULT: 300000).

**scim.signals.pub.retry.elapsed.limit** - PRD-B elapsed-time recovery budget for transport / 5xx push failures, in milliseconds (DEFAULT: 21600000 — 6h, matching operations.md `RETRY_LIMIT`). When `now - queuedAt >= elapsed.limit`, the per-stream `PushRetryWorker` transitions the stream to `DISABLED` with reason `"transport recovery exceeded 6h"` (or whatever value is configured). Pending JTIs are retained in the durable queue for operator-driven re-enable.

**scim.signals.pub.pem.watch** - PRD-B slice #79. When `true` (default), an out-of-band rotation of `scim.signals.pub.pem.path` is detected proactively via `java.nio.file.WatchService` (with a 5s mtime-poll fallback) and the cached issuer key on the active `PushStream` is refreshed before the next push. Set to `false` to disable the watcher and rely solely on the reactive `jws_signature_failed` reload path. Has no effect when `scim.signals.pub.pem.value` is set (env-value mode requires a restart to roll the key anyway).

#### RISC events (PRD #86)

i2scim can additionally derive **OpenID RISC** events (OpenID RISC Profile 1.0) from SCIM `User` state changes and emit them as their own Security Event Tokens on the existing SET push stream. RISC emission is opt-in and additive to — and gated by — `scim.signals.enable` / `scim.signals.pub.enable`; it does not bypass them. Each RISC event is delivered as its own SET with a distinct `jti`, sharing the `txn` and `toe` of the SCIM events derived from the same operation so a receiver can correlate them. RISC events are emitted only for `User` resources.

**scim.signals.risc.enable** - Master switch for RISC event emission (DEFAULT: `false`). When `false`, upgrading i2scim never changes what existing receivers receive.

**scim.signals.risc.types** - Comma-separated list of RISC event types to emit, by short name: `account-purged`, `account-disabled`, `account-enabled`, `identifier-changed`. A single `*` (the DEFAULT) emits all supported types.

**scim.signals.risc.identifier.attrs** - Comma-separated list of `User` attributes whose primary value is treated as a login identifier for `identifier-changed` derivation (DEFAULT: `userName,emails`). A singular attribute (e.g. `userName`) uses its scalar value; a multi-valued attribute (e.g. `emails`, `phoneNumbers`) uses the `value` of the entry flagged `primary`, treating a lone value as primary when no `primary` flag is set.

**scim.signals.risc.identifier.addRemoveIsChange** - When `true` (the DEFAULT), a pure add or removal of an identifier value (one side absent) counts as an Identifier Changed event. When `false`, only a value-to-value change emits.

**scim.signals.risc.subject.format** - The subject identifier format carried by all RISC events: one of `scim` (the DEFAULT), `email`, `username`, or `phone`. With `scim` the subject is the stable `id`/`uri` of the `User`. With `email`/`username`/`phone` the subject carries the primary value of the corresponding `User` attribute (`emails`, `userName`, `phoneNumbers`); for Identifier Changed this is the *prior* (pre-image) value, with the new value still conveyed in the payload. When the configured format cannot be satisfied for a given `User` (e.g. `email` format but the `User` has no email), the event falls back to the `scim` subject so it is still emitted.

RISC events are derived as follows:

- **account-purged** — a `User` DELETE.
- **account-enabled** / **account-disabled** — a change to a `User`'s `active` attribute (an absent `active` is treated as enabled). A CREATE always emits, reflecting the resulting state. A PATCH that `add`s/`replace`s `active` (including a path-less `value` object) emits the new value — even when unchanged — and a PATCH that `remove`s `active` emits `account-enabled`. A PUT emits only when `active` actually changes versus the prior state.
- **identifier-changed** — a change to the primary value of a configured identifier attribute (`scim.signals.risc.identifier.attrs`) on a `User` PUT or PATCH; CREATE and DELETE never emit it. Each changed identifier attribute yields its own SET. With the default `scim` subject format the SET carries the stable `id`/`uri` subject and conveys the new identifier value in the event payload as `new-value`. Under a non-`scim` `scim.signals.risc.subject.format` the subject instead carries the prior identifier value and the payload still conveys the new value.

#### Operator re-enable after DISABLED (PRD-B)

A stream transitioned to `DISABLED` by the elapsed-time cap retains all pending JTIs in `pendingPushes` (Mongo) or `events/pending/<streamId>/` (memory). Re-enable is done through the existing PRD-A control surface — programmatically calling `pushStream.state.transitionTo(StreamStatus.ENABLED, null)` on the active `SsfHandler.getPushStream()`. The per-stream `PushRetryWorker` registers a transition listener that wakes the idle-sleeping worker thread on `DISABLED → ENABLED`, so the queue resumes draining (in `queuedAt` order) within milliseconds rather than after the next idle window. The first 2xx response is the operational marker that the stream is healthy again; if the receiver is still failing, the elapsed-time cap fires again and the stream is re-DISABLED. Pending JTIs are never deleted by a DISABLE — only by a successful 2xx delivery.

#### Pending-push durability (PRD-B)

When the server cannot deliver a SET to a downstream receiver, the producer
records the signed token to a durable queue and a per-stream worker re-sends
it as the receiver recovers. The queue implementation depends on the persistence backend:

- **Mongo provider** — pending events are stored in the `pendingPushes` collection in the same Mongo deployment that holds SCIM resources. Survives restarts and is shared across replicas.
- **Memory provider** — pending events are stored as one `.set` file per token under `<scim.prov.memory.dir>/events/{pending,preregister}/<streamId>/<jti>.set`. Each file is a single-line JSON metadata header, a `\n` separator, then the encoded JWT payload. Writes go through tmp+rename, so a crash never leaves a partial `.set` visible to readers.

> **Restart-loss note (memory provider):** the memory backend persists pending push events under `<scim.prov.memory.dir>/events/`, but **SCIM resource state itself** (under `<scim.prov.memory.dir>/scimdata.json` and rotating backups) is in-memory and only flushed on shutdown / interval. After a hard crash, recent SCIM resource changes may be lost while the matching outbound SET payloads remain in the queue. For deployments that need durable resource state, use the Mongo provider.

#### Poll-side ack durability (PRD-B)

When the receiver applies an inbound SET locally, the SET's JTI is recorded to a durable ack queue before the producer-side acknowledgement is delivered to the SSF transmitter. This means a restart between "event applied locally" and "ack delivered to remote" replays the ack on the next poll rather than allowing the remote to re-deliver an event we have already processed.

- **Mongo provider** — acks are stored in the `pendingAcks` collection (per-stream index on `streamId`); the `_id` is `<streamId>:<jti>` so repeated enqueues are upserts. Rows are deleted only after the transmitter responds 2xx to the next poll/ack call.
- **Memory provider** — acks are stored as one `.ack` file under `<scim.prov.memory.dir>/events/acks/<streamId>/<jti>.ack` (URL-encoded). Each file is a single-line JSON `{"v":1,"streamId":...,"jti":...,"appliedAt":...}`. Writes are atomic tmp+rename, so a crash never exposes a partial `.ack`.

The shutdown sequence calls `pollEvents(ackOnly=true, retries=0)` once if the durable store has any pending acks; transient remote unavailability during shutdown does NOT flip stream state (PRD-A user story 22). Whatever does not deliver during shutdown is re-attempted on the next start, again from the durable store.
