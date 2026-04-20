#!/usr/bin/env bash
#
# Pulsar dev bootstrap — runs once before the broker starts.
#
# Generates (idempotently) the material needed by the standalone broker to
# run with TLS + JWT auth in development:
#
#   /pulsar/keys/jwt-private.pem   RS256 private key (used by the SupportPlane
#                                  backend to sign tenant tokens — exported via
#                                  the shared volume so docker-compose can mount
#                                  it into the backend container)
#   /pulsar/keys/jwt-public.pem    RS256 public key (loaded by the broker via
#                                  tokenPublicKey)
#   /pulsar/keys/superuser.token   JWT signed for subject "supportplane-backend",
#                                  consumed by the backend container as
#                                  PULSAR_AUTH_TOKEN
#   /pulsar/certs/ca.crt           Self-signed CA used for TLS in dev
#   /pulsar/certs/server.{crt,key} Broker server cert signed by the CA
#
# In staging/prod these are replaced by cert-manager-provisioned certs and a
# real RS256 key pair stored in K8s Secrets — see ADR 0003.

set -euo pipefail

KEYS_DIR=/pulsar/keys
CERTS_DIR=/pulsar/certs
mkdir -p "$KEYS_DIR" "$CERTS_DIR"

PRIV_KEY="$KEYS_DIR/jwt-private.pem"
PUB_KEY="$KEYS_DIR/jwt-public.pem"
SUPERUSER_TOKEN="$KEYS_DIR/superuser.token"

CA_KEY="$CERTS_DIR/ca.key"
CA_CERT="$CERTS_DIR/ca.crt"
SERVER_KEY="$CERTS_DIR/server.key"
SERVER_CERT="$CERTS_DIR/server.crt"
SERVER_CSR="$CERTS_DIR/server.csr"

# ---------------------------------------------------------------- JWT keypair
if [[ ! -f "$PRIV_KEY" ]]; then
    echo "[pulsar-init] generating RS256 JWT keypair"
    /pulsar/bin/pulsar tokens create-key-pair \
        --output-private-key "$PRIV_KEY" \
        --output-public-key "$PUB_KEY"
else
    echo "[pulsar-init] JWT keypair already present, skipping"
fi

# ---------------------------------------------------------------- superuser token
if [[ ! -f "$SUPERUSER_TOKEN" ]]; then
    echo "[pulsar-init] minting superuser token (subject=supportplane-backend)"
    /pulsar/bin/pulsar tokens create \
        --private-key "file://$PRIV_KEY" \
        --subject supportplane-backend \
        > "$SUPERUSER_TOKEN"
else
    echo "[pulsar-init] superuser token already present, skipping"
fi

# ---------------------------------------------------------------- self-signed CA + server cert
if [[ ! -f "$CA_CERT" ]]; then
    echo "[pulsar-init] generating self-signed CA + broker server cert"
    openssl req -new -x509 -nodes -days 3650 \
        -subj "/CN=SupportPlane Dev CA" \
        -newkey rsa:2048 \
        -keyout "$CA_KEY" -out "$CA_CERT" >/dev/null 2>&1

    openssl req -new -nodes \
        -subj "/CN=pulsar" \
        -newkey rsa:2048 \
        -keyout "$SERVER_KEY" -out "$SERVER_CSR" >/dev/null 2>&1

    cat > "$CERTS_DIR/server.ext" <<EOF
subjectAltName = DNS:pulsar,DNS:localhost,DNS:supportplane-pulsar,IP:127.0.0.1
EOF

    openssl x509 -req -in "$SERVER_CSR" \
        -CA "$CA_CERT" -CAkey "$CA_KEY" -CAcreateserial \
        -days 3650 -extfile "$CERTS_DIR/server.ext" \
        -out "$SERVER_CERT" >/dev/null 2>&1

    rm -f "$SERVER_CSR" "$CERTS_DIR/server.ext" "$CERTS_DIR/ca.srl"
else
    echo "[pulsar-init] TLS material already present, skipping"
fi

chmod 644 "$PUB_KEY" "$CA_CERT" "$SERVER_CERT" "$SUPERUSER_TOKEN"
chmod 600 "$PRIV_KEY" "$CA_KEY" "$SERVER_KEY"

echo "[pulsar-init] dev material ready under $KEYS_DIR and $CERTS_DIR"
echo "[pulsar-init] superuser token (first 24 chars): $(head -c 24 "$SUPERUSER_TOKEN")..."
