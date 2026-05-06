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

#### Operator re-enable after DISABLED (PRD-B)

A stream transitioned to `DISABLED` by the elapsed-time cap retains all pending JTIs in `pendingPushes` (Mongo) or `events/pending/<streamId>/` (memory). Re-enable is done through the existing PRD-A control surface — programmatically calling `pushStream.state.transitionTo(StreamStatus.ENABLED, null)` on the active `SsfHandler.getPushStream()`. The per-stream `PushRetryWorker` registers a transition listener that wakes the idle-sleeping worker thread on `DISABLED → ENABLED`, so the queue resumes draining (in `queuedAt` order) within milliseconds rather than after the next idle window. The first 2xx response is the operational marker that the stream is healthy again; if the receiver is still failing, the elapsed-time cap fires again and the stream is re-DISABLED. Pending JTIs are never deleted by a DISABLE — only by a successful 2xx delivery.

#### Pending-push durability (PRD-B)

When the server cannot deliver a SET to a downstream receiver, the producer
records the signed token to a durable queue and a per-stream worker re-sends
it as the receiver recovers. The queue implementation depends on the persistence backend:

- **Mongo provider** — pending events are stored in the `pendingPushes` collection in the same Mongo deployment that holds SCIM resources. Survives restarts and is shared across replicas.
- **Memory provider** — pending events are stored as one `.set` file per token under `<scim.prov.memory.dir>/events/{pending,preregister}/<streamId>/<jti>.set`. Each file is a single-line JSON metadata header, a `\n` separator, then the encoded JWT payload. Writes go through tmp+rename, so a crash never leaves a partial `.set` visible to readers.

> **Restart-loss note (memory provider):** the memory backend persists pending push events under `<scim.prov.memory.dir>/events/`, but **SCIM resource state itself** (under `<scim.prov.memory.dir>/scimdata.json` and rotating backups) is in-memory and only flushed on shutdown / interval. After a hard crash, recent SCIM resource changes may be lost while the matching outbound SET payloads remain in the queue. For deployments that need durable resource state, use the Mongo provider.
