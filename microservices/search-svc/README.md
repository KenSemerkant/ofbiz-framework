# search-svc

Part of the OFBiz → microservices target state. See:
- FUTURESTATE-ARCHITECTURE.md §6.3 (purpose, owned entities, API surface, events)
- FUTURESTATE-LOWLEVEL.md (module scaffolding, contracts, outbox, deployment)

Status: P1-F5-S1 implemented — Kafka consumer (`search.doc.indexed.v1`,
retry→DLQ), OpenSearch index adapter, REST `/api/v1/search` + reindex
command, gRPC query API. This folder becomes the service repo checkout
if/when the multi-repo split happens (open question §11.1).
