# humanres-svc schema mapping

Split of `monolith/applications/datamodel/entitydef/humanres-entitymodel.xml`
(46 entities: 41 real + 5 views) per FUTURESTATE-ARCHITECTURE.md §5.10.

## Live slice (migrated in P6)

| OFBiz entity | humanres-svc home | Notes |
|---|---|---|
| EmplPosition | `humanres.domain.EmplPosition` (empl_positions) | id + type + status; reportingToEmplPositionId replaces the EmplPositionReportingStruct edge (kept inline — no cross-position query in any Phase-6 story) |
| EmplPositionType | free-form ids | vocabulary stays ids (emplPositionTypeId) |
| StatusItem (EmplPosition status) | free-form ids | statusId stays an id |
| Employment | `humanres.domain.Employment` (employments) | collapsed to a single-party-to-position employment with a surrogate employmentId PK; the monolith's (roleTypeIdFrom, roleTypeIdTo, partyIdFrom, partyIdTo, fromDate) composite collapses to (partyId=employee, emplPositionId, fromDate) — the employer side is implicit (internal org), terminationReasonId/terminationTypeId deferred with the offboarding story |
| Party (party-entitymodel.xml) | `party_projection` table | names JSON for validation/display only — party data is owned by party-svc, linkage by id (no duplication) |

Events: `humanres.position.created/updated.v1`,
`humanres.employment.created.v1`, `humanres.onboarding.completed.v1`
(topic prefix `humanres.` per the ADR-009 `<context>.…` rule; the
FUTURESTATE §5.10 sketch says `hr.` — superseded here by the implemented
contract, recorded as a P6 deviation to reconcile in the docs pass).

## Deferred (not migrated)

Remaining ~41 entities (skills, payroll inputs, recruitment, leave,
performance reviews) — the P6-T3 story is positions + employments +
onboarding only. They remain owned by the monolith until their story
lands (ADR-012 archive; nothing dropped before Phase 7).

## Full assignment (46)

| BenefitType | BenefitTypeAndParty | EmplLeave | EmplLeaveReasonType | EmplLeaveType |
| Employment | EmploymentAndPerson | EmploymentApp | EmploymentAppSourceType | EmplPosition |
| EmplPositionAndFulfillment | EmplPositionClassType | EmplPositionFulfillment | EmplPositionFulfillmentAndReportingStruct | EmplPositionReportingStruct |
| EmplPositionResponsibility | EmplPositionType | EmplPositionTypeClass | EmplPositionTypeRate | EmplPositionTypeRateAndAmount |
| JobInterview | JobInterviewType | JobRequisition | PartyBenefit | PartyQual |
| PartyQualType | PartyResume | PartySkill | PayGrade | PayHistory |
| PayrollPreference | PerformanceNote | PerfRatingType | PerfReview | PerfReviewItem |
| PerfReviewItemType | PersonTraining | ResponsibilityType | SalaryStep | SkillType |
| TerminationReason | TerminationType | TrainingClassType | TrainingRequest | UnemploymentClaim |
| ValidResponsibility |
