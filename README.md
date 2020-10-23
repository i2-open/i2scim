# i2scim

This repository is private and all materials are Copyright of IndependentID.com

I2 SCIM Server

This project is an implementation of the SCIM Schema (RFC7643) and Protocol (RFC7644). The server automatically parses standard schema and resource type JSON files and maps it to database service. This means the server can be easily extended to support new schema and resources simply by defining
the appropriate schema in JSON.

For storage, the server uses a persistence (backend) handler that can be extended to different data providers. The initial version supports MongoDB because of its support for JSON/BSON document semantics.

Additional information:
* [Configuration](Configuration.md) - I2 Configuration Properites