## i2scim Using Memory Provider

This packaging of i2scim deploys using the memory provider. Active information is stored in-memory for fast updates 
and queries. The memory provider persists data to disk on a scheduled cycle determined by configuration.

Notice: This package is designed to be a demonstration for testing purposes of the implementation of SCIM protocol. 
This configuration does not include replication or multi-server support. A future enhancement will be made available 
separately to provide replication services based on Apache Kafka. For more information contact [info@independentid.
com](mailto:info@independentid.com).

### Kubernetes Deployments

To deploy i2scim in Kubernetes, 4 files are provided as templates as follows:

* `1-i2scim-memory-configs.yaml` Defines the basic environment settings for the i2scim server. 
  This includes defining secrets such as the root account for the server.
   
* `2-i2scim-memory-pvset.yaml` Defines the persistent volume sets and claims for the i2scim server. Modify this 
   file to create your kubernetes persistent volumes. These volumes will be used by i2sciim to persist scim data.
   
* `3-i2scim-config-schema.yaml` Defines the data model and access control the server will provide. i2scim works by
  automatically loading schema definitions and resource types at run time.  Modify these configmaps to change the data 
  model and access control for the server as follows:
  - `acis.json` The access control policy for the server.  See [Access Control](../AccessControl.md) for information 
    on the format of this file.
  - `resourceTypes.json` The ResourceTypes defined in this server. This file corresponds to 
    [RFC7643 Section 6](https://datatracker.ietf.org/doc/html/rfc7643#section-6) and [RFC7644 Section 4](https://datatracker.ietf.org/doc/html/rfc7644#section-4)
    defining configuration discovery.
  - `scimCommonSchema.json` This file defines the common attributes used in all resource types in the server. **This 
    file should not be changed as implementation is fixed.**
  - 