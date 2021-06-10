## Mongo Setup and Testing Notes

For testing a mongo server is normally setup on the local host.

Set up an account and enable access control. See [Mongodb: Enable Access Control](https://docs.mongodb.com/manual/tutorial/enable-authentication/).

If the username/password is to be changed, the appropriate settings in ScimMongoTestProfile must also be changed.

In Mongo admin:

`
db.createUser({user:"admin",pwd:"t0p-Secret",roles:[{role:"userAdminAnyDatabase",db:"admin"},"readWriteAnyDatabase",
"dbAdminAnyDatabase"]})
`

Deploying on MacOS: [Installing MongoDB Community Edition on macOS](https://docs.mongodb.com/manual/tutorial/install-mongodb-on-os-x/)