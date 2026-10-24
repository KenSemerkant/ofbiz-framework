# marketing-svc schema mapping

Slice of `monolith/applications/datamodel/entitydef/marketing-entitymodel.xml`
per FUTURESTATE-LOWLEVEL.md. marketing-svc owns the campaign/tracking-code/
opportunity core plus the attribution report (P6-T5); the contact-list and
segment-group families stay monolith-side until a story pulls them.

## Live slice (migrated in P6)

| OFBiz entity | marketing-svc home | Notes |
|---|---|---|
| MarketingCampaign | `marketing.domain.MarketingCampaign` (marketing_campaigns) | name + statusId (PLANNED/ACTIVE/COMPLETED closed vocabulary) + date window; cost/budget/lead fields deferred until a campaign-planning UI appears |
| TrackingCode | `marketing.domain.TrackingCode` (tracking_codes) | campaign link kept as FK-by-id (nullable); redirect/override branding fields not migrated |
| TrackingCodeOrder | `attributions` table | replaced by the event-driven read model: order.order.placed.v1 carrying an optional trackingCode (OrderPlaced.avsc superset, P4-F2-S2) lands one row per coded order, unique on order_id; THIS service owns the report (order-svc keeps only its internal count) |
| SalesOpportunity | `marketing.domain.SalesOpportunity` (sales_opportunities) | description + stage + closeProbability (int 0-100, guarded); next-step/estimated-amount/date fields deferred |

Events: `marketing.campaign.created/updated.v1`,
`marketing.trackingcode.created/updated.v1`,
`marketing.opportunity.created/updated.v1` (transactional outbox, ADR-009).

## Deferred (remain monolith-owned until a story pulls them)

- TrackingCodeType, TrackingCodeVisit, TrackingCodeOrderReturn,
  TrackingCodeAnd* view entities — visit tracking and return attribution
  need the analytics pipeline, not the CRUD slice
- ContactList / ContactListParty / ContactListCommStatus and the whole
  segment-group family — email marketing scope (notification-svc adjacent)
- SalesOpportunityHistory / Role / WorkEffort / Quote / Forecast entities —
  SFA pipeline, blocked on party-svc role links
- MarketingCampaignNote / Price / Promo / Role — campaign composition,
  blocked on product-svc price rules and party-svc roles
