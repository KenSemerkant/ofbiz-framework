# Ops Toolkit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One `./ops` bash dispatcher at repo root covering day-2 operations for the microservices stack (kind cluster) and the monolith (postgres-demo compose).

**Architecture:** Thin dispatcher delegating to existing scripts (`infra/dev/up.sh|down.sh`, `platform/services/10-deploy.sh|20-verify.sh|40-seed-demo.sh`); new code only for status/logs/restart/backup/restore/port-forward and the monolith compose wrapper (durable pgdata via ops-managed compose override file).

**Tech Stack:** Bash (`set -euo pipefail`), kubectl, docker compose. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-26-ops-toolkit-design.md`

**TDD deviation (explicit):** Repo convention for ops scripts is zero unit tests — verified by execution (all `platform/*.sh` work this way, Rule 11). Each task below instead runs concrete smoke checks with expected output before its commit. Bash tab-completion from the spec is dropped: a child process cannot register completions in the parent shell; sourcing `ops` would execute the dispatcher. Spec line amended in Task 5.

**Environment facts (verified 2026-10-26/27):** kind cluster `ofbiz` (context `kind-ofbiz`), namespace `ofbiz`, postgres pod `postgres-0` with local-socket trust (`psql -U admin` pattern in `10-deploy.sh`), service DBs/roles: party product inventory search orderdb billing finance manufacturing workeffort humanres content marketing. Gateway exposed via istio-ingressgateway NodePort 30080. Observability: `observability/jaeger-query:16686`, `observability/grafana:3000`, `ofbiz/mailhog:8025`. Monolith compose DBs: ofbizmaindb/ofbizolapdb/ofbiztenantdb (users ofbiz/ofbizolap/ofbiztenant, superuser `postgres`); upstream compose at `monolith/docker/examples/postgres-demo/docker-compose.yml` uses image `ofbiz-docker`, port 8443, `OFBIZ_DATA_LOAD: demo`, and has **no** pgdata volume.

---

### Task 1: Skeleton — vars, helpers, usage, dispatch

**Files:**
- Create: `ops` (repo root, executable)

- [ ] **Step 1: Write `ops` skeleton**

```bash
#!/usr/bin/env bash
#
# ops — unified operations & maintenance entrypoint.
#   micro: kind-cluster microservices stack (delegates to platform/* + infra/dev/*)
#   mono:  monolith via monolith/docker/examples/postgres-demo compose
# Spec: docs/superpowers/specs/2026-10-26-ops-toolkit-design.md
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

NS="ofbiz"
CTX="kind-ofbiz"
PG_POD="postgres-0"
DEPLOYS="identity-svc gateway audit-svc notification-svc search-svc party-svc product-svc inventory-svc order-svc billing-svc finance-svc manufacturing-svc workeffort-svc humanres-svc content-svc marketing-svc demo-svc"
SVC_DBS="party product inventory search orderdb billing finance manufacturing workeffort humanres content marketing"
MONO_DIR="${ROOT}/monolith/docker/examples/postgres-demo"
MONO_DBS="ofbizmaindb ofbizolapdb ofbiztenantdb"
FWD_PIDS="/tmp/ops-fwd.pids"

die() { echo "ERROR: $*" >&2; exit 1; }
ts()   { date +%Y%m%d-%H%M%S; }
kube() { kubectl --context "${CTX}" "$@"; }
mono() { docker compose --project-directory "${MONO_DIR}" "$@"; }

require_yes() { # $1=flag $2=what would happen
  if [ "${1:-}" != "--yes" ]; then
    echo "DRY-RUN (nothing done). Would: ${2}"
    echo "Re-run with --yes to execute."
    exit 1
  fi
}

check_deploy() {
  case " ${DEPLOYS} " in
    *" ${1:-} "*) ;;
    *) die "unknown deploy '${1:-}' — choose one of: ${DEPLOYS}" ;;
  esac
}

usage() {
  cat <<'EOF'
ops — unified operations toolkit

  ops micro up            full stack up (infra/dev/up.sh: terraform kind + ansible stack)
  ops micro down [--yes]  full teardown (infra/dev/down.sh: terraform destroy) — --yes required
  ops micro status        pods + deployments + gateway endpoint
  ops micro logs S        follow logs of deployment S
  ops micro restart S     rollout restart + status of deployment S
  ops micro deploy        rebuild + redeploy all services (platform/services/10-deploy.sh)
  ops micro verify        run platform/services/20-verify.sh
  ops micro seed          run platform/services/40-seed-demo.sh
  ops micro backup        pg_dump all service DBs -> backups/micro/<ts>/
  ops micro restore DIR [--yes]    restore dumps into existing DBs (expects fresh/empty cluster; duplicate keys abort) — --yes required
  ops micro fwd [stop]    port-forward jaeger(16686) grafana(3000) mailhog(8025)

  ops mono build          docker build -t ofbiz-docker monolith/
  ops mono start          compose up -d (durable pgdata via ops override)
  ops mono stop           compose down (STOP_ALL=1 wipes volumes too)
  ops mono status         compose ps + https://localhost:8443 check
  ops mono logs           follow ofbiz container logs
  ops mono psql           psql shell as ofbiz @ ofbizmaindb
  ops mono backup         pg_dump all mono DBs -> backups/mono/<ts>/
  ops mono restore FILE [--yes]   restore one dump file — --yes required
EOF
}

cmd_micro() {
  local action="${1:-}"; shift || true
  case "${action}" in
    up|down|status|logs|restart|deploy|verify|seed|backup|restore|fwd)
      die "micro ${action}: not implemented yet" ;;
    *) die "unknown micro action '${action}' (see: ops help)" ;;
  esac
}

cmd_mono() {
  local action="${1:-}"; shift || true
  case "${action}" in
    build|start|stop|status|logs|psql|backup|restore)
      die "mono ${action}: not implemented yet" ;;
    *) die "unknown mono action '${action}' (see: ops help)" ;;
  esac
}

case "${1:-help}" in
  micro) shift; cmd_micro "$@" ;;
  mono)  shift; cmd_mono "$@" ;;
  help|--help|-h) usage ;;
  *) usage >&2; exit 1 ;;
esac
```

- [ ] **Step 2: Make executable + syntax check**

Run: `chmod +x ops && bash -n ops`
Expected: no output, exit 0

- [ ] **Step 3: Help and dispatch checks**

Run: `./ops help | grep -c 'ops micro\|ops mono'`
Expected: `19` (11 micro + 8 mono action lines)

Run: `./ops bogus; echo "exit=$?"`
Expected: usage on stderr, `exit=1`

Run: `./ops micro bogus; echo "exit=$?"`
Expected: `ERROR: unknown micro action 'bogus' (see: ops help)`, `exit=1`

- [ ] **Step 4: Commit**

```bash
git add ops
git commit -m "Added: ops toolkit skeleton — dispatcher, help, env facts, confirm gates"
```

---

### Task 2: micro lifecycle + read-only actions

**Files:**
- Modify: `ops` — replace the `cmd_micro` stub body

- [ ] **Step 1: Replace `cmd_micro` with real dispatch**

```bash
cmd_micro() {
  local action="${1:-}"; shift || true
  case "${action}" in
    up)      sh infra/dev/up.sh ;;
    down)    require_yes "${1:-}" "terraform-destroy the dev cluster (sh infra/dev/down.sh)"
             sh infra/dev/down.sh ;;
    status)  kube get pods -A
             echo
             kube -n "${NS}" get deploy
             echo
             echo "gateway: http://localhost:30080 (istio NodePort) — SPA dev server on :4200 proxies it" ;;
    logs)    check_deploy "${1:-}"
             kube -n "${NS}" logs -f "deploy/${1}" ;;
    restart) check_deploy "${1:-}"
             kube -n "${NS}" rollout restart "deploy/${1}"
             kube -n "${NS}" rollout status "deploy/${1}" ;;
    deploy)  sh platform/services/10-deploy.sh ;;
    verify)  sh platform/services/20-verify.sh ;;
    seed)    sh platform/services/40-seed-demo.sh ;;
    backup)  micro_backup ;;
    restore) micro_restore "${1:-}" "${2:-}" ;;
    fwd)     micro_fwd "${1:-}" ;;
    *)       die "unknown micro action '${action}' (see: ops help)" ;;
  esac
}
```

(`micro_backup`, `micro_restore`, `micro_fwd` referenced here are implemented in Task 3; if executing tasks out of order, implement Task 3 first or expect "command not found" on those three actions only.)

- [ ] **Step 2: Add `micro_fwd` function (place above `cmd_micro`)**

```bash
micro_fwd() {
  if [ "${1:-}" = stop ]; then
    [ -f "${FWD_PIDS}" ] || die "no forwards running"
    xargs kill < "${FWD_PIDS}" 2>/dev/null || true
    rm -f "${FWD_PIDS}"
    echo "forwards stopped"
    return
  fi
  [ -z "${1:-}" ] || die "usage: ops micro fwd [stop]"
  if [ -f "${FWD_PIDS}" ]; then
    local pid
    for pid in $(cat "${FWD_PIDS}"); do
      kill -0 "${pid}" 2>/dev/null && die "forwards already running — ops micro fwd stop first"
    done
  fi
  : > "${FWD_PIDS}"
  # direct kubectl (not the kube() wrapper): backgrounding a function puts the
  # subshell PID in $!, leaving kubectl orphaned when killed
  kubectl --context "${CTX}" -n observability port-forward svc/jaeger-query 16686:16686 >/dev/null 2>&1 & echo $! >> "${FWD_PIDS}"
  kubectl --context "${CTX}" -n observability port-forward svc/grafana 3000:3000        >/dev/null 2>&1 & echo $! >> "${FWD_PIDS}"
  kubectl --context "${CTX}" -n "${NS}" port-forward svc/mailhog 8025:8025              >/dev/null 2>&1 & echo $! >> "${FWD_PIDS}"
  sleep 1
  local dead=""
  for pid in $(cat "${FWD_PIDS}"); do
    kill -0 "${pid}" 2>/dev/null || dead="${dead} ${pid}"
  done
  [ -z "${dead}" ] || die "port-forward failed (pid${dead}) — check kubectl/context; clean up with: ops micro fwd stop"
  echo "jaeger http://localhost:16686  grafana http://localhost:3000  mailhog http://localhost:8025"
  echo "stop with: ops micro fwd stop"
}
```

- [ ] **Step 3: Syntax check**

Run: `bash -n ops`
Expected: clean

- [ ] **Step 4: Live checks against the running cluster**

Run: `./ops micro status | tail -5`
Expected: gateway hint line + deploy table with 15 `-svc`/`gateway`/`demo-svc` rows

Run: `./ops micro logs bogus; echo "exit=$?"`
Expected: `ERROR: unknown deploy 'bogus' — choose one of: ...`, `exit=1`

Run: `./ops micro restart demo-svc`
Expected: `deployment.apps/demo-svc restarted` then `deployment "demo-svc" successfully rolled out`

Run: `./ops micro down`
Expected: `DRY-RUN (nothing done). Would: terraform-destroy the dev cluster (sh infra/dev/down.sh)` + exit 1 — **cluster must still exist after**: verify with `kind get clusters | grep ofbiz`

Run: `./ops micro fwd stop; ./ops micro fwd; sleep 2; curl -s -o /dev/null -w '%{http_code}\n' http://localhost:16686; ./ops micro fwd stop`
Expected: `forwards stopped` (or "no forwards running" first), then `200` from jaeger, then `forwards stopped`

- [ ] **Step 5: Commit**

```bash
git add ops
git commit -m "Added: ops micro lifecycle — up/down/status/logs/restart/deploy/verify/seed/fwd"
```

---

### Task 3: micro backup / restore

**Files:**
- Modify: `ops` — add two functions above `cmd_micro`

- [ ] **Step 1: Add functions**

```bash
micro_backup() {
  local dir="backups/micro/$(ts)" db
  mkdir -p "${dir}"
  trap 'rm -rf "${dir}"' ERR   # failed backup must not leave a valid-looking partial dir
  for db in ${SVC_DBS}; do
    echo "  -- ${db}"
    kube -n "${NS}" exec "${PG_POD}" -- pg_dump -U "${db}" "${db}" | gzip > "${dir}/${db}.sql.gz"
  done
  trap - ERR
  echo "backup complete: ${dir}"
}

micro_restore() { # DIR FLAG
  local dir="${1:?usage: ops micro restore DIR --yes}" flag="${2:-}"
  require_yes "${flag}" "restore SQL dumps from ${dir} into cluster postgres (destructive)"
  [ -d "${dir}" ] || die "no such directory: ${dir}"
  local f db
  for f in "${dir}"/*.sql.gz; do
    [ -e "${f}" ] || die "no *.sql.gz dumps found in ${dir}"
    db="$(basename "${f}" .sql.gz)"
    case " ${SVC_DBS} " in *" ${db} "*) ;; *) die "dump name '${db}' is not a known service DB";; esac
    echo "  -- restoring ${db}"
    gunzip -c "${f}" | kube -n "${NS}" exec -i "${PG_POD}" -- psql -v ON_ERROR_STOP=1 -U "${db}" -d "${db}" >/dev/null
  done
  echo "restore complete"
}
```

- [ ] **Step 2: Syntax check**

Run: `bash -n ops`
Expected: clean

- [ ] **Step 3: Live backup check**

Run: `./ops micro backup && ls backups/micro/*/ | head -3 && find backups/micro -name '*.sql.gz' | wc -l`
Expected: `12` dump files, each non-empty (`ls -la backups/micro/*/` shows size > 0)

Run: `gunzip -c "$(find backups/micro -name 'party.sql.gz' | head -1)" | head -5`
Expected: standard pg_dump header (`-- PostgreSQL database dump`)

Run: `./ops micro restore backups/micro/nope`
Expected: `DRY-RUN (nothing done). Would: restore SQL dumps from backups/micro/nope...`, exit 1

- [ ] **Step 4: Commit**

```bash
git add ops
git commit -m "Added: ops micro backup/restore — pg_dump all service DBs, --yes-gated restore"
```

---

### Task 4: mono (build/start/stop/status/logs/psql/backup/restore)

**Files:**
- Modify: `ops` — add three functions + real `cmd_mono` body

- [ ] **Step 1: Add functions and replace `cmd_mono` stub**

```bash
mono_start() {
  command -v docker >/dev/null || die "docker not installed"
  docker image inspect ofbiz-docker >/dev/null 2>&1 \
    || { echo "image ofbiz-docker missing — building"; docker build -t ofbiz-docker monolith/; }
  cat > "${MONO_DIR}/docker-compose.override.yml" <<'EOF'
# ops-managed: durable postgres data for the postgres-demo example.
services:
  db:
    volumes:
      - ops_pgdata:/var/lib/postgresql/data
volumes:
  ops_pgdata:
EOF
  mono up -d
  echo "starting (first run loads demo data — takes minutes). UI: https://localhost:8443"
}

mono_stop() {
  if [ "${STOP_ALL:-0}" = "1" ]; then
    mono down -v
    echo "stopped; volumes wiped (STOP_ALL=1)"
  else
    mono down
    echo "stopped; pgdata volume kept (set STOP_ALL=1 to wipe)"
  fi
}

mono_backup() {
  local dir="backups/mono/$(ts)" db
  mkdir -p "${dir}"
  trap 'rm -rf "${dir}"' ERR   # failed backup must not leave a valid-looking partial dir
  for db in ${MONO_DBS}; do
    echo "  -- ${db}"
    mono exec -T db pg_dump -U postgres "${db}" | gzip > "${dir}/${db}.sql.gz"
  done
  trap - ERR
  echo "backup complete: ${dir}"
}

mono_restore() { # FILE FLAG
  local file="${1:?usage: ops mono restore FILE --yes}" flag="${2:-}"
  require_yes "${flag}" "restore ${file} into monolith postgres (destructive)"
  [ -f "${file}" ] || die "no such file: ${file}"
  local db
  db="$(basename "${file}" .sql.gz)"
  case " ${MONO_DBS} " in *" ${db} "*) ;; *) die "dump name '${db}' not one of: ${MONO_DBS}";; esac
  gunzip -c "${file}" | mono exec -T db psql -v ON_ERROR_STOP=1 -U postgres -d "${db}" >/dev/null
  echo "restore complete: ${db}"
}

cmd_mono() {
  local action="${1:-}"; shift || true
  case "${action}" in
    build)   docker build -t ofbiz-docker monolith/ ;;
    start)   mono_start ;;
    stop)    mono_stop ;;
    status)  mono ps
             curl -sk -o /dev/null -w "https://localhost:8443 -> HTTP %{http_code}\n" https://localhost:8443 || true ;;
    logs)    mono logs -f ofbiz ;;
    psql)    mono exec db psql -U ofbiz -d ofbizmaindb ;;
    backup)  mono_backup ;;
    restore) mono_restore "${1:-}" "${2:-}" ;;
    *)       die "unknown mono action '${action}' (see: ops help)" ;;
  esac
}
```

- [ ] **Step 2: Syntax check**

Run: `bash -n ops`
Expected: clean

- [ ] **Step 3: Checks**

Run: `./ops mono status`
Expected: compose error about missing project/containers is fine (or `curl` line with `000`); must not abort the script before printing — `|| true` holds. Exit 0.

Run: `./ops mono restore backups/mono/x.sql.gz`
Expected: DRY-RUN message, exit 1

Manual (heavy, ~10–20 min, requires Docker running): `./ops mono build && ./ops mono start && ./ops mono status`
Expected: build succeeds; containers up; after demo load, `https://localhost:8443 -> HTTP 200` or `302`. Then `./ops mono backup && find backups/mono -name '*.sql.gz' | wc -l` → `3`. Then `./ops mono stop` → `stopped; pgdata volume kept`, and `docker volume ls | grep ops_pgdata` shows the volume. Then `./ops mono start` again — must NOT re-run initdb (pgdata persisted; logs lack `initdb` lines). Name is `docker-compose.override.yml` (auto-merged by compose; `docker-compose.ops-override.yml` would be silently ignored).

- [ ] **Step 4: Commit**

```bash
git add ops
git commit -m "Added: ops mono — build/start/stop/status/logs/psql/backup/restore via postgres-demo compose + durable pgdata override"
```

---

### Task 5: gitignore, docs, final gate

**Files:**
- Modify: `.gitignore` (append one line)
- Modify: `docs/superpowers/specs/2026-10-26-ops-toolkit-design.md` (amend two lines)
- Modify: `IMPLEMENTATION.md` (add P10 story line)

- [ ] **Step 1: Ignore backups**

Append to root `.gitignore` (file exists, ends with `/docs/asciidoc/images/`):

```
backups/
monolith/docker/examples/postgres-demo/docker-compose.override.yml
```

- [ ] **Step 2: Amend spec to match reality**

In `docs/superpowers/specs/2026-10-26-ops-toolkit-design.md`:
- Replace `pg_dump ofbiz -> backups/mono/<ts>.sql.gz` with `pg_dump all mono DBs (ofbizmaindb/olap/tenant) -> backups/mono/<ts>/`
- Replace the sentence `Bash tab completion registered at script bottom.` with `Tab completion dropped — a child process cannot register completions in the parent shell; would require sourcing the dispatcher.`

- [ ] **Step 3: Add IMPLEMENTATION.md story**

Read `IMPLEMENTATION.md`, find the P10 section (grep `P10`), and add this line under its story list (match existing list indentation):

```
- **F1 (M):** Unified ops toolkit `ops` (root dispatcher: micro up/down/status/logs/restart/deploy/verify/seed/backup/restore/fwd + mono build/start/stop/status/logs/psql/backup/restore via postgres-demo compose, durable pgdata). AC: [ ] `bash -n ops` clean [ ] `ops help` lists all 19 subcommands [ ] `ops micro status|restart|backup` green on live cluster [ ] `ops mono start` → https://localhost:8443 responds, restart keeps pgdata [ ] backups land in gitignored `backups/`. Spec: docs/superpowers/specs/2026-10-26-ops-toolkit-design.md — plan: docs/superpowers/plans/2026-10-27-ops-toolkit.md
```

Tick the AC boxes (`[ ]` → `[x]`) only for checks actually run and passing.

- [ ] **Step 4: Final gate**

Run: `bash -n ops && ./ops help | grep -c 'ops micro\|ops mono' && git status --short`
Expected: `19`, working tree only shows the three modified files; `backups/` NOT listed (ignored)

- [ ] **Step 5: Commit**

```bash
git add .gitignore docs/superpowers/specs/2026-10-26-ops-toolkit-design.md IMPLEMENTATION.md
git commit -m "Added: ops toolkit docs — backups gitignored, spec amended (3 mono DBs, no completion), IMPLEMENTATION.md P10-F1 story"
```

---

## Self-Review

- **Spec coverage:** up/down/status/logs/restart/deploy/verify/seed/backup/restore/fwd → Task 2/3; mono build/start/stop/status/logs/psql/backup/restore → Task 4; `--yes` gates on down/restore ×2 → `require_yes` (Task 1) used at all four sites; STOP_ALL → `mono_stop`; backups gitignored → Task 5. Gap vs spec: completion — dropped, documented amendment in Task 5. Covered.
- **Placeholders:** none; every step has full code or exact commands.
- **Consistency:** `require_yes(flag, description)`, `check_deploy`, `kube`, `mono`, `micro_backup/micro_restore/micro_fwd`, `mono_start/mono_stop/mono_backup/mono_restore` — names identical across tasks. `DEPLOYS` (16 names incl. demo-svc) vs `SVC_DBS` (12 DBs) — different lists on purpose: gateway/demo-svc have no dedicated DB, platform services use shared postgres roles.
