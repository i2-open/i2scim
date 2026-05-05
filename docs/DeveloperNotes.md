# Developer Notes

These notes are for those developers who wish to work with the i2scim project directly. An overview of maven modules
is provided along with build and test instructions.

## Introduction
The i2scim project is built using the Quarkus.io platform. As of release 0.10.0 the project is structured as three Maven modules supporting three usage patterns:
1. As a SCIM Protocol microservice front-ending a database (MongoDB).
2. As a SCIM server with a built-in memory index and disk-based storage.
3. As a SCIM client library for building and manipulating SCIM resources on a service provider.

This project uses CDI/ArC dependency injection to instantiate services based on a MicroProfile run-time configuration that supports environment variables, application properties, and K8s `ConfigMap` data.

The project modules:

* `i2scim-core` — The protocol engine: JSON↔SCIM parsers/serializers, `SchemaManager`, access-control evaluation, event/operation processing, the `IScimProvider` SPI, and the canonical SCIM schema JSON loaded by the server at startup. Published to Maven Central (when publishing is enabled).
* `i2scim-client` — Client library for building SCIM resources and calling a SCIMv2 service. Used by tests and by external consumers. Depends on Apache HttpClient 5.x. Published to Maven Central (when publishing is enabled).
* `i2scim-server` — The Quarkus runtime application. Contains the HTTP servlet, the memory and Mongo provider implementations, the SCIM Events / SSF (signals) implementation, the OPA integration, the DevOps/health endpoints, the integration test suite, the Docker build, and the example Kubernetes manifests. This is the module that becomes `independentid/i2scim-universal:<tag>`.

Backend selection is a runtime configuration choice (`scim.prov.providerClass`) rather than a packaging-time choice — the same JAR/image runs against either backend.

Releases prior to 0.10.0 split this code across ten modules (`i2scim-prov-memory`, `i2scim-prov-mongo`, `i2scim-signals`, `i2scim-tests`, `i2scim-universal`, plus two `pkg-*` packagings). See [DECISIONS.md](../DECISIONS.md), 2026-05-04 entry, for why those were collapsed.

## Testing Configuration

To run the provided tests, a Mongo Database (Community Edition) needs to be available. By default, the server should 
be available on localhost port 27017. The tests require a user account with enough access rights to create databases 
and manipulate data. By default, the tests will use username `admin` and password `t0p-Secret`.

For Open Policy Agent integration testing, install the [OPA agent](https://www.openpolicyagent.org/docs/latest/#running-opa). 
In the `/opa` project directory, you will find a shell script to start the OPA server along with a rego policy for 
the i2scim server. By default, the OPA tests will attempt to access the OPA agent using a URL of: 
`http://localhost:8181/v1/data/i2scim`.

To override these defaults, the following environment variables may be set:
* `TEST_OPA_URL` - The URL i2scim is to call for policy decisions. 
* `TEST_MONGO_URI` - The URL used to access a test instance of Mongo.
* `TEST_MONGO_USER` - The userid to be used when accessing Mongo database
* `TEST_MONGO_SECRET` - The password used with the userid specified.

### Using Quarkus with IntelliJ
i2scim was developed using Quarkus and IntelliJ. [Here is information](https://www.jetbrains.com/help/idea/quarkus.html#debug) on how to set up a 
Quarkus development environment under IntelliJ