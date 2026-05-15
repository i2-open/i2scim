# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

i2scim is a Quarkus-based implementation of the IETF SCIM v2 protocol (RFC 7643/7644). The defining design choice is that **resource types and schema are not hard-coded** — they are loaded at startup from JSON files (`scimSchema.json`, `resourceTypes.json`, `acis.json`) supplied via path, classpath, or K8s ConfigMap. Most code operates on a generic JSON-document model rather than typed resources.

Java 25 / Quarkus 3.34.3 / Jakarta EE 11. Group: `com.independentid`, version: `0.10.0`.

## Build

Three modules, plain Maven from the root:

```bash
mvn install                          # build everything (skips tests by default — see surefire config)
mvn install -DskipTests=false        # build + run tests (requires MongoDB on localhost:27017)
./build.sh -b                        # equivalent to `mvn install` (kept for muscle-memory)
./build.sh -t                        # build + tests
./build.sh --tag <ver>               # build + multi-arch Docker image (load locally)
./build.sh -p --tag <ver>            # build + push to docker.io/independentid
```

Module dependency order: `i2scim-core` → `i2scim-client` → `i2scim-server`. Maven resolves this from the reactor; the explicit `-pl` ordering of the pre-collapse era is no longer required.

The publishable artifacts are `i2scim-core` and `i2scim-client`. `i2scim-server` is the runtime app and sets `<maven.deploy.skip>true</maven.deploy.skip>` to make `mvn deploy` safe (see `docs/publishing.md` for the dormant Maven Central restoration recipe).

## Testing

Unit and integration tests live in `i2scim-server/src/test/java/com/independentid/scim/test/` (organized by area: `memory/`, `mongo/`, `auth/`, `client/`, `http/`, `opa/`, `password/`, `devops/`, `misc/`, `set/`, `ssf/`, `sub/`, `events/`).

```bash
# Whole module:
mvn -pl i2scim-server test

# Single test class or method:
mvn -pl i2scim-server test -Dtest=MemoryProviderTest
mvn -pl i2scim-server test -Dtest=MemoryProviderTest#testCreateUser
```

Test prerequisites:
- **MongoDB** on `localhost:27017` with admin user `admin`/`t0p-Secret`. Override via `TEST_MONGO_URI`, `TEST_MONGO_USER`, `TEST_MONGO_SECRET`. Some tests use Quarkus mongodb devservices and Testcontainers (`org.testcontainers:mongodb`) and need a running Docker daemon.
- **OPA** (optional, for `opa/*` tests) — start via `opa/run-opa.sh`; defaults to `http://localhost:8181/v1/data/i2scim`. Override via `TEST_OPA_URL`.
- **Surefire `argLine`** in the root POM is required: `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/javax.net.ssl=ALL-UNNAMED --enable-native-access=ALL-UNNAMED`. If you write your own runner, replicate these or SSL-context introspection in the signals code will throw `InaccessibleObjectException` on Java 25.
- **`LoadScimClusterTest` is excluded** from the standard build because it stalls trying to reach a non-existent K8s cluster (see DECISIONS.md, 2026-04-15). The exclusion lives in `i2scim-server/pom.xml` surefire config.

Test-specific configuration is supplied via `QuarkusTestProfile` implementations (e.g. `ScimMemoryTestProfile`) — these set `scim.prov.providerClass`, disable security/events, and remap schema paths to test classpath resources. Follow this pattern; don't put profile-specific values in `application.properties`. Test-only properties that *must* live in `application.properties` use the `%test.` prefix.

## Running locally

- **Dev mode** (memory or mongo, selected by `scim.prov.providerClass` in `application.properties`):
  `mvn -pl i2scim-server quarkus:dev` — endpoint at `http://localhost:8080/`. The `%test.quarkus.mongodb.devservices.enabled=true` profile auto-starts a Mongo container if you flip the provider class to `MongoProvider`.
- **Mongo cluster for SSF/Signals work**: `docker compose -f docker-compose-signals.yml up` (3-node Mongo replica set + 2 SCIM instances) or `docker-compose-mongo-cluster.yml`.

## Architecture

**i2scim-core** — Protocol engine and canonical SCIM schema definitions. Everything else depends on it.
- `protocol/` — request/response types (`RequestCtx`, `ScimResponse`, `ListResponse`), filter parsing (`Filter`, `LogicFilter`, `ValuePathFilter`, `AttributeFilter`), JSON Patch.
- `schema/` — `SchemaManager` loads JSON definitions and exposes attribute metadata; `Schema`, `Attribute`, `ResourceType`.
- `resource/` — generic `ScimResource` / `Value` model that everything serializes through.
- `op/` — operation objects (`CreateOp`, `GetOp`, `PatchOp`, `BulkOps`...) executed by a worker pool.
- `core/` — `ConfigMgr` (config singleton), `PoolManager` (worker threads).
- `backend/` — `IScimProvider` SPI; provider implementations live in `i2scim-server` (memory + mongo).
- `plugin/` — `IScimPlugin` SPI for pre/post-transaction hooks; `PluginHandler` dispatches.
- `events/` — `IEventHandler` SPI for async event publishing.
- `security/` — ACI parsing/evaluation (`AccessControl`, `AciSet`, `AccessManager`); built-in `ScimAuthMechanism`, `ScimBasicIdentityProvider`. Auth is per-request (no sessions/cookies).
- `pwd/` — PBKDF2 password handling (FIPS-aligned).
- `serializer/` — JSON in/out; SCIM uses lenient JSON ("Postel's Law") — undefined attributes are tolerated, not rejected.
- `resources/schema/` — canonical SCIM schema JSON (`scimSchema.json`, `scimCommonSchema.json`, `scimFixedSchema.json`, `resourceTypes.json`) that other modules consume from the classpath.

**i2scim-client** — Standalone client library for building/manipulating SCIM resources and calling a SCIM server. Used by tests and external consumers; depends on Apache HttpClient 5.x.

**i2scim-server** — The Quarkus runtime app. `ScimV2Servlet` (`@WebServlet("/*")`) is the HTTP entry point and dispatches to operation classes from `i2scim-core/op`. The servlet uses `@Inject` to wire `ConfigMgr` and `PoolManager` — Quarkus ArC handles the lifecycle. Packages of note:
- `backend/memory/` — `MemoryProvider`: in-memory index + JSON-on-disk persistence with periodic backup snapshots (`scim.prov.memory.maxbackups`, `scim.prov.memory.backup.mins`).
- `backend/mongo/` — `MongoProvider`: SCIM JSON ↔ BSON. `MongoFilterMapper` translates SCIM filters to Mongo query documents. `MongoIdGenerator` issues SCIM `id` values.
- `signals/`, `set/`, `ssf/` — SCIM Events / Shared Signals Framework (SSF) draft-02 implementation: signal publishing/polling, Security Event Tokens, token issuance/validation. Uses `CopyOnWriteArrayList` in `SignalsEventHandler` to avoid `ConcurrentModificationException` during poll (DECISIONS.md, 2026-04-15).
- `filter/` — servlet auth filters; `security/` — OPA integration; `devops/` — health/liveness checks.
- `src/main/docker/` — `Dockerfile.jvm` for the published `independentid/i2scim-universal` image (backend selection happens at runtime via `scim.prov.providerClass`).
- `k8s/` — example deployment manifests for memory- and mongo-backend deployments. See `i2scim-server/k8s/README.md`.

The pre-slice-6 modules (`i2scim-prov-memory`, `i2scim-prov-mongo`, `i2scim-signals`, `i2scim-universal`, `i2scim-tests`, and the two `pkg-*` packagings) were collapsed into `i2scim-server` on `2026-05-04`. See DECISIONS.md and PRD #52 for context.

## Conventions specific to this codebase

- **RESTEasy Reactive only** (`quarkus-rest-jackson`). Do not introduce `quarkus-resteasy` (Classic) — it triggers a Quarkus capability conflict (DECISIONS.md, 2026-04-15).
- **Apache HttpClient 5.4.x**, not 4.5. Tests use `throws Exception` to absorb HC5's checked exceptions.
- **Micrometer**, not SmallRye Metrics (the latter is removed in Quarkus 3.x).
- **Docker base is Chainguard JRE** (`cgr.dev/chainguard/jre:latest`, UID 65532). Chainguard's entrypoint is already `["java"]`, so the Dockerfile `CMD` must be **arguments only** (no leading `java`). If you switch to Temurin, you must add `java` back to the `CMD` — see comments in `i2scim-server/src/main/docker/Dockerfile.jvm`. Use `JAVA_TOOL_OPTIONS` (read by the JVM) rather than `JAVA_OPTS` (which exec-form CMD ignores).
- **POM ordering**: `<modelVersion>` must be the first child of `<project>` or submodule resolution breaks (DECISIONS.md, 2026-04-14).
- **Java 25 module access**: any test or tool that introspects SSL or core internals needs the `--add-opens` / `--enable-native-access` flags listed above.
- Polling retries are disabled on shutdown to avoid long delays during test cleanup — preserve this when touching `SignalsEventHandler` / `PollStream`.
- **Maven Central publishing is dormant** (`maven.deploy.skip=true` on `i2scim-server`; release plugins removed). To restore, follow `docs/publishing.md`.

## Configuration reference

`docs/Configuration.md` is authoritative for `scim.*` properties. The most-used ones:

| Property | Purpose |
| --- | --- |
| `scim.prov.providerClass` | Selects backend (`com.independentid.scim.backend.memory.MemoryProvider` or `com.independentid.scim.backend.mongo.MongoProvider` FQCN). |
| `scim.schema.path` / `scim.resourcetype.path` / `scim.coreSchema.path` | JSON config files, classpath or absolute path. |
| `scim.security.acis` | ACI JSON file. |
| `scim.security.enable` | Master switch for authn/authz; tests typically disable. |
| `scim.prov.mongo.uri` / `scim.prov.mongo.dbname` | Mongo connection. |
| `scim.signals.ssf.serverUrl` + `scim.signals.ssf.trust.certs.{path,value}` | SSF endpoint and trust roots (PEM, file or env value). |
| `scim.event.enable` | Enables async event publishing. |

DECISIONS.md is a living log of upgrade decisions — consult it before changing build/runtime infra (Java version, base image, RESTEasy variant, module structure, etc.).

## Claude Development work cycle

1. Each new idea starts with the skil /grill-me in plan mode to work to a common understanding
2. /to-prd to write a PRD and commit to Github
3. /to-issues to create Github issues (slices)
4. Create a new branch for the PRD. Each issue in the PRD will be part of a common PR
5. Implement each issue using /tdd skill and commit to Github.
6. Once all issues are implemented, complete QA cycle assessing whether all issues are complete in the PR
7. Request HITL QA approval
8. Merge PR

## Agent skills

### Issue tracker

GitHub Issues at `i2-open/i2scim` via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical Matt Pocock vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.