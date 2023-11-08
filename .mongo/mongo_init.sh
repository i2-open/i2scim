#!/bin/bash
echo "sleeping for 15 seconds"
sleep 15

echo mongo_init.sh time now: `date +"%T" `
mongosh -u root -p dockTest --host mongo1:30001 <<EOF
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
  rs.status();
EOF