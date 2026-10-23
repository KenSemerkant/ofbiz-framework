# finance-svc schema mapping

FROZEN P5 split of `monolith/applications/datamodel/entitydef/accounting-entitymodel.xml`
per FUTURESTATE-LOWLEVEL.md §2.1. finance-svc owns **85** (71 real + 14 views);
see billing-svc/MAPPING.md for the other 99. Split approved with SME
2026-10-23 (CG-5 item 1).

## Live slice (migrated in P5)

| OFBiz entity | finance-svc home | Notes |
|---|---|---|
| GlAccount | `finance.domain.GlAccount` (gl_accounts) | code + name + postBalance(debit/credit) + class id |
| GlAccountType / GlAccountClass / GlAccountTypeDefault | free-form ids | vocabularies stay ids |
| AcctgTrans | `finance.domain.GlTransaction` (gl_transactions) | journal batch; posted flag; periodId |
| AcctgTransEntry | `finance.domain.GlEntry` (gl_entries) | debit OR credit per line (postedAmount + debitCreditFlag) |
| CustomTimePeriod (defined in party-entitymodel.xml) | `finance.domain.Period` (periods) | cross-file exception: ledger slice owns fiscal periods; from/thru dates + closed flag |
| InvoiceItemTypeGlAccount / PaymentTypeGlAccount etc. | `finance.domain.PostingRule` (posting_rules) | configurable event→account mapping (P5-F2-S2); replaces monolith GlAccountTypeDefault lookups |
| GlJournal / GlJournalRecord | not migrated | manual-journal batching not needed by auto-posting; add when a manual journal UI appears |
| GlAccountHistory | derived | trial-balance API computes balances on demand from gl_entries |

## Historical archive

Remaining ~74 owned entities (FinAccount*, TaxAuthority*, GlAccountCategory/
Group/Role, Budget cross-refs, view-entities, ...) are **not migrated**. They
remain owned by the monolith until Phase 7 (ADR-012 archive; nothing dropped
before then).

## Full assignment (85)

| AccommodationClass | AccommodationMap | AccommodationMapType | AccommodationSpot |
| AcctgTrans | AcctgTransAndEntries | AcctgTransAttribute | AcctgTransEntry |
| AcctgTransEntrySums | AcctgTransEntryType | AcctgTransType | AcctgTransTypeAttr |
| CreditCardTypeGlAccount | FinAccount | FinAccountAndRole | FinAccountAttribute |
| FinAccountAuth | FinAccountAuthSum | FinAccountRole | FinAccountStatus |
| FinAccountTrans | FinAccountTransAttribute | FinAccountTransSum | FinAccountTransType |
| FinAccountTransTypeAttr | FinAccountType | FinAccountTypeAttr | FinAccountTypeGlAccount |
| FixedAsset | FixedAssetAndGeoPoint | FixedAssetAttribute | FixedAssetDepMethod |
| FixedAssetGeoPoint | FixedAssetIdent | FixedAssetIdentType | FixedAssetMaint |
| FixedAssetMaintOrder | FixedAssetMaintWorkEffort | FixedAssetMeter | FixedAssetProduct |
| FixedAssetProductType | FixedAssetRegistration | FixedAssetStdCost | FixedAssetStdCostType |
| FixedAssetType | FixedAssetTypeAttr | FixedAssetTypeGlAccount | GiftCard |
| GiftCardFulfillment | GlAccOrgAndAcctgTransAndEntry | GlAccount | GlAccountAndHistory |
| GlAccountAndHistoryTotals | GlAccountCategory | GlAccountCategoryMember | GlAccountCategoryType |
| GlAccountClass | GlAccountGroup | GlAccountGroupMember | GlAccountGroupType |
| GlAccountHistory | GlAccountOrganization | GlAccountOrganizationAndClass | GlAccountRole |
| GlAccountType | GlAccountTypeDefault | GlBudgetXref | GlFiscalType |
| GlJournal | GlReconciliation | GlReconciliationEntry | GlResourceType |
| GlXbrlClass | SettlementTerm | TaxAuthority | TaxAuthorityAndDetail |
| TaxAuthorityAssoc | TaxAuthorityAssocType | TaxAuthorityCategory | TaxAuthorityCategoryView |
| TaxAuthorityGlAccount | TaxAuthorityGlAccountBalance | TaxAuthorityRateProduct | TaxAuthorityRateType |
| VarianceReasonGlAccount |
