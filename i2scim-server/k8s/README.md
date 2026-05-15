# Kubernetes Deployment for i2scim

This directory holds reference manifests for deploying i2scim on Kubernetes. The published image — `independentid/i2scim-universal:<tag>` — supports both the in-memory and MongoDB backends; backend selection is a runtime configuration choice, not a separate image.

Two starter layouts are provided:

| Path | Backend | Notes |
| :--- | :--- | :--- |
| [`memory/`](memory) | In-memory + on-disk persistence | Simplest. Single-replica. Good for demos, dev clusters, and small standalone deployments. No replication. |
| [`mongo/`](mongo) | MongoDB | Stateless SCIM pods backed by a Mongo cluster. Use for HA / scale-out. |

Both layouts deploy into their own namespace (`scim-mem` or `scim-mongo`) and load schema, ResourceTypes, and ACIs from a `ConfigMap`. None of the SCIM data model is hard-coded — to extend or restrict the API you edit the `ConfigMap`, not the image.

> **Update the image tag before applying.** The example deployment manifests still pin pre-collapse image references (`independentid/i2scim-mem:0.7.0-Alpha`, `independentid/i2scim-mongo:0.6.1`). Replace these with `independentid/i2scim-universal:<tag>` and add `scim.prov.providerClass` to the corresponding `ConfigMap` if it isn't already set.

## Memory backend

Files in [`memory/`](memory) (apply in numeric order):

1. `1-i2scim-memory-configs.yaml` — namespace `scim-mem`, root-account `Secret`, and `i2scim-mem-config` `ConfigMap` (run-time properties, including `mp.jwt.verify.publickey`).
2. Persistent volume claim — pick one based on environment:
    - `2-i2scim-memory-pvset.yaml` — NFS-style claim for bare-metal or generic clusters.
    - `2-i2scim-gke-memory-pvset.yaml` — Google Kubernetes Engine variant.
   Sized to hold the SCIM JSON snapshots + access logs. Adjust to your data volume.
3. `3-i2scim-config-schema.yaml` — `ConfigMap` containing the SCIM schema and access-control JSON loaded at startup:
    - `acis.json` — access-control policy. See [docs/AccessControl.md](../../docs/AccessControl.md).
    - `resourceTypes.json` — endpoints exposed by the server (RFC 7643 §6 / RFC 7644 §4).
    - `scimCommonSchema.json` — common attributes. **Do not modify**; the implementation is fixed against this file.
    - `scimSchema.json` — schema attribute definitions (User, Group, plus extensions).
4. `4-i2scim-memory-deploy.yml` — `Deployment` plus `Service` exposing port 8080.

Configure `scim.prov.providerClass=com.independentid.scim.backend.memory.MemoryProvider` in the `ConfigMap`. The memory provider periodically snapshots to disk; tune via `scim.prov.memory.maxbackups` and `scim.prov.memory.backup.mins`.

> **Demo only.** The memory backend has no replication or multi-server support. For HA, use the Mongo backend.

## Mongo backend

Files in [`mongo/`](mongo) (apply in numeric order):

1. `1-i2scim-mongo-configs.yaml` — namespace `scim-mongo`, MongoDB credential `Secret` (`mongo-db-cred`), root-account `Secret` (`i2scim-root`), and `i2scim-mongo-config` `ConfigMap` (run-time properties, including `scim.prov.mongo.uri`, `scim.prov.mongo.dbname`, and `scim.prov.mongo.indexes`).
2. Persistent volume claim — pick one based on environment:
    - `2-i2scim-mongo-pvset.yaml` — NFS-style claim for bare-metal or generic clusters.
    - `2-i2scim-gke-mongo-pvset.yaml` — Google Kubernetes Engine variant.
   The Mongo backend itself is stateless on the SCIM side; this volume is for access logs and the cached signing key (if any).
3. `3-i2scim-config-schema.yaml` — same schema/ACI `ConfigMap` shape as the memory variant.
4. `4-i2scim-mongo-set.yml` — `StatefulSet` plus `Service` exposing port 8080.
5. `dbmongo-test-service.yaml` — *non-production* single-node MongoDB for smoke testing only. Replace with your managed/clustered Mongo (Atlas, Bitnami operator, etc.) before going live.

Configure `scim.prov.providerClass=com.independentid.scim.backend.mongo.MongoProvider` in the `ConfigMap`. Set `scim.prov.mongo.uri` to your Mongo cluster's connection string.

For a fault-tolerant deployment, run an external MongoDB replica set or sharded cluster; i2scim itself can scale horizontally because the Mongo backend stores all state.

## Health and observability

The image exposes the standard Quarkus health endpoints (`/q/health/live`, `/q/health/ready`) and a Prometheus-format metrics endpoint at `/q/metrics` on port 8080 (configured via `quarkus.micrometer.export.prometheus.path`). The endpoint is anonymous (it sits under Quarkus's non-application root, outside the SCIM auth filter) so dev/cluster Prometheus instances can scrape it without credentials. Prometheus selects the 0.0.4 text format with `Accept: text/plain;version=0.0.4`; the default Accept negotiation returns OpenMetrics 1.0.0, which Prometheus also handles. Both deployment files set the standard `prometheus.io/*` annotations for scrape discovery — point the scrape path at `/q/metrics`.

## See also

- [Configuration properties](../../docs/Configuration.md)
- [Access control](../../docs/AccessControl.md) and [OPA integration](../../docs/OPA_AccessControl.md)
- [Signals / SSF](../../docs/Signals.md)