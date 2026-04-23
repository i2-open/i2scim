#!/bin/bash
set -e
echo "sleeping for 15 seconds"
sleep 15
echo mongo_init.sh time now: `date +"%T" `

# Initialize TLS args if certs are present
TLS_ARGS=""
if [ -f /certs/mongo.pem ]; then
  echo "Using TLS for mongosh"
  TLS_ARGS="--tls --tlsAllowInvalidHostnames --tlsCAFile /certs/ca.pem --tlsCertificateKeyFile /certs/mongo.pem"
else
  echo "NOT using TLS for mongosh (certs missing)"
fi

# Initialize replica set
# Use a retry loop because mongod might still be starting up after the key was created
echo "Connecting to mongo1:30001..."
until mongosh $TLS_ARGS -u root -p dockTest --host mongo1:30001 --quiet --eval "db.adminCommand('ping')" > /dev/null 2>&1; do
  echo "Waiting for mongo1:30001 to be ready..."
  sleep 2
done

mongosh $TLS_ARGS -u root -p dockTest --host mongo1:30001 <<EOF
  var cfg = {
    "_id": "dbrs",
    "version": 1,
    "members": [
      {
        "_id": 0,
        "host": "mongo1:30001"
      },
      {
        "_id": 1,
        "host": "mongo2:30002"
      },
      {
        "_id": 2,
        "host": "mongo3:30003"
      }
    ]
  };
  rs.initiate(cfg,{ force: true });
  
  // Wait for primary to be elected
  while (!rs.isMaster().ismaster) {
    print("Waiting for primary...");
    sleep(2000);
  }

  // Create SPIFFE user for goSignals workload (always good to have if SPIFFE is later enabled)
  // Note: This matches the Subject computed in mongo_spiffe_init.sh for workload/gosignals-node
  db.getSiblingDB("\$external").runCommand(
    {
      createUser: "CN=spiffe://cluster.i2gosignals.internal/workload/gosignals-node",
      roles: [
           { role: "readWrite", db: "goSignals1" },
           { role: "readWrite", db: "goSignals2" },
           { role: "dbAdmin", db: "goSignals1" },
           { role: "dbAdmin", db: "goSignals2" }
      ],
      writeConcern: { w: "majority" }
    }
  );
  
  rs.status();
EOF