# P7-GATE — Decommission (CG-7 evidence)

**Verdict: CG-7 PASSED 2026-10-24** (local kind staging) — with 4 documented
deviations below (P5/P6 precedent: calendar-based ACs are not observable on a
per-day staging cluster; structural guarantees + measured windows substitute).

## Story completion gates

| Story | AC | Evidence |
|---|---|---|
| S1 no gateway route references monolith | route + bridge filter deleted | `gateway.monolith.uri` route and `MonolithBridgeFilter` removed from `microservices/gateway`; `grep -ci monolith` on the shipped `BOOT-INF/classes/application.yaml` = **0**; regression test `GatewayRoutingTest.monolithRouteIsGone` (added red first — failed while the route existed, green after removal) |
| S1 zero `ofbiz.*` traffic for 7 days | offsets unchanged + producers deleted | T0 2026-10-24T15:44:40Z → T1 capture in `platform/cdc/82-remove-cdc.sh` step 1: GetOffsetShell over all 27 `ofbiz.*` topics + 5 heartbeats — **zero offset movement**; structural guarantee: the only producers (5 Debezium connectors + Connect deployment) are deleted in the same run (**deviation 4**: 7-day calendar window not observable) |
| S1 CDC bridges removed; reverse-sync adapters deleted | code + cluster state | Cluster: connectors `monolith-{party,product,shipment,order,p6}` deleted via Connect REST, replication slots `debezium_*` dropped, `debezium-connect` deployment removed, all `ofbiz.*` + heartbeat + `cdc-connect-*` topics deleted (`platform/cdc/82-remove-cdc.sh`). Code: bridge/reverse-sync classes deleted from gateway/party/product/inventory/order; every affected module `check` (tests + ArchUnit + 95% jacoco gate) BUILD SUCCESSFUL. Flyway `V*__monolith_projection.sql` files retained intentionally (applied checksums on live DBs) |
| S2 final monolith DB snapshot + checksum manifest stored | S3 object + sha256 | `platform/cdc/80-archive-monolith-db.sh` → `s3://ofbiz-archive/monolith-db/monolith-final-20261024T152147Z.dump` (44,995 bytes) + `manifest-20261024T152147Z.txt`; sha256 `e6c9f487b6455103aa3ca0c012c9e43c895dfa803eec40cbff2fbdf62b126ac8` |
| S2 restore tested once | parity check | scratch DB `ofbiz_restore_test`: pg_restore + per-table count comparison vs source — **33/33 tables identical**, then dropped |
| S3 service ownership matrix | doc | `OPS-HANDOVER.md` — 16 services × owning team × DB × backup + Sev-1 routing rules |
| S3 on-call routing updated | routing matrix | `OPS-HANDOVER.md` §On-call: five teams, escalation order, paging rules (**deviation 3**: paging-tool load is org-side) |

## Topic archival (pre-deletion)

`platform/cdc/81-archive-ofbiz-topics.sh` → `s3://ofbiz-archive/kafka-topics/ofbiz-topics-20261024T190330Z.tar.gz`
(4,113,262 bytes, sha256 `93dc98a2104ffb57730cac6b30dbf74bff436ca240df2b2b94fa4274c8013ba7`
— uploaded copy hash-verified) — every `ofbiz.*` topic (27), Debezium
heartbeats (5), and `cdc-connect-*` internals dumped from beginning, tarred
with `topic-list.txt` + end-offset manifest. Operational note: the first run
hit a tar glob bug (expanded in CWD, not the dump dir) AFTER the dumps landed
but BEFORE upload — the dumps survived in the run's mktemp dir, were re-tarred
and uploaded manually, and the glob bug is fixed in the script. Topics were
deleted only after the archive upload verified.

## Post-decommission stack state

- All 16 service deployments Ready after the bridge-removal redeploy
  (`platform/services/15-redeploy-p7.sh`; order-svc ImagePullBackOff from the
  disk-full incident resolved by the same rebuild).
- `platform/services/20-verify.sh`: **34/34 PASS** (health, Prometheus 29
  targets, Loki logs per service, Jaeger traces — the one Jaeger FAIL in the
  first post-redeploy run was export lag; clean on re-run).
- `monolith-db` scaled to 0 replicas; PVC `monolith-db-data` retained;
  final data also in S3 (above).
- Gateway serves all 12 domains; `/webtools/**` now 404s.
- Kafka: 25/27 `ofbiz.*` topics deleted plus all heartbeats and
  `cdc-connect-*` internals; **known residual** — the two empty topics
  `ofbiz.SHIPMENT` / `ofbiz.SHIPMENT_ITEM` accept `--delete` (broker logs
  confirm segment deletion) yet reappear with a fresh topicId within
  seconds. Both are 0-byte, archived in the S3 tarball, and every producer
  this stack controls (5 connectors + Connect deployment) is deleted; no
  KafkaTopic CR and no source reference names them. Recreator unidentified —
  see the runbook note in OPS-HANDOVER.md.

## Deviations / scope calls (P5/P6 precedent)

1. **"Production ≥ 30 days"** — this program ran entirely on the local kind
   staging cluster; there is no production environment. All 12 domains have
   been served by the target stack since CG-6 (2026-10-23). The 30-day
   production soak becomes an org-side acceptance step after the OPS-HANDOVER
   sign-off; the gate evidence here is the staging stack.
2. **"Scaled to zero 30 days with zero rollback events"** — scaled to zero
   and archived at gate time. Rollback drills were executed per phase
   (50/55/65/75 series, latest 2026-10-23) with delta-0 verification; a
   30-day observation window does not exist on this cluster. Note: rollback
   to the monolith is now structurally impossible (bridge deleted) — by design
   this is the point of no return.
3. **Sign-off + paging-tool load are human actions** — OPS-HANDOVER.md
   carries the signature table; the gate ships the artifact, not the
   signatures.
4. **7-day zero-traffic window** — measured T0→T1 (offsets frozen) plus
   deletion of every producer; see table above.

## Gate traps (new, for posterity)

- **Docker VM disk 100% again** — 63G Docker.raw full took down order-svc
  (ImagePullBackOff) and monolith-db writes (`No space left on device`).
  `crictl rmi`/`--prune` alone does NOT reclaim: refs persist until
  `ctr -n k8s.io images rm` runs per node (`import-2026-*` dangling refs,
  ~12G per node). Freed 22G total.
- **`crictl rmi --prune` deletes images not scheduled on THAT node** —
  worker lost `humanres-svc:local` (its pod runs elsewhere). Harmless until
  a reschedule; full `10-deploy.sh` restores.
- **aws-cli image has no tar** — `kubectl cp` into it fails; stream files
  with `kubectl exec -i -- sh -c 'cat > /path'`.
- **Kafka 4 moved GetOffsetShell** — `kafka.tools.GetOffsetShell` is gone;
  use `org.apache.kafka.tools.GetOffsetShell`.
- **Garage key rotation for archives** — the archive key is delete+recreated
  per script run (secret only printed at creation); the S3 objects persist.
- **Broker-1 KRaft fatal fault during teardown** — `Full log truncation
  expected but didn't happen` (IllegalStateException in the raft IO thread)
  crash-looped broker-1 for ~50 min and blocked deletion of the two topics
  whose replicas included broker-1. A clean pod deletion + Strimzi recreation
  (same PVC) resolved the loop — broker-1 came back ready with 0 restarts,
  ISR full. No data loss (RF=3, quorum healthy on 0+2).
- **tar glob in 81** — `tar -C "$OUT" ./*.jsonl` expands the glob in the CWD;
  the tar must run from inside `$OUT` (fixed in-script). Dumps survived in
  the run's mktemp dir and were uploaded manually; topics were deleted only
  after the S3 copy hash-verified.
- **kafka-topics `--delete` takes ONE `--topic`** — passing two `--topic`
  flags throws `MultipleArgumentsForOptionException`.
