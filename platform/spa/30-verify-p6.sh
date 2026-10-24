#!/usr/bin/env bash

# P6-GATE S3: SPA long-tail evidence — performs exactly the OIDC
# (authorization code + PKCE) login the SPA performs (demo-user /
# demo-password, CUSTOMER role), then browser-verifies CRUD on the five
# primary entities through the gateway (the exact requests the P6 SPA
# screens issue). Non-zero exit on failure.
set -euo pipefail
NS="ofbiz"
VERIFIER=$(openssl rand -hex 32)
CHALLENGE=$(printf '%s' "${VERIFIER}" | openssl dgst -sha256 -binary \
  | openssl base64 | tr '+/' '-_' | tr -d '=')
RUN="SPACG6$(date +%s)"

FLOW=$(mktemp)
cat > "${FLOW}" <<EOF
#!/bin/sh
set -e
IDP=http://identity-svc:8080
GW=http://gateway:8080
VERIFIER=$VERIFIER
CHALLENGE=$CHALLENGE
RUN=$RUN

# 1-2. authorize -> login (demo-user) -> code (same dance as the SPA)
curl -s -c /tmp/c.txt -H 'Accept: text/html' -o /dev/null "\${IDP}/oauth2/authorize?response_type=code&client_id=gateway&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2Flogin%2Foauth2%2Fcode%2Fgateway&scope=openid&code_challenge=\${CHALLENGE}&code_challenge_method=S256"
CSRF=\$(curl -s -b /tmp/c.txt -c /tmp/c.txt -H 'Accept: text/html' \${IDP}/login | grep -o 'name="_csrf"[^>]*value="[^"]*"' | head -1 | sed 's/.*value="//;s/"//')
LOGIN_OUT=\$(curl -s -b /tmp/c.txt -c /tmp/c.txt -H 'Accept: text/html' -o /dev/null \
  -w '%{http_code} %{redirect_url}' \
  -X POST \${IDP}/login -d "username=demo-user&password=demo-password&_csrf=\${CSRF}")
REDIR=\$(echo "\${LOGIN_OUT}" | cut -d' ' -f2-)
REDIR=\$(echo "\${REDIR}" | sed 's|http://identity-svc/|http://identity-svc:8080/|')
case "\${REDIR}" in
  *code=*) ;;
  *) REDIR=\$(curl -s -b /tmp/c.txt -c /tmp/c.txt -H 'Accept: text/html' -o /dev/null -w '%{redirect_url}' "\${REDIR}") ;;
esac
CODE=\$(echo "\${REDIR}" | sed -n 's/.*code=\([^&]*\).*/\1/p')
[ -n "\${CODE}" ] || { echo "FAIL: no auth code"; exit 1; }

# 3. token
TOKEN_RESP=\$(curl -s -u gateway:dev-local-secret -X POST \${IDP}/oauth2/token \
  -d "grant_type=authorization_code&code=\${CODE}&redirect_uri=http%3A%2F%2Flocalhost%3A4200%2Flogin%2Foauth2%2Fcode%2Fgateway&client_id=gateway&code_verifier=\${VERIFIER}")
TOKEN=\$(echo "\${TOKEN_RESP}" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -n "\${TOKEN}" ] || { echo "FAIL: no access token"; exit 1; }
echo "oidc login: token issued"

# CRUD through the gateway (the SPA's exact requests). create -> read ->
# update-or-delete on each primary entity.
CRUD() { # label create_path+body read_path update_method update_path update_body
  LABEL=\$1; CREATE=\$2; READ=\$3; UMETHOD=\$4; UPDATE=\$5; UBODY=\$6
  CPATH=\${CREATE%% *}; CBODY=\${CREATE#* }
  STATUS=\$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer \${TOKEN}" \
    -H 'Content-Type: application/json' -X POST "\${GW}\${CPATH}" -d "\${CBODY}")
  case "\${STATUS}" in 200|201) ;; *) echo "FAIL: \$LABEL create -> \$STATUS"; return 1;; esac
  R=\$(curl -s -H "Authorization: Bearer \${TOKEN}" "\${GW}\${READ}")
  [ -n "\$R" ] || { echo "FAIL: \$LABEL read empty"; return 1; }
  if [ -n "\${UMETHOD}" ]; then
    US=\$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer \${TOKEN}" \
      -H 'Content-Type: application/json' -X "\${UMETHOD}" "\${GW}\${UPDATE}" -d "\${UBODY}")
    case "\${US}" in 200|201|204) ;; *) echo "FAIL: \$LABEL update -> \$US"; return 1;; esac
  fi
  echo "  \$LABEL: create+read+\${UMETHOD:-read} OK"
}

FAIL=0
CRUD "bom" \
  "/api/v1/boms {\"productId\":\"\${RUN}\",\"components\":[{\"productId\":\"\${RUN}-C\",\"quantity\":2,\"scrapPct\":0}]}" \
  "/api/v1/boms/\${RUN}" \
  DELETE "/api/v1/boms/\${RUN}" "" || FAIL=1
CRUD "work-effort" \
  "/api/v1/workefforts {\"workEffortId\":\"\${RUN}\",\"workEffortTypeId\":\"TASK\",\"workEffortName\":\"spa drill\"}" \
  "/api/v1/workefforts/\${RUN}" \
  PUT "/api/v1/workefforts/\${RUN}" "{\"workEffortName\":\"spa drill renamed\"}" || FAIL=1
CRUD "position" \
  "/api/v1/positions {\"emplPositionId\":\"\${RUN}\",\"emplPositionTypeId\":\"ENGINEER\",\"statusId\":\"ACTIVE\"}" \
  "/api/v1/positions/\${RUN}" \
  PUT "/api/v1/positions/\${RUN}" "{\"emplPositionTypeId\":\"ENGINEER\",\"statusId\":\"INACTIVE\"}" || FAIL=1
CRUD "content" \
  "/api/v1/contents {\"contentId\":\"\${RUN}\",\"contentTypeId\":\"DOCUMENT\",\"contentName\":\"spa drill\"}" \
  "/api/v1/contents/\${RUN}" \
  DELETE "/api/v1/contents/\${RUN}" "" || FAIL=1
CRUD "campaign" \
  "/api/v1/campaigns {\"marketingCampaignId\":\"\${RUN}\",\"campaignName\":\"spa drill\",\"statusId\":\"ACTIVE\",\"fromDate\":\"2026-01-01\",\"thruDate\":\"2026-12-31\"}" \
  "/api/v1/campaigns/\${RUN}" \
  PUT "/api/v1/campaigns/\${RUN}" "{\"campaignName\":\"spa drill renamed\",\"statusId\":\"ACTIVE\",\"fromDate\":\"2026-01-01\",\"thruDate\":\"2026-12-31\"}" || FAIL=1

# role gate on a P6 route (same rule as the catalog)
MACHINE=\$(curl -s -u gateway:dev-local-secret -X POST \${IDP}/oauth2/token -d "grant_type=client_credentials" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
STATUS=\$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer \${MACHINE}" "\${GW}/api/v1/campaigns")
[ "\${STATUS}" = "403" ] && echo "role gate: machine token 403 on /api/v1/campaigns" \
  || { echo "FAIL: machine token got \${STATUS}, expected 403"; FAIL=1; }

[ "\${FAIL}" = "0" ] && echo "SPA P6 EVIDENCE PASSED" || exit 1
EOF

TOOLS="cg6-spa-tools"
kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1 || true
kubectl -n "${NS}" run "${TOOLS}" --image=curlimages/curl:8.11.1 --command -- sleep 600 >/dev/null
for i in $(seq 1 30); do kubectl -n "${NS}" exec "${TOOLS}" -- true >/dev/null 2>&1 && break; sleep 2; done
trap 'kubectl -n "${NS}" delete pod "${TOOLS}" --ignore-not-found --force --grace-period=0 >/dev/null 2>&1; rm -f "${FLOW}"' EXIT
kubectl -n "${NS}" cp "${FLOW}" "${TOOLS}:/tmp/flow.sh"
kubectl -n "${NS}" exec "${TOOLS}" -- sh /tmp/flow.sh
