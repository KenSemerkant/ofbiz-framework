#!/usr/bin/env bash

# CG-3: SPA catalog evidence — performs exactly the OIDC (authorization
# code + PKCE) login the SPA performs (demo-user / demo-password, CUSTOMER
# through the party projection -> JWT roles chain), then browses the
# catalog and resolves a price through the gateway against product-svc.
# Also proves the route is role-gated: a machine token (no roles) gets
# 403. Non-zero exit on failure.
set -euo pipefail
NS="ofbiz"
VERIFIER=$(openssl rand -hex 32)
CHALLENGE=$(printf '%s' "${VERIFIER}" | openssl dgst -sha256 -binary \
  | openssl base64 | tr '+/' '-_' | tr -d '=')
AT=$(date -u +%Y-%m-%dT%H%%3A%M%%3A%S.000Z)

FLOW=$(mktemp)
cat > "${FLOW}" <<EOF
#!/bin/sh
set -e
IDP=http://identity-svc:8080
GW=http://gateway:8080
VERIFIER=$VERIFIER
CHALLENGE=$CHALLENGE
AT="$AT"

# 1. authorize -> login page (session cookie)
# (redirect_uri is the client's registered one; the code is taken from the
#  302 Location without following it — same code the SPA receives. Accept:
# text/html is what a browser sends — without it SAS answers Bearer 401)
curl -s -c /tmp/c.txt -H 'Accept: text/html' -o /dev/null "\${IDP}/oauth2/authorize?response_type=code&client_id=gateway&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2Flogin%2Foauth2%2Fcode%2Fgateway&scope=openid&code_challenge=\${CHALLENGE}&code_challenge_method=S256"
CSRF=\$(curl -s -b /tmp/c.txt -c /tmp/c.txt -H 'Accept: text/html' \${IDP}/login | grep -o 'name="_csrf"[^>]*value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')

# 2. login (demo-user / demo-password) -> back through authorize -> code
LOGIN_OUT=\$(curl -s -b /tmp/c.txt -c /tmp/c.txt -H 'Accept: text/html' -o /dev/null \
  -w '%{http_code} %{redirect_url}' \
  -X POST \${IDP}/login -d "username=demo-user&password=demo-password&_csrf=\${CSRF}")
REDIR=\$(echo "\${LOGIN_OUT}" | cut -d' ' -f2-)
# login posts back to authorize; the code is one redirect further. The
# Location omits the service port (identity sees itself on :80) — restore
# it before following, or the mesh answers 503.
REDIR=\$(echo "\${REDIR}" | sed 's|http://identity-svc/|http://identity-svc:8080/|')
case "\${REDIR}" in
  *code=*) ;;
  "") echo "FAIL: login rejected (\${LOGIN_OUT}; csrf-len \${#CSRF})"; exit 1 ;;
  *) REDIR=\$(curl -s -b /tmp/c.txt -c /tmp/c.txt -H 'Accept: text/html' -o /dev/null -w '%{redirect_url}' "\${REDIR}") ;;
esac
CODE=\$(echo "\${REDIR}" | sed -n 's/.*code=\([^&]*\).*/\1/p')
[ -n "\${CODE}" ] || { echo "FAIL: no auth code (redirect: \${REDIR})"; exit 1; }
echo "oidc login: code obtained"

# 3. token exchange (public client + PKCE verifier)
TOKEN_RESP=\$(curl -s -u gateway:dev-local-secret -X POST \${IDP}/oauth2/token \
  -d "grant_type=authorization_code&code=\${CODE}&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2Flogin%2Foauth2%2Fcode%2Fgateway&client_id=gateway&code_verifier=\${VERIFIER}")
TOKEN=\$(echo "\${TOKEN_RESP}" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "\${TOKEN}" ] || { echo "FAIL: no access token (\$(echo \${TOKEN_RESP} | head -c 200))"; exit 1; }
echo "oidc login: token issued (roles chain via party projection)"

# 4. catalog browse through the gateway (the SPA's GET /api/v1/products)
CATALOG=\$(curl -s -H "Authorization: Bearer \${TOKEN}" "\${GW}/api/v1/products?page=0&size=3")
echo "\${CATALOG}" | grep -q '"totalElements"' || { echo "FAIL: catalog browse: \$(echo \${CATALOG} | head -c 200)"; exit 1; }
TOTAL=\$(echo "\${CATALOG}" | sed -n 's/.*"totalElements":\([0-9]*\).*/\1/p')
FIRST=\$(echo "\${CATALOG}" | sed -n 's/.*"items":\[{"productId":"\([^"]*\)".*/\1/p')
echo "catalog browse: \${TOTAL} products via gateway (first: \${FIRST})"

# 5. price lookup through the gateway (the SPA's per-row lookup)
PRICE=\$(curl -s -H "Authorization: Bearer \${TOKEN}" "\${GW}/api/v1/products/\${FIRST}/price?currency=USD&at=\${AT}")
echo "\${PRICE}" | grep -q '"price"' || { echo "FAIL: price lookup: \$(echo \${PRICE} | head -c 200)"; exit 1; }
echo "price lookup: \${FIRST} -> \$(echo \${PRICE} | head -c 160)"

# 6. role gate: machine token (no roles) must be 403
MACHINE=\$(curl -s -u gateway:dev-local-secret -X POST \${IDP}/oauth2/token -d "grant_type=client_credentials" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
STATUS=\$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer \${MACHINE}" "\${GW}/api/v1/products?page=0&size=3")
[ "\${STATUS}" = "403" ] || { echo "FAIL: machine token got \${STATUS}, expected 403"; exit 1; }
echo "role gate: machine token 403 on /api/v1/products"
echo "SPA CATALOG EVIDENCE PASSED"
EOF

TOOLS="cg3-spa-tools"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 600 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done
trap 'kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1; rm -f "${FLOW}"' EXIT
kubectl -n "${NS}" cp "${FLOW}" "${TOOLS}:/tmp/flow.sh"
kubectl -n "${NS}" exec "${TOOLS}" -- sh /tmp/flow.sh
