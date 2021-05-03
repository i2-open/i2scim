# i2scim.io

This repository is private and all materials are Copyright of IndependentID.com

## What is SCIM?

![scim](SCIM_B-and-W_792x270.jpg)

SCIM is an IETF specified protocol and schema designed to support simple cloud identity management over a RESTful HTTP
service. Note: though the acronym stands for System for Cross-domain Identity Management.
See [simplecloud.info](https://simplecloud.info). SCIM standardizes common attributes (aka claims) about Users and
Groups to make it easier to provision accounts and access between systems. In some cases, SCIM is deployed as a
directory server such as with Microsoft Azure or Oracle IDCS. SCIM servers often serve as the backing store for OAuth
Authorization and OpenId Connect systems.

## Introducing i2scim

**i2scim** is an implementation of the SCIM Schema [RFC7643](https://tools.ietf.org/html/rfc7643) and Protocol
[RFC7644](https://tools.ietf.org/html/rfc7644). i2scim is designed as a protocol engine, which is to say the server 
does not work off of a fixed schema and structure. i2scim parses JSON schema and resource type JSON files and automatically
maps SCIM resources to a provider (a persistence service or database). i2scim may be easily extended to support new
schema and resources simply by defining the appropriate schema in JSON.

For storage, the server defines an Interface `IScimProvider` handler that can be implemented to support 
different
providers, or services. The initial version supports MongoDB and an in-memory provider that stores documents to a
persistance volume (disk).

Additional information:

* Quick Starts
    * Deploying i2scim-memory and
    * Deploying i2scim-mongo.
* [Configuration](Configuration.md) - i2scim Configuration Properites
* [Access Control](AccessControl.md) - Access Control Configuration

## What can i2scim be used for?
i2scim is a kubernetes enabled service which can support the following scenarios:
* An extensible data store for customer/user accounts shared by one or more services in a kubernetes cluster. 
* An account pre-provisioning endpoint for integration with enterprise provisioning connectors.
* An adaptable servlet that can be used to SCIM enable an application user API
* A load balancer for scaling to a common shared database (e.g. Mongo)
* An event engine that can be used to trigger services such as Kafka based messaging (e.g. replication) and other 
  workflows.
  
## Features

i2scim supports the following features:

* Dynamic schema support - i2scim can support schema files (as decribed in RFC7643) which can beloaded at boot time. The
  server will automatically create endpoints and persistence mappings to support the provided json configuration.
* Full SCIMV2 (RFC7644) protocol support including JSON Patch. Bulk support is planned for a future release.
* Kubernetes deployment using docker on Intel and ARM64.
* DevOps Health, Liveness and performance interceptor support ready (e.g. grafana)
* Event system enables support for enhancements such as Apache Kafka and server-to-server multi-master replication (see
  other).
* Security features
    * Access control support - acis are defined in json format (as a configuration file) and are an evolved  
      version of many popular LDAP server ACI formats. i2scim acis are intended ot support the requirements defined in:
      [RFC2820](https://datatracker.ietf.org/doc/rfc2820/).
    * HTTP Authentication Mechanisms
        * [RFC7523](https://tools.ietf.org/html/rfc7523) JWT Bearer tokens - i2scim uses
          the [Quarkus SmallRye JWT](https://quarkus.io/guides/security-jwt) libraries for authentication.
        * [RFC7617](https://tools.ietf.org/html/rfc7617) HTTP Basic - i2scim supports HTTP basic authentication of users
          against Users stored in i2scim.
    * Secure password support using PBKDF2 (Password Basked Key Derivation Function 2) with salt and pepper hash for
      FIPS 140 compliance
* Other features:
    * i2scim may be adapted to front-end other databases and API through its provider interface.
    * Supports "virtual" attribute extensions enabling custom mapping and handling (e.g. password policy)
    * `IScimPlugin` interface enables pre and post transaction custom actions
    * `IEventHandler` interface enables deployment of asynchronous event handlers (e.g. for replication or security
      events)
    * Built on the [Quarkus](https://quarkus.io) platform version 1.13.1 for smaller deployables with faster startup
      running in Docker containers.

## Deployment Packages

i2scim currently has 2 deployment packages that enable fast deployment on a Kubernetes cluster. One package is
configured to run the Mongo DB and the other the in Memmory database.

### i2scim Mongo Provider

This packaging configures i2scim to run against a Mongo database cluster. Each i2scim node is stateless and depends on a
Mongo database platform for replication. See module [pkg-i2scim-prov-mongo](pkg-i2scim-prov-mongodb).

### i2scim Memory Provider

Designed mainly for testing purposes, i2scim with Memory Provider stores data in memory and flushes data to a persistant
volume on a periodic basis. The memory provider database is a JSON based representation of SCIM resources. See
module [pkg-i2scim-prov-memory](pkg-i2scim-prov-memory).

## Other Notes

Several features are available on request for enterprise deployments such as Kafka based replication.
