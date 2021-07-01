# i2scim.io

# Introducing **i2scim**

**i2scim** is an kubernetes deployable server implementation of the IETF SCIM specification for provisioning of 
Identities as a common Identity Directory.
**i2scim** is as a generalized SCIM engine that supports configured endpoints and schemas defined in json. 
**i2scim** parses
JSON schema specification (attributes, their syntax, mutability, etc) and resource types (which objects and their
endpoints) as JSON files and automatically
maps SCIM resources (e.g. Users) to a persistance provider (another service API, file, or database). **i2scim** may be
extended to support new schema and resources simply by defining the appropriate schema in JSON.

For more information on SCIM, See [What is SCIM](#What is SCIM)

Additional information:

* Quick Starts
    * Deploying i2scim using memory database(TBD).
    * [Deploying i2scim using MongoDb on K8S](pkg-i2scim-prov-mongodb/i2scim-mongo-k8s.md).
* General Documentation
    * [Configuration](Configuration.md) - i2scim Configuration Properties
    * [Access Control](AccessControl.md) - Access Control Configuration

## What can i2scim be used for?
i2scim is a kubernetes deployable service which can support the following scenarios:
* An extensible data store for customer/user accounts shared by one or more services in a kubernetes cluster.
* An account pre-provisioning endpoint for integration with enterprise provisioning connectors.
* An adaptable servlet that can be used to SCIM enable an application user API
* A load balancer for scaling to a common shared database (e.g. Mongo)
* An event engine that can be used to trigger services such as Kafka based messaging (e.g. replication) and other
  workflows.
  
## What is SCIM

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

How SCIM and XML are alike:
* Schema defines attributes, their syntax, mutability, optionaly, visibility, etc.
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

  
## i2scim Features

i2scim supports the following features:

* Dynamic schema support - i2scim can support schema files (as described in RFC7643) which can be loaded at boot 
  time. The
  server will automatically create endpoints and persistence mappings to support the provided json configuration.
* Full SCIM V2 (RFC7644) protocol support including JSON Patch. Bulk support is planned for a future release.
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
    * Built on the [Quarkus](https://quarkus.io) platform version 1.13.1 for smaller deployments with faster startup
      running in Docker containers.
      
## Other Notes

Inter-SCIM server replication services are not currently part of this project and are currently only supported as 
part of a database cluster. For fault-tolerant scaled systems use i2scim deployed 
with a [MongoDB cluster on K8S](pkg-i2scim-prov-mongodb/i2scim-mongo-k8s.md) along with an enterprise MongoDB 
deployment.

Multiple DC replication, and custom data source features are available on request for enterprise deployments.

## For More Information
Open Source i2scim is maintained by Independent Identity Incorporated on a best effort sponsored basis.
For more information, please email [info@independentid.com](mailto:pinfo@independentid.com).