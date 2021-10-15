# Developer Notes

These notes are for those developers who wish to work with the i2scim project directly. An overview of maven modules 
is provided along with build and test instructions.

## Introduction
The i2scim project is built using the Quarkus.io platform. The project is broken into a series of maven modules for 
usage in 3 ways:
1. As SCIM Protocol microservice front-ending a database (MongoDB).
2. As a SCIM server with built-in memory index and disk based storage.
3. As a SCIM client library for building and manipulating SCIM resources on a service provider.

This project is a Quarkus based project that uses injection to instantiate services based on an Eclipse Microprofie run 
configuration which which supports configuration variables provided by environment, application properties or K8S 
ConfigMaps. See: https://github.com/smallrye.

The project modules consist of the following:
* `i2scim-core` - The core protocol engine modules including JSON to SCIM parser and serializers, SchemaManager, 
  Access Control, Event/Operation Processing, and Provider interfaces.
* `i2scim-client` - Client libraries for building SCIM resources and accessing a SCIMv2 service.
* `i2scim-prov-memory` - An implementation of the provider interface that stores entries on disk with a memory based 
  index.
* `i2scim-prov-mongo` - An implementation of the provider interface that translates from SCIM JSON to BSON and 
  manages resources as documents in Mongo Database.
* `i2scim-server` - This contains the main servlet code that is injected at run time and boots the server. Also 
  included are servlet filters for i2scim access control or [Open Policy Agent](https://www.openpolicyagent.org) integrated access control. 
* `i2scim-tests` - This module includes unit tests for the entire server. Tests a broken out into major functional 
  area. See the **Testing Configuration** section for more info on how to configure test Mongo and OPA services for 
  testing.

Finally there are two packaging modules that assemble combinations of the modules above into Dockers images for 
direct deployment in docker or as part of a Kubernets configuration.
* `pkg-i2scim-prov-memory` - Packaging, prooperties, and builds for running i2scim with the Memory Provider. 
  Includes example YAML files for K8S deployments.
* `pkg-i2scim-prov-mongodb` - Packaging, prooperties, and builds for running i2scim with the Mongo Provider.
  Includes example YAML files for K8S deployments.

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