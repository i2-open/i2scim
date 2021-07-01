
#i2scim.io

## Kubernetes Mongo Quickstart Deployment

This module is used to build the docker bundling necessary to run i2scim
on Kubernetes using Mongo Database.

Included are a number of K8S yaml files that can be modified to suit
your deployment. By default, all components are deployed in the scim-mongo K8S namespace.
Files of note:
1. `dbmongo-test-service.yaml` - This file sets up a Mongo database instance which the i2scim server. Note: this
should be applied to the K8S cluster after 1-i2scim-mongo-configs.yaml is applied.
will look for. Note that this test service does not set up a fault tolerant Mongo configuration. Look
  to your cloud provider or [MongoDB web site for information](https://www.mongodb.com/kubernetes).
2. `1-i2scim-mongo-configs.yaml` This file sets up the initial run configuration for i2scim. Customize this
file to suite your deployment requirements
      * Creates `scim-mongo` namespace.
      * Creates the MongoDB admin access secret (`mongo-db-cred`).
      * Creates the i2scim server administration credential (`i2scim-root`).
      * Creates the i2scim server configuration properties (`i2scim-mongo-config`).
3. `2-i2scim-mongo-pvset-yaml` - In the case where operational files like access logs need to be retained, this file
defines persistant volumes used to hold data in the `/scim` mount point. Note, i2scim with Mongo depends entirely
   on MongoDb to hold all data. An i2scim-mongo deployment is otherwise stateless.
4. `3-i2scim-config-schema.yaml` - Thie ConfigMap sets up the operational schema, and endpoint (resource types) 
   definitions for the server. See [below](i2scim-mongo-k8s.md#i2scim-config-schema-configmap).
5. `4-i2scim-mongo-set.yaml` - Deploys i2scim as a Stateful set using the cluster's load balancer.   

    

### i2scim-mongo-config ConfigMap Properties
This K8S ConfigMap sets up i2scim to run using the MongoProvider. If not using the `dbmongo-test-service.yaml`,
update the property 'scim.prov.mongo.uri' to point to a valid MongoDb accessible by the cluster.

In order to facilitate K8S change control, the access control (`acis.json`) and schema files (`resourceTypes.json` 
and `scimSchema.json`) are loaded into a Config map and are referenced at the "/config" endpoint.

i2scim uses the Quarkus Smallrye JWT Plugin. See [Quarkus JWT Guide](https://quarkus.io/guides/security-jwt) 
for more information. Any properties described can be added to the `i2scim-mongo-config` ConfigMap.

### i2scim-config-schema ConfigMap
This configmap (set by `3-i2scim-config-schema.yaml`) defines the access controls, resource types, and schema to
be used by the server. Configmap includes the following files:
* `acis.json` - The access controls to be used for the server as defined in [Access Control](../AccessControl.md).
* `resourceTypes.json` - Is the configuration defining the resource types in the server. The format of this file
corresponds to the format returned by the SCIM `/Resourcetypes` endpoint. See [RFC 7644 Section 4](https://datatracker.ietf.org/doc/html/rfc7644#section-4).
* `scimSchema.json` - A file containing the resource schemas used in the server. By default, the schemas included 
  are defined in [RFC7643 Section 4](https://datatracker.ietf.org/doc/html/rfc7643#section-4).
  
Note that i2scim engine is entirely schema driven. Any new schema or resource type can be configured by updating the 
ConfigMap. Once updated server nodes will now support the new schemas.