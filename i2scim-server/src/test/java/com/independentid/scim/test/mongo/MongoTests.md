## Mongo Setup and Testing Notes

The Mongo provider tests (`MongoConfigTest`, `MongoProviderTest`, `MongoFilterMapTest`,
`RiscMongoPreImageTest`) run against an ephemeral MongoDB container — **no local Mongo
install is required**.

### How it works

These tests use the `ScimMongoTestProfile` profile. In the `test` profile,
`application.properties` enables [Quarkus Dev Services for MongoDB](https://quarkus.io/guides/mongodb#dev-services):

```
%test.quarkus.mongodb.devservices.enabled=true
%test.quarkus.mongodb.devservices.image-name=mongo:8.0
```

On test startup Quarkus pulls and starts the pinned `mongo:8.0` container, then exposes
its connection string as `quarkus.mongodb.connection-string`. Both
`scim.prov.mongo.uri` (in `application.properties`) and `TestUtils.DEF_TEST_MONGO_URI`
resolve to that string via `${quarkus.mongodb.connection-string:mongodb://localhost:27017}`,
so the tests connect to the container automatically. The container runs without access
control, and `TestUtils.resetMongoDb()` connects unauthenticated.

**The only prerequisite is a running Docker daemon.** This is what CI uses:
`.github/workflows/build-and-attest.yml` runs the entire `i2scim-server` test
suite on `ubuntu-latest` (which ships with Docker), so the Mongo tests — along
with everything else — run on every push and pull request.

### Running locally

```bash
# All Mongo provider tests:
mvn -pl i2scim-server test -Dtest='Mongo*,RiscMongoPreImageTest'

# A single class or method:
mvn -pl i2scim-server test -Dtest=MongoProviderTest
mvn -pl i2scim-server test -Dtest=MongoProviderTest#b_ScimAddUserTest
```

### Optional: running against a pre-existing Mongo

To point the tests at a Mongo you manage yourself instead of Dev Services, set
`quarkus.mongodb.connection-string` (e.g. via the `QUARKUS_MONGODB_CONNECTION_STRING`
environment variable). If that server enforces access control, also configure
`scim.prov.mongo.username` / `scim.prov.mongo.password` — `TestUtils.resetMongoDb()`
folds those credentials into the connection URI. To create the admin account, see
[MongoDB: Enable Access Control](https://www.mongodb.com/docs/manual/tutorial/enable-authentication/):

```
db.createUser({user:"admin",pwd:"t0p-Secret",roles:[{role:"userAdminAnyDatabase",db:"admin"},
"readWriteAnyDatabase","dbAdminAnyDatabase"]})
```
