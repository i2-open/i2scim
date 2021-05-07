# i2scim.io

This repository is private and all materials are Copyright of IndependentID.com

## What is SCIM?

![scim](docs/SCIM_B-and-W_792x270.jpg)

SCIM is an IETF specified protocol and schema designed to support simple cloud identity management over a RESTful HTTP
service. Note: the SCIM acronym stands for System for Cross-domain Identity Management.
See: [simplecloud.info](https://simplecloud.info). 

### Schema

In the JSON world schema can be a controversial topic. In the SCIM world because many service providers will
implement SCIM, there had to be a way to tell SCIM clients how to read and understand endpoints with slightly 
different set ups. For example, one server may have only Users and Groups (the most common), but other servers may
have other custom resource types like Devices, Applications, etc.

In a SCIM server, resources are collected in top-level containers known as *Resource Types*. Each resource (record) 
has a defined "schema" which lays out the attributes contained within the 
record, their characteristics and types (e.g. boolean). In addition to a main "schema", an individual resource may be 
extended with additional attributes known as an *extension schema*. This enables applications to attach local 
attributes, while preserving inter-operability with the standardized attributes such as defined in the User schema.

SCIM standardizes common attributes (aka claims) about Users and
Groups [RFC7643](https://tools.ietf.org/html/rfc7643). Users, Groups, and other objects are handled as JSON 
documents (aka resources). SCIM schema defines attribute types:
* String, 
* Integer, 
* Decimal, 
* Boolean, 
* Date, 
* Binary, 
* Reference, 
* Complex, and
* Multi-valued.

For each attribute defined, there are also a set of characteristics that define how to parse and how the attribute 
is used:
* What type is the attribute (see above)?
* Is the attribute required?
* Does that attribute have pre-defined canonical values?
* Is the attribute matched as case exact? (case-insensitive)
* Are values immutable?
* When is the attribute returned (never, on request, by default, etc)?
* Is the attribute unique (for example a username)?

### Core Attributes
SCIM defines the following core attributes that are present in every SCIM Resource:
* `id` - Every resource has a permanent (immutable) resource identifier. To access a resource, the URL is alwasy of 
  the form `https://<serverdns>/<ResourceType>/<id>` where:
    * `<serverdns>` is the domain name of the server
    * `<ResourceType>` is the type of resource such as `Users` or `Groups`
    * `<id>` the unique and permanent identifier for the resource.
* `schemas` - tells readers what attributes can be found in a JSON document and how to handle the attribute based on 
  type and characteristics.  Schemas is multi-valued where one value is the "core" schema defining the contents of 
  the main JSON document, and other values are "extension" schemas used to attach additional attributes (e.g. such 
  as application data) not found in the core or standardized schemas.
* `meta` - Contains metadata about the resource such as `version`, `lastModified`, `resourceType`, and `location`.
* `externalid` - an identifier used by an external client to uniquely reference the resource (optional).

An important aspect of SCIM is that all resource URIs a permenant - not subject to change. By using a globally unique 
identifier, external references (e.g. such as to Users within Groups) are not subject to link breakages. This provides SCIM services with 
a natural form of referential integrity.

### SCIM API / Protocol
SCIM Protocol [RFC7644](https://tools.ietf.org/html/rfc7644) is technically just a profile of HTTP 
[RFC7230-40](https://tools.ietf.org/html/rfc7230). In other words, SCIM is just a RESTful API that 
defines things like create, read/search, update and delete methods using HTTP methods. 

* CREATE: Is used to create an object in SCIM, the HTTP POST method is used where the body of the request is simply a 
JSON 
document. The server generates a unique ID and returns the accepted result.

* GET: Is used to retrieve a specific resource or search for resources using a filter.

* PUT: Is used to replace a resource with a new representation. Note: because some attributes may be immutable, or 
read-only, SCIM defines a flexible approach making it easy for a client to simply GET a resource, make a change, and 
PUT it back.

* PATCH: Is used to modify a SCIM Resource using a format similar to JSON Patch [RFC6902](https://tools.ietf.
org/html/rfc6902). While slightly more complex for clients than HTTP PUT, PATCH allows descreat attribute modification without having 
to transmit the whole resource. For example, modifying a Group with a 100K or millions of members becomes difficult 
to handle in Javascript, not to mention the overhead and security risk in transferring such large values.
  
* DELETE: Is used to delete a resource specified by the request URL.

In addition to the basic CRUD methods above, i2scim also supports the following HTTP features:

* HEAD: Like GET, HEAD allows a client to query the existence of a resource and get its most recent ETag value which 
allows a client to detect whether the resource has changed. 

* Conditional Requests [RFC7232](https://tools.ietf.org/html/rfc7232) place conditions on GET, HEAD, PUT, and PATCH 
requests. For example, only update a resource if it is unchanged since a certain date, or still has a specific Etag 
version value.
 
## Where is SCIM Used?
In some cases, SCIM is deployed as a
directory server such as with Microsoft Azure or Oracle Identity Cloust Service (IDCS). As a directory, many
applications can access a common stateful store for User profile information. Similarly SCIM 
servers often serve as the backing store for OAuth Authorization and OpenId Connect systems. 

SCIM is also used (as originally intended) as a provisioning service enabling a common open API to
create and update User accounts in cloud based applications and services (e.g. SFDC).

# Introducing **i2scim**

**i2scim** is an implementation of the SCIM Schema  and Protocol. **i2scim** is designed as a protocol engine, which is to say the server 
does not work off of a fixed schema and structure. **i2scim** parses JSON schema and resource type JSON files and automatically
maps SCIM resources to a provider (a persistence service or database). **i2scim** may be easily extended to support new
schema and resources simply by defining the appropriate schema in JSON.

For persistance, the server defines an Interface `IScimProvider` handler that can be implemented to support 
different providers, or services. The initial version supports a MongoDB provider and an in-memory provider that stores 
JSON documents to a persistance volume (disk).

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
* Support for HTTP HEAD method and HTTP Conditional [RFC7232] qualifiers.
* Kubernetes deployment using docker on Intel and ARM64 (e.g. Raspberry Pi).
* DevOps Health, Liveness and performance interceptor support ready (e.g. grafana)
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
      FIPS 140 compliance
    * Note: at this time, i2scim does not support a web (html) interface and does not have built in support for 
      session control (cookies) for browsers. Each HTTP request is individually authenticated and authorized.
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

Several features are available on request for enterprise deployments such as Kafka based replication and events.
