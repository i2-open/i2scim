#!/bin/bash
set -e

TRUST_DOMAIN="cluster.i2gosignals.internal"

# ---------------------------------------------------------------------------
# Cleanup — remove stale certs and init sentinel from any prior run so that
# mongod's startup loop waits for fresh SVIDs and the healthcheck is reset.
# ---------------------------------------------------------------------------
echo "Cleaning up old certs and sentinel..."
rm -f /certs/mongo.pem /certs/ca.pem /certs/*.pem /certs/*.key /certs/.init_complete

# ---------------------------------------------------------------------------
# Wait for SPIRE agent binary and socket
# ---------------------------------------------------------------------------
echo "Waiting for spire-agent binary..."
until [ -f /usr/local/bin/spire-agent ]; do sleep 2; done

echo "Waiting for spire-agent socket..."
until [ -S /run/spire/sockets/agent.sock ]; do sleep 2; done

# ---------------------------------------------------------------------------
# MongoDB replica key (shared across replica set members for keyFile auth)
# ---------------------------------------------------------------------------
echo "Setting up replica.key..."
if [ ! -f /data/config/replica.key ]; then
    mkdir -p /data/config
    openssl rand -base64 756 > /data/config/replica.key
    chmod 400 /data/config/replica.key
    chown 999:999 /data/config/replica.key
    echo "replica.key created"
fi

# ---------------------------------------------------------------------------
# Fetch initial SVID for the mongodb workload
# ---------------------------------------------------------------------------
echo "Fetching initial SPIFFE SVID for MongoDB workload..."
until /usr/local/bin/spire-agent api fetch x509 -write /certs/ -socketPath /run/spire/sockets/agent.sock; do
    echo "Waiting for SVID registration to complete..."
    sleep 5
done

# Prepare the combined PEM file mongod requires (cert + key)
# Search for the correct SVID among all fetched identities
SVID_PEM=""
for p in /certs/svid.*.pem; do
    [ -f "$p" ] || continue
    if openssl x509 -in "$p" -noout -ext subjectAltName | grep -q "URI:spiffe://$TRUST_DOMAIN/workload/mongodb"; then
        SVID_PEM="$p"
        break
    fi
done

if [ -z "$SVID_PEM" ]; then
    echo "ERROR: Could not find SVID for workload/mongodb in initial fetch."
    exit 1
fi

IDX=$(echo "$SVID_PEM" | sed 's/.*svid\.\([0-9]*\)\.pem/\1/')
SVID_KEY="/certs/svid.${IDX}.key"
SVID_BUNDLE="/certs/bundle.${IDX}.pem"

cp "$SVID_PEM" /certs/svid.current.pem
cp "$SVID_PEM" /certs/mongo.pem
cat "$SVID_KEY" >> /certs/mongo.pem
cp "$SVID_BUNDLE" /certs/ca.pem
chmod 644 /certs/mongo.pem /certs/ca.pem
chown 999:999 /certs/mongo.pem /certs/ca.pem
echo "Initial certificates fetched and prepared"

# ---------------------------------------------------------------------------
# Determine MongoDB $external usernames from SVID Subjects.
#
# MongoDB X.509 auth looks up the TLS peer certificate's Subject DN (RFC2253
# format) in the $external database.  Standard SPIFFE SVIDs have an empty
# Subject — identity lives in the URI SAN.  The SPIRE "uniqueid"
# CredentialComposer plugin adds OID 2.5.4.45 (x509UniqueIdentifier) to the
# Subject, with value = SHA256(spiffe_id)[0:16] hex-encoded (32 chars).
#
# Because the hash is deterministic we can compute the expected Subject for
# every known workload without needing to hold their actual certificates.
# We verify the formula against our own cert first; if it doesn't match we
# bail with a clear diagnostic so the operator knows what's wrong.
# ---------------------------------------------------------------------------

# Compute first 16 bytes (128 bits) of SHA256 of a SPIFFE ID URI as hex.
# Uses only tools present in mongo:latest (openssl, awk, cut).
spiffe_hash() {
    printf '%s' "$1" | openssl dgst -sha256 | awk '{print $NF}' | cut -c1-32
}

MONGODB_SPIFFE_ID="spiffe://${TRUST_DOMAIN}/workload/mongodb"
ACTUAL_SUBJECT=$(openssl x509 -in /certs/svid.current.pem -noout -subject -nameopt RFC2253 | sed 's/^subject=//')
echo "Actual SVID Subject (RFC2253): '${ACTUAL_SUBJECT}'"

if [ -z "$ACTUAL_SUBJECT" ]; then
    echo "ERROR: SVID Subject is empty."
    echo "  Ensure the 'uniqueid' CredentialComposer plugin is enabled in"
    echo "  config/spire/server/server.conf and the SPIRE server has been restarted."
    exit 1
fi

MONGODB_HASH=$(spiffe_hash "$MONGODB_SPIFFE_ID")
echo "Expected SHA256[0:16] of mongodb SPIFFE ID: '${MONGODB_HASH}'"

if ! echo "$ACTUAL_SUBJECT" | grep -qF "$MONGODB_HASH"; then
    echo "ERROR: Computed hash not found in Subject DN."
    echo "  Computed hash:  ${MONGODB_HASH}"
    echo "  Actual Subject: ${ACTUAL_SUBJECT}"
    echo "  The 'uniqueid' plugin may not be active, or the hash algorithm"
    echo "  assumption (SHA256, first 16 bytes, hex-encoded) is incorrect."
    exit 1
fi

# Extract the format prefix/suffix so we can derive other workload Subjects
# without holding their certificates.  Typically the Subject is simply
# "2.5.4.45=<32-char-hex>" or "x509UniqueIdentifier=<32-char-hex>".
SUBJ_PREFIX="${ACTUAL_SUBJECT%%${MONGODB_HASH}*}"
SUBJ_SUFFIX="${ACTUAL_SUBJECT#*${MONGODB_HASH}}"
echo "Subject format: prefix='${SUBJ_PREFIX}' suffix='${SUBJ_SUFFIX}'"

make_mongo_user() {
    local HASH
    local CN=$2
    HASH=$(spiffe_hash "spiffe://${TRUST_DOMAIN}/$1")
    if [ -n "$CN" ]; then
        # SVIDs registered with -dns names (via register_workload_with_dns) have
        # the first DNS name as a CN in the Subject DN.
        echo "${SUBJ_PREFIX}${HASH},CN=${CN}${SUBJ_SUFFIX}"
    else
        echo "${SUBJ_PREFIX}${HASH}${SUBJ_SUFFIX}"
    fi
}

GOSIGNALS1_USER=$(make_mongo_user "workload/gosignals-node" "goSignals1")
GOSIGNALS1B_USER=$(make_mongo_user "workload/gosignals-node" "goSignals1b")
GOSIGNALS2_USER=$(make_mongo_user "workload/gosignals-node" "goSignals2")
GOSSF_USER=$(make_mongo_user "workload/gossf-node" "goSsfServer")
SCIM_USER=$(make_mongo_user "workload/scim")
# Compute the goSignals Admin workload Subject DN
ADMIN_USER=$(make_mongo_user "workload/gosignals-admin")

# NOTE: the mongodb workload Subject is NOT added as a $external user.
# With clusterAuthMode sendKeyFile, replica nodes authenticate to each other
# via the shared keyFile — not via $external x.509 lookup.  The mongodb cert
# Subject matches the clusterAuthX509.attributes pattern, so MongoDB correctly
# refuses to create a $external user with that subject anyway.

echo "Computed MongoDB \$external users:"
echo "  gosignals-node1: ${GOSIGNALS1_USER}"
echo "  gosignals-node1b:${GOSIGNALS1B_USER}"
echo "  gosignals-node2: ${GOSIGNALS2_USER}"
echo "  gossf-node:      ${GOSSF_USER}"
echo "  scim:            ${SCIM_USER}"
echo "  gosignals-admin: ${ADMIN_USER}"

# ---------------------------------------------------------------------------
# Background certificate renewal loop (every 5 minutes)
#
# SPIRE rotates SVIDs at ~50% of their TTL (default: 30 min into a 1h SVID).
# Each iteration fetches the current SVID from the Workload API. When SPIRE
# has issued a new SVID, the fetched cert will differ from the previous one.
#
# After writing new cert files we call db.adminCommand({rotateCertificates:1})
# on each mongod node. That command re-reads net.tls.certificateKeyFile,
# net.tls.CAFile, and net.tls.CRLFile from disk without restarting mongod.
# ---------------------------------------------------------------------------
echo "Starting certificate renewal loop in background..."
(
set +e # Don't exit the loop if a command fails
while true; do
    sleep 300
    if [ ! -S /run/spire/sockets/agent.sock ]; then
        echo "ERROR: SPIRE agent socket missing. Waiting for agent recovery...: $(date)"
        continue
    fi

    if /usr/local/bin/spire-agent api fetch x509 -write /certs/ -socketPath /run/spire/sockets/agent.sock 2>/dev/null; then
        # 1. Identify the correct SVID for the mongodb workload
        SVID_PEM=""
        for p in /certs/svid.*.pem; do
            [ -f "$p" ] || continue
            if openssl x509 -in "$p" -noout -ext subjectAltName | grep -q "URI:spiffe://$TRUST_DOMAIN/workload/mongodb"; then
                SVID_PEM="$p"
                break
            fi
        done

        if [ -z "$SVID_PEM" ]; then
            echo "ERROR: Could not find SVID for workload/mongodb in fetched certificates: $(date)"
            continue
        fi

        IDX=$(echo "$SVID_PEM" | sed 's/.*svid\.\([0-9]*\)\.pem/\1/')
        SVID_KEY="/certs/svid.${IDX}.key"
        SVID_BUNDLE="/certs/bundle.${IDX}.pem"

        # 2. Check if the certificate has actually changed
        if ! cmp -s "$SVID_PEM" /certs/svid.current.pem 2>/dev/null; then
            echo "New SVID detected for workload/mongodb. Updating certificates on disk..."
            
            # Save the current cert/CA as "previous" for the rotation connection.
            # We must connect using a cert that the server's CURRENT in-memory CA trusts.
            cp /certs/mongo.pem /certs/mongo.pem.prev 2>/dev/null || true
            cp /certs/ca.pem    /certs/ca.pem.prev    2>/dev/null || true

            cp "$SVID_PEM" /certs/svid.current.pem
            cp "$SVID_PEM" /certs/mongo.pem
            cat "$SVID_KEY" >> /certs/mongo.pem
            cp "$SVID_BUNDLE" /certs/ca.pem
            chmod 644 /certs/mongo.pem /certs/ca.pem
            chown 999:999 /certs/mongo.pem /certs/ca.pem
            echo "Certificates updated on disk: $(date)"
        fi

        # 3. Hot-reload TLS material on each replica node.
        # We try rotation using both the "previous" and "current" certificates.
        # If rotation failed previously, the node might still be on an even older cert,
        # in which case it will eventually be restarted by Docker's healthcheck.
        for HOST in mongo1 mongo2 mongo3; do
            IDX_HOST=${HOST##mongo}
            PORT=$((30000 + IDX_HOST))
            
            # Rotation strategy: 
            # a) Try with .prev (usually what the server expects)
            # b) If fails, try with current (maybe it already rotated)
            # c) Both fail? Log and wait for next cycle or node restart.
            
            SUCCESS=false
            for TRY_CERT in "/certs/mongo.pem.prev" "/certs/mongo.pem"; do
                [ -f "$TRY_CERT" ] || continue
                
                if [ "$TRY_CERT" = "/certs/mongo.pem.prev" ]; then
                    TRY_CA="/certs/ca.pem.prev"
                else
                    TRY_CA="/certs/ca.pem"
                fi
                # Fallback to current CA if .prev CA is missing
                [ -f "$TRY_CA" ] || TRY_CA="/certs/ca.pem"

                if mongosh --host "$HOST" --port "$PORT" \
                        --username root --password dockTest \
                        --authenticationDatabase admin \
                        --tls --tlsAllowInvalidHostnames \
                        --tlsAllowInvalidCertificates \
                        --tlsCAFile "$TRY_CA" \
                        --tlsCertificateKeyFile "$TRY_CERT" \
                        --quiet \
                        --eval "db.adminCommand({rotateCertificates: 1, message: 'SPIRE SVID renewal'})" \
                        </dev/null 2>/dev/null; then
                    echo "  rotateCertificates succeeded on ${HOST}:${PORT} using ${TRY_CERT}: $(date)"
                    SUCCESS=true
                    break
                fi
            done
            
            if [ "$SUCCESS" = false ]; then
                echo "  ERROR: rotateCertificates failed on ${HOST}:${PORT} with all cert candidates: $(date)"
                echo "  Node may need restart if certificates are expired."
            fi
        done
    else
        echo "ERROR: Certificate fetch from SPIRE agent failed: $(date)"
    fi
done) &

# ---------------------------------------------------------------------------
# Wait for MongoDB nodes, then initialize replica set and users
# ---------------------------------------------------------------------------
CONN_STR="mongodb://root:dockTest@mongo1:30001,mongo2:30002,mongo3:30003/?replicaSet=dbrs&tls=true&tlsAllowInvalidHostnames=true&tlsCAFile=/certs/ca.pem&tlsCertificateKeyFile=/certs/mongo.pem&authSource=admin"

echo "Waiting for mongo1 on port 30001..."
until mongosh --host mongo1 --port 30001 --tls --tlsAllowInvalidHostnames \
        --tlsCAFile /certs/ca.pem --tlsCertificateKeyFile /certs/mongo.pem \
        --eval "db.adminCommand('ping')" > /dev/null 2>&1; do
    echo "  mongo1 not ready yet..."
    sleep 5
done
echo "mongo1 is up"

# Check / initialize replica set
IS_INITIATED=$(mongosh --host mongo1 --port 30001 --tls --tlsAllowInvalidHostnames \
    --tlsCAFile /certs/ca.pem --tlsCertificateKeyFile /certs/mongo.pem \
    --quiet --eval "try { rs.status().ok } catch(e) { 0 }")

if [ "$IS_INITIATED" = "1" ]; then
    echo "Replica set already initiated"
else
    echo "Initializing replica set..."
    INIT_CMD='var cfg = {
        "_id": "dbrs", "version": 1,
        "members": [
            { "_id": 0, "host": "mongo1:30001" },
            { "_id": 1, "host": "mongo2:30002" },
            { "_id": 2, "host": "mongo3:30003" }
        ]
    };
    rs.initiate(cfg, { force: true });'

    if ! mongosh --tls --tlsAllowInvalidHostnames --tlsCAFile /certs/ca.pem \
            --tlsCertificateKeyFile /certs/mongo.pem --host mongo1:30001 \
            --eval "$INIT_CMD" > /dev/null 2>&1; then
        echo "  Unauthenticated initiate failed, trying with root credentials..."
        mongosh --tls --tlsAllowInvalidHostnames --tlsCAFile /certs/ca.pem \
            --tlsCertificateKeyFile /certs/mongo.pem -u root -p dockTest \
            --authenticationDatabase admin --host mongo1:30001 \
            --eval "$INIT_CMD" || true
    fi

    echo "Waiting for primary election..."
    until mongosh "$CONN_STR" --quiet --eval "db.hello().isWritablePrimary" 2>/dev/null | grep -q "true"; do
        echo "  Waiting for primary..."
        sleep 5
    done
fi

# ---------------------------------------------------------------------------
# Create / update $external users for SPIFFE X.509 authentication
# ---------------------------------------------------------------------------
echo "Creating/updating \$external users..."

USER_SCRIPT="
const users = [
  {
    user: '${GOSIGNALS1_USER}',
    roles: [
      { role: 'readWrite', db: 'goSignals1' },
      { role: 'readWrite', db: 'goSignals2' },
      { role: 'dbAdmin',   db: 'goSignals1' },
      { role: 'dbAdmin',   db: 'goSignals2' }
    ]
  },
  {
    user: '${GOSIGNALS1B_USER}',
    roles: [
      { role: 'readWrite', db: 'goSignals1' },
      { role: 'readWrite', db: 'goSignals2' },
      { role: 'dbAdmin',   db: 'goSignals1' },
      { role: 'dbAdmin',   db: 'goSignals2' }
    ]
  },
  {
    user: '${GOSIGNALS2_USER}',
    roles: [
      { role: 'readWrite', db: 'goSignals1' },
      { role: 'readWrite', db: 'goSignals2' },
      { role: 'dbAdmin',   db: 'goSignals1' },
      { role: 'dbAdmin',   db: 'goSignals2' }
    ]
  },
  {
    user: '${GOSSF_USER}',
    roles: [
      { role: 'readWrite', db: 'SsfServer1' },
      { role: 'dbAdmin',   db: 'SsfServer1' }
    ]
  },
  {
    user: '${SCIM_USER}',
    roles: [
      { role: 'readWrite', db: 'goSignals1' },
      { role: 'readWrite', db: 'goSignals2' }
    ]
  },
  {
    user: '${ADMIN_USER}',
    roles: [
      { role: 'readWrite', db: 'gosignalsadmin' }
    ]
  }
];

users.forEach(u => {
  try {
    const existing = db.getSiblingDB('\\\$external').getUser(u.user);
    if (existing) {
      print('Updating user: ' + u.user);
      db.getSiblingDB('\\\$external').updateUser(u.user, { roles: u.roles });
    } else {
      print('Creating user: ' + u.user);
      db.getSiblingDB('\\\$external').createUser({ user: u.user, roles: u.roles });
    }
  } catch (e) {
    if (e.message && e.message.includes('already exists')) {
      db.getSiblingDB('\\\$external').updateUser(u.user, { roles: u.roles });
    } else {
      print('Error for user ' + u.user + ': ' + e);
      throw e;
    }
  }
});

print('User creation complete');
"

for i in $(seq 1 10); do
    if mongosh "$CONN_STR" --eval "$USER_SCRIPT"; then
        echo "Users processed successfully"
        break
    else
        echo "User creation attempt $i failed, retrying in 5s..."
        sleep 5
    fi
done

# ---------------------------------------------------------------------------
# Signal completion for the docker-compose healthcheck
# ---------------------------------------------------------------------------
touch /certs/.init_complete
echo "Initialization complete: $(date)"

# Keep alive for cert renewal
wait
