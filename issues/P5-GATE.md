# P5-GATE — CG-5 evidence (billing-svc + finance-svc)

Run date: 2026-10-23 (local kind staging; email leg verified same day after
the eager-adapter workaround below). All commands re-runnable.

## CG-5 checklist

| Item | Status | Evidence |
|---|---|---|
| SME-approved entity split frozen in a migration mapping doc | **PASS** | `microservices/billing-svc/MAPPING.md` (99 entities) + `microservices/finance-svc/MAPPING.md` (85) — full enumeration of accounting-entitymodel.xml (147 real + 37 view = 184), crossover resolved per-entity (payment-method details → billing for PCI confinement; GL mappings → finance) |
| Invoice issue→settle e2e; payment gateway adapter (test gateway) auth/capture/void | **PASS** | `platform/services/80-billing-lifecycle.sh` §2/§4/§5: order.order.invoicerequested → invoice ISSUED; authorize → CAPTURED → SETTLED; second payment authorize → VOIDED; typed 409 on cancel-after-settle |
| GL auto-posting from billing events; trial balance equals monolith trial balance on migrated dataset | **PASS** | e2e §7: both auto-postings (ATT-INV-*-issued/settled) posted, TB balanced 569.99=569.99; `platform/cdc/35-check-finance-consistency.sh`: per-account D/C parity 1100=175.50/0, 1200=225.50/175.50, 4000=0/225.50 |
| notification-svc sends invoice emails; PCI scope documented as confined to billing-svc | **PASS** | e2e §3: "invoice email delivered (mailpit)". billing-svc emits notification.requested.v1 in the issue tx; notification-svc renders the `invoice-issued` template (en/de) and delivers via the eager client-first SMTP adapter. PCI scope: `microservices/billing-svc/PCI-SCOPE.md` |

## Consistency (P5-F3-S1)

Delta 0 on the migrated dataset (seed `46-seed-accounting.sh` → import `47-import-accounting.sh`):

```
consistency delta report (P5-GATE billing slice, migrated dataset)
  invoices : monolith/billing checksums d8c770db85dc/d8c770db85dc
  payments : monolith/billing checksums f6f41d65fddc/f6f41d65fddc
  DELTA 0 — billing slice consistent
trial-balance parity report (P5-GATE finance slice, migrated dataset)
  monolith per-account D/C:
    1100=175.50/0
    1200=225.50/175.50
    4000=0/225.50
  finance-svc per-account D/C:
    1100=175.50/0
    1200=225.50/175.50
    4000=0/225.50
  PARITY — trial balance equals the monolith trial balance
```

## Local kind datapath fault + workaround (root cause)

The local kind cluster eats a server's first unsolicited data packet:
connections where the SERVER speaks first (an SMTP banner on 1025/2525/
any port) complete the TCP handshake and then blackhole, for every
backend (mailhog v1.0.1, mailpit), client (curl, socat, JavaMail,
busybox), VIP and direct pod IP, meshed and unmeshed. Connections where
the CLIENT speaks first (HTTP requests, the Postgres startup packet,
Kafka client-first) flow normally. Node iptables/nftables carry no port
rules; kube-proxy restarts and ethtool offload changes did not affect
it. Reproducer: connect to an SMTP port, read → timeout; send EHLO
before reading → banner + reply arrive on the same connection.

Workaround (staging-only, both pieces in the repo):

1. `EagerSmtpSender` (notification-svc) — raw-socket SMTP client that
   transmits EHLO before reading the greeting; selected with
   `notification.mail.eager=true` (JavaMail stays the default for real
   environments). Proven by EagerSmtpSenderTest, whose fake server
   holds the greeting until the client speaks.
2. mailpit (mailhog successor) serves SMTP on **8080** in the service
   port class the datapath handles (`--smtp 0.0.0.0:8080`), UI on 8025.

A Docker Desktop VM reboot was tested 2026-10-23 (user-approved): the
fault SURVIVES it — structural to this Docker Desktop/kind datapath, not
transient state. The eager-8080 workaround is therefore permanent for
local staging; cloud staging uses the JavaMail adapter unchanged. If a
future Docker Desktop upgrade fixes it, revert by dropping
NOTIFICATION_MAIL_EAGER and the --smtp arg.

## Environment incidents during the gate run (fixed inline)

- Docker-VM disk 100%: ctr import-* images removed on all 3 nodes
  (P4 playbook; zsh word-splitting cost one round trip — use xargs).
- Postgres connection-slot exhaustion (100 slots vs 12 services x10
  pools): idle pools terminated to unblock bootstorms; durable fix =
  max_connections 300 (needs a postgres restart — pending user consent).
- Postgres crashed during the disk window; recovered after space freed.
- VM reboot (approved): cluster + PVs survived; node-local images needed
  a full redeploy; monolith staging DB had NO volume and reset to
  initdb state — P4 order + P5 accounting tables re-created (13-extend,
  46-seed, 47-import) and a PVC added to monolith-db.yaml so it cannot
  recur; CDC slots auto-recreated, all connectors RUNNING; checkers and
  the full CG-5 e2e re-verified green post-reboot.
- mailhog → mailpit swap + kube-proxy/conntrack archaeology — outcome
  documented above.
