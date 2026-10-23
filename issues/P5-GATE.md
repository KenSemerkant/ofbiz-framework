# P5-GATE — CG-5 evidence (billing-svc + finance-svc)

Run date: 2026-10-23 (local kind staging). All commands re-runnable.

## CG-5 checklist

| Item | Status | Evidence |
|---|---|---|
| SME-approved entity split frozen in a migration mapping doc | **PASS** | `microservices/billing-svc/MAPPING.md` (99 entities) + `microservices/finance-svc/MAPPING.md` (85) — full enumeration of accounting-entitymodel.xml (147 real + 37 view = 184), crossover resolved per-entity (payment-method details → billing for PCI confinement; GL mappings → finance) |
| Invoice issue→settle e2e; payment gateway adapter (test gateway) auth/capture/void | **PASS** | `platform/services/80-billing-lifecycle.sh` §2/§4/§5: order.order.invoicerequested → invoice ISSUED; authorize → CAPTURED → SETTLED; second payment authorize → VOIDED; typed 409 on cancel-after-settle |
| GL auto-posting from billing events; trial balance equals monolith trial balance on migrated dataset | **PASS** | e2e §7: both auto-postings (ATT-INV-*-issued/settled) posted, TB balanced 569.99=569.99; `platform/cdc/35-check-finance-consistency.sh`: per-account D/C parity 1100=175.50/0, 1200=225.50/175.50, 4000=0/225.50 |
| notification-svc sends invoice emails; PCI scope documented as confined to billing-svc | **BLOCKED (infra) / PCI PASS** | PCI scope: `microservices/billing-svc/PCI-SCOPE.md`. Email path fully wired (billing-svc emits notification.requested.v1 in the issue tx; notification-svc template `invoice-issued` en/de; consumer verified end-to-end in unit tests + Kafka DLQ shows JavaMail attempts). Delivery blocked by a LOCAL kind networking fault — see below |

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

## BLOCKED: in-cluster SMTP delivery (local kind only)

Symptom: pod-to-pod TCP on ports outside the long-running service set
(1025/2525/8125/8443 observed) completes the TCP handshake but the
application stream never arrives — for every backend tried (mailhog
v1.0.1, mailpit), every client type (curl, socat, JavaMail, busybox),
VIP and direct-pod-IP, same-node and cross-node. Ports 8080/8025/5432/9093
work. Istio ambient/ztunnel is NOT deployed; node iptables/nftables carry
no port rules; kube-proxy restart did not change it. An UNMESHED pod
speaking full SMTP to a host-gateway mailpit (192.168.65.254) works
perfectly — only the meshed notification-svc's outbound SMTP is
blackholed.

Unblocking options (need a decision — removing notification's Istio
sidecar was denied by the permission gate, correctly, as an mTLS
posture change; recreating the kind cluster would destroy the running
P4 30-day soak):

1. Investigate the datapath fault (Docker Desktop VM restart is the
   likely fix; kind nodes carry scars from the 2026-10-23 disk-full
   incident).
2. Run an SMTP relay on the Docker Desktop VM host reached via
   ServiceEntry from the mesh (needs the fault above fixed anyway).
3. Accept host-mailpit delivery for local staging (works from unmeshed
   senders) and verify the meshed path on the cloud staging cluster.

`80-billing-lifecycle.sh` step 3 honors `P5_SKIP_EMAIL=1` to record this
as SKIP-BLOCKED; without it the drill fails loud (by design).

## Environment incidents during the gate run (fixed inline)

- Docker-VM disk 100%: ctr import-* images removed on all 3 nodes
  (P4 playbook; zsh word-splitting cost one round trip — use xargs).
- Postgres connection-slot exhaustion (100 slots vs 12 services x10
  pools): idle pools terminated to unblock bootstorms; durable fix =
  max_connections 300 (needs a postgres restart — pending user consent).
- Postgres crashed during the disk window; recovered after space freed.
- mailhog → mailpit swap + kube-proxy/conntrack archaeology — outcome
  documented above.
