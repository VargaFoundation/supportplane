#!/bin/sh
set -e

BACKEND_URL="${BACKEND_URL:-http://backend:8081}"
FRONTEND_URL="${FRONTEND_URL:-http://frontend:80}"

PASS=0
FAIL=0
TESTS=""

pass() {
  PASS=$((PASS + 1))
  TESTS="${TESTS}\n  PASS: $1"
  echo "  PASS: $1"
}

fail() {
  FAIL=$((FAIL + 1))
  TESTS="${TESTS}\n  FAIL: $1"
  echo "  FAIL: $1"
}

check() {
  TEST_NAME="$1"
  shift
  RESPONSE=$(curl -s -o /tmp/resp -w "%{http_code}" "$@")
  BODY=$(cat /tmp/resp 2>/dev/null || echo "")
  if echo "$RESPONSE" | grep -qE "^2"; then
    pass "$TEST_NAME (HTTP $RESPONSE)"
    echo "$BODY"
    return 0
  else
    fail "$TEST_NAME (HTTP $RESPONSE)"
    echo "$BODY"
    return 1
  fi
}

echo "============================================"
echo "  SupportPlane E2E Tests"
echo "============================================"
echo ""

# 1. Health checks
echo "--- Health Checks ---"
check "Backend health" "${BACKEND_URL}/actuator/health"
check "Frontend reachable" "${FRONTEND_URL}/"

# 2. Register tenant
echo ""
echo "--- Register Tenant ---"
REGISTER_RESP=$(curl -s -X POST "${BACKEND_URL}/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"companyName":"E2E Test Corp","email":"admin@e2e.com","fullName":"E2E Admin","password":"password123"}')
echo "$REGISTER_RESP"

TENANT_ID=$(echo "$REGISTER_RESP" | sed -n 's/.*"tenantId":"\([^"]*\)".*/\1/p')
if [ -n "$TENANT_ID" ]; then
  pass "Register tenant (tenantId=$TENANT_ID)"
else
  fail "Register tenant"
fi

# 3. Login
echo ""
echo "--- Login ---"
LOGIN_RESP=$(curl -s -X POST "${BACKEND_URL}/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin@e2e.com\",\"password\":\"password123\",\"tenantId\":\"${TENANT_ID}\"}")
echo "$LOGIN_RESP"

TOKEN=$(echo "$LOGIN_RESP" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [ -n "$TOKEN" ]; then
  pass "Login (got JWT)"
else
  fail "Login"
fi

AUTH="Authorization: Bearer ${TOKEN}"

# 4. Dashboard
echo ""
echo "--- Dashboard ---"
check "Dashboard (authenticated)" "${BACKEND_URL}/api/v1/dashboard" -H "$AUTH"

# 5. Attach cluster
echo ""
echo "--- Attach Cluster ---"
ATTACH_RESP=$(curl -s -X POST "${BACKEND_URL}/api/v1/clusters/attach" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d '{"clusterId":"e2e-cluster-001","name":"E2E Cluster"}')
echo "$ATTACH_RESP"

OTP_CODE=$(echo "$ATTACH_RESP" | sed -n 's/.*"otpCode":"\([^"]*\)".*/\1/p')
CLUSTER_ID=$(echo "$ATTACH_RESP" | sed -n 's/.*"clusterId":\([0-9]*\).*/\1/p')
if [ -n "$OTP_CODE" ]; then
  pass "Attach cluster (OTP=$OTP_CODE)"
else
  fail "Attach cluster"
fi

# 6. List clusters
echo ""
echo "--- List Clusters ---"
check "List clusters" "${BACKEND_URL}/api/v1/clusters" -H "$AUTH"

# 7. Validate OTP
echo ""
echo "--- Validate OTP ---"
check "Validate OTP" -X POST "${BACKEND_URL}/api/v1/clusters/validate-otp" \
  -H "X-ODPSC-Cluster-ID: e2e-cluster-001" \
  -H "X-ODPSC-Attachment-OTP: ${OTP_CODE}"

# 8. Create ticket
echo ""
echo "--- Create Ticket ---"
TICKET_RESP=$(curl -s -X POST "${BACKEND_URL}/api/v1/tickets" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d '{"title":"E2E Test Ticket","description":"Created by E2E test","priority":"HIGH"}')
echo "$TICKET_RESP"

TICKET_ID=$(echo "$TICKET_RESP" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')
if [ -n "$TICKET_ID" ]; then
  pass "Create ticket (id=$TICKET_ID)"
else
  fail "Create ticket"
fi

# 9. List tickets
echo ""
echo "--- List Tickets ---"
check "List tickets" "${BACKEND_URL}/api/v1/tickets" -H "$AUTH"

# 10. Upload bundle
echo ""
echo "--- Upload Bundle ---"
echo "test-bundle-data" > /tmp/test-bundle.zip
check "Upload bundle" -X POST "${BACKEND_URL}/api/v1/bundles/upload" \
  -F "bundle=@/tmp/test-bundle.zip" \
  -H "X-ODPSC-Bundle-ID: e2e-bundle-001" \
  -H "X-ODPSC-Cluster-ID: e2e-cluster-001"

# 11. OpenAPI docs
echo ""
echo "--- OpenAPI Docs ---"
check "OpenAPI docs accessible" "${BACKEND_URL}/api-docs"

# Summary
echo ""
echo "============================================"
echo "  Results: ${PASS} passed, ${FAIL} failed"
echo "============================================"
printf "%b\n" "$TESTS"
echo ""

if [ "$FAIL" -gt 0 ]; then
  echo "E2E TESTS FAILED"
  exit 1
fi

echo "ALL E2E TESTS PASSED"
exit 0
