# i2scim

This repository is private and all materials are Copyright of IndependentID.com

This project is an implementation of the SCIM Schema (RFC7643) and Protocol (RFC7644). The server automatically parses standard schema and resource type JSON files and maps it to database service. This means the server can be easily extended to support new schema and resources simply by defining
the appropriate schema in JSON.

For storage, the server uses a persistence (backend) handler that can be extended to different data providers. The initial version supports MongoDB because of its support for JSON/BSON document semantics.

Additional information:
* [Configuration](Configuration.md) - i2scim Configuration Properites
* [Access Control](AccessControl.md) - Access Control Configuration

##Features

i2scim supports the following features:
* Dynamic schema support - i2scim can support schema files (as decribed in RFC7643) which can beloaded
at boot time. The server will automatically create endpoints and persistence mappings to support the provided json configuration.
* Full SCIMV2 (RFC7644) protocol support including JSON Patch. Bulk support is planned for a future release.
* Kubernetes deployment using docker on Intel and ARM64.
* DevOps Health, Liveness and performance interceptor support ready (e.g. grafana)
* Event system enables support for enhancements such as Apache Kafka and server-to-server multi-master replication (see other).
* Security features
    * Access control support - acis are defined in json format (as a configuration file) and are based on similar format to many popular LDAP server ACI formats.
    * HTTP Authentication Mechanisms
        * [RFC7523](https://tools.ietf.org/html/rfc7523) JWT Bearer tokens - i2scim uses the [Quarkus SmallRye JWT](https://quarkus.io/guides/security-jwt) libraries for authentication.
        * [RFC7617](https://tools.ietf.org/html/rfc7617) HTTP Basic - i2scim supports HTTP basic authentication of users against Users stored in i2scim.
    * Secure password support using PBKDF2 (Password Basked Key Derivation Function 2) with salt and pepper hash for FIPS 140 compliance
* Other features:
    * Can be adapted to front-end other databases and API through its provider interface.
    * Supports "virtual" attribute extensions enabling custom mapping and handling (e.g. password policy)
    * IScimPlugin interface enables pre and post transaction custom actions
    * IEventHandler enables deployment of asynchronous event handlers (e.g. for replication or security events)
    * Built on the [Quarkus](https://quarkus.io) platform
    
##Deployment Packages
###i2scim Mongo Provider
This packaging configures i2scim to run against a Mongo database cluster. Each i2scim node is stateless and depends on 
a Mongo database platform for replication. See module [pkg-i2scim-prov-mongo](pkg-i2scim-prov-mongodb).

###i2scim Memory Provider
Designed mainly for testing purposes, i2scim with Memory Provider stores data in memory and flushes data to a persistant volume
on a periodic basis. The memory provider database is a JSON based representation of SCIM resources. See module [pkg-i2scim-prov-memory](pkg-i2scim-prov-memory).
    
## Other Notes
Several features are available on request for enterprise deployments such as Kafka based replication.
