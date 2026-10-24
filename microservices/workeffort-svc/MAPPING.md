# workeffort-svc schema mapping

Live slice of `monolith/applications/datamodel/entitydef/workeffort-entitymodel.xml`
(41 entities incl. views) per FUTURESTATE-LOWLEVEL.md. Phase 6 migrates the
projects/tasks/timesheets/calendar core; the long tail stays on the monolith
until Phase 7 (ADR-012 archive; nothing dropped before then).

## Live slice (migrated in P6)

| OFBiz entity | workeffort-svc home | Notes |
|---|---|---|
| WorkEffort | `workeffort.domain.WorkEffort` (work_efforts) | id/type/name/parent/status + the two estimate dates; status machine forward-only CREATED→IN_PROGRESS→COMPLETED, CANCELLED from any live state |
| WorkEffortType / WorkEffortPurposeType / StatusItem / Enumeration | enum + free-form ids | `WorkEffortType{PROJECT,TASK,EVENT}` live subset; vocabularies stay ids |
| Timesheet | `workeffort.domain.Timesheet` (timesheets) | partyId + from/thru + statusId (DRAFT/SUBMITTED) + entries |
| TimeEntry | `workeffort.domain.TimesheetEntry` (timesheets.entries JSON) | inline-JSON precedent: {workEffortId, hours, rateTypeId} on the sheet row; no cross-entry query in any Phase-6 story |
| RateType | free-form id | `rateTypeId` stays a vocabulary id |
| WorkEffortIcalData | not migrated | monolith stores generated iCal per work effort; our export is **computed on demand** from the aggregate (ICalendar renderer in the domain) — nothing stored, nothing to go stale |

## Deferred (stay on the monolith until Phase 7)

WorkEffortPartyAssignment, WorkEffortAssoc (+Type/Attribute), Deliverable
(+Type), WorkEffortAttribute/Billing/ContactMech/Content/ContentType,
WorkEffortCostCalc, WorkEffortDeliverableProd, WorkEffortEventReminder,
WorkEffortFixedAssetAssign/Std, WorkEffortGoodStandard (+Type),
WorkEffortInventoryAssign/Produced, WorkEffortInvRes, WorkEffortKeyword,
WorkEffortNote, WorkEffortReview, WorkEffortSearchConstraint/SearchResult,
WorkEffortSkillStandard, WorkEffortStatus (superseded by the typed status
machine on the aggregate), WorkEffortSurveyAppl, WorkEffortTransBox,
WorkEffortTypeAttr, TimesheetRole, CommunicationEventWorkEff. Party,
Invoice, InventoryItem, FixedAsset etc. are owned by their own services —
referenced by id only (ADR-007).

## Full assignment (41)

| ApplicationSandbox | CommunicationEventWorkEff | Deliverable | DeliverableType | TimeEntry | Timesheet |
| TimesheetRole | WorkEffort | WorkEffortAssoc | WorkEffortAssocAttribute | WorkEffortAssocType | WorkEffortAssocTypeAttr |
| WorkEffortAttribute | WorkEffortBilling | WorkEffortContactMech | WorkEffortContent | WorkEffortContentType | WorkEffortCostCalc |
| WorkEffortDeliverableProd | WorkEffortEventReminder | WorkEffortFixedAssetAssign | WorkEffortFixedAssetStd | WorkEffortGoodStandard | WorkEffortGoodStandardType |
| WorkEffortIcalData | WorkEffortInventoryAssign | WorkEffortInventoryProduced | WorkEffortInvRes | WorkEffortKeyword | WorkEffortNote |
| WorkEffortPartyAssignment | WorkEffortPurposeType | WorkEffortReview | WorkEffortSearchConstraint | WorkEffortSearchResult | WorkEffortSkillStandard |
| WorkEffortStatus | WorkEffortSurveyAppl | WorkEffortTransBox | WorkEffortType | WorkEffortTypeAttr | |
