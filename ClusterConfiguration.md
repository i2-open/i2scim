# Replication and Cluster Configuration
The i2scim server is designed to be deployed in a Kubernetes Stateful set cluster.
The server can be configured with each node as a replica capable of serving any request, or i2scim can be configured as
sharded cluster where each node holds a portion of resoruces.

Note: in the current release, partitioning is based solely on hashing rource identifiers. In the future, partioning may be
based on a combination of resource type and identifier (e.g. so Groups can be held in different partitions).

i2scim uses Apache Kafka for replication and message exchange between servers. There are several reasons for this:
* Kafka acts as a bus which makes the cost of scaling out N servers much lower than traditional master-client tree networks. No fanouts only simple connections.
* Kafka becomes the central repository of authority. New servers can be initialized by simply reading from specified start dates.
* Nodes can be reset to a particular point in time.
* Kafka can be used in both partitioned and replicated clusters
* Kafka mirror maker enables global replication
* Multi-master like functionality. Since the bus is the stateful authority, any client i2scim node can accept writes. **This may be limited in mirrormaker scenarios

## Deployment Scenarios:
### Replicated Cluster - Per Node Store
Each i2scim node accepts requests and submits them to the Kafka replication bus. Each node has its own Provider (Memory or Mongo) and stores data separately in its own persistent volume (PV) or database (e.g. Mongo).

While each node is functionally equal, this strategy can be limited as change rates increase because each node must process every
transaction in the system. By partitioning, the transaction rate can be cut in N assuming N partitions.

Settings:
* `scim.server.replicas` is the total number of replicas running (matches kubernetes statefulset)
* `scim.kafka.rep.client.id` is generated per node
* `scim.kafka.rep.cluster.id` is set with a common name for all nodes
* `scim.kafka.rep.sub.group.id` is set to match `scim.kafka.rep.client.id`
* `scim.kafka.rep.partitions` is set to 1

For example, a cluster of 6 kubernetes replicas = 1 kafka partition replicated across 6 kafka groupss each with independent data store

### Partitioned Cluster - Common Store
In a partitioned cluster, each data handles a subset of data based on a modulo hash calculation of resource identifiers (to spread load evenly). Mongo/Memory providers use
a shared database or persistent voluem to store data. This means that at any one time the shared data store holds a complete set of data while each i2scim node
performs operation on a sub-set of data to enable scale.

This mode depends on the data store itself handling its own replication, redundancy/fault tolerance. This approach makes the cost of
re-balancing shards/partitions very low since each i2scim node is fully "stateless" relative to the data store.

Settings:
* `scim.server.replicas` is the total number of replicas running (matches kubernetes statefulset)
* `scim.kafka.rep.client.id` is generated per node
* `scim.kafka.rep.cluster.id` is set with a common name for all nodes
* `scim.kafka.rep.sub.group.id` is set to match `scim.kafka.rep.client.id`
* `scim.kafka.rep.partitions` is set to to match `scim.server.replicas`

For example, 6 kubernetes replicas = 6 kafka partitioned servers sharing a common data store

### Partitioned Cluster - Partitioned Store
In a sharded cluster, each data handles a subset of data based on a modulo hash calculation of resource identifiers (to spread load evenly). Each node uses
its own database or persistent volume to store data. Individual nodes each hold a portion of the data. 

If there is only 1 node per partition, then redundancy is dependent upon the data store layer.

If 2 or more nodes are assigned for a partition, then the nodes will be maintained as replicas within the partition.
For example a cluster of 6 nodes may have 3 partitions each with 2 replicated members.

In this mode, rebalancing of partitions may be more difficult as nodes will have restructue stored data from other nodes.

Settings:
* `scim.server.replicas` is the total number of replicas running (matches kubernetes statefulset)
* `scim.kafka.rep.client.id` is generated per node
* `scim.kafka.rep.cluster.id` is set with a common name for all nodes
* `scim.kafka.rep.sub.group.id` is set to a unique name per data replica
* `scim.kafka.rep.partitions` is set to to `scim.server.replicas` / number of replicas

Note: `scim.server.replicas` = (number of `scim.kafka.rep.sub.group.id` values) * `scim.kafka.rep.partitiona`
For example:  6 kubernetes replicas = 2 kafka replicas * 3 partitions each

## Memory Provider / Kafka Notes
TBD

## Mongo Provider / Kafka Notes
TBD
