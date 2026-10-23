# billing-svc schema mapping

FROZEN P5 split of `monolith/applications/datamodel/entitydef/accounting-entitymodel.xml`
(184 entities incl. 37 view-entities) per FUTURESTATE-LOWLEVEL.md §2.1.
billing-svc owns **99** (76 real + 23 views); see finance-svc/MAPPING.md for the
other 85. Crossover entities resolved per-entity (§2.1 note): payment-method
details (CheckAccount, CreditCard, Deduction, DeductionType, EftAccount) go to
billing-svc so **PCI scope stays inside billing-svc**; GL-mapping tables
(CreditCardTypeGlAccount, SettlementTerm, VarianceReasonGlAccount) go to
finance-svc. Split approved with SME 2026-10-23 (CG-5 item 1).

## Live slice (migrated in P5)

| OFBiz entity | billing-svc home | Notes |
|---|---|---|
| Invoice | `billing.domain.Invoice` (invoices) | header + guarded status machine IN_DRAFT→INVOICE_IN_PROCESS→INVOICE_READY→INVOICE_PAID/INVOICE_CANCELLED/INVOICE_WRITEOFF (mirrors monolith InvoiceStatus flow) |
| InvoiceItem | lines JSON on Invoice | seq + item type + qty + amount + description |
| InvoiceStatus | statusHistory embedded on Invoice | per-transition append, like order-svc |
| InvoiceTerm / TermType | terms JSON on Invoice | net-days etc., free-form type ids |
| Payment | `billing.domain.Payment` (payments) | amount + status + idempotency key |
| PaymentApplication | applications JSON on Payment | invoiceId + amountApplied |
| PaymentGatewayResponse | `billing.domain.GatewayAttempt` (gateway_attempts) | every adapter call: refNum, result code, idempotency key — reconciliation source |
| BillingAccount | `billing.domain.BillingAccount` (billing_accounts) | credit limit + available computed from AR |
| BillingAccountTerm | terms JSON on BillingAccount | credit-limit check uses account terms |
| InvoiceType / PaymentType / InvoiceItemType / StatusItem | free-form ids | shared vocabularies stay ids; no lookup tables |
| CreditCard / EftAccount / CheckAccount | paymentMethodId reference only | raw PAN data NEVER stored (PCI: token from gateway adapter only); tables archived in monolith |

## Historical archive

The remaining ~85 owned entities (Budget*, PaymentGroup*, InvoiceRole/Note/
Content/Attribute, view-entities, ...) are **not migrated**. They remain owned
by the monolith until their owning phase pulls them; at Phase 7 untouched
tables are archived per ADR-012; nothing is dropped before then.

## Full assignment (99)

| BillingAccount | BillingAccountAndRole | BillingAccountRole | BillingAccountRoleAndAddress |
| BillingAccountTerm | BillingAccountTermAttr | Budget | BudgetAttribute |
| BudgetItem | BudgetItemAttribute | BudgetItemType | BudgetItemTypeAttr |
| BudgetReview | BudgetReviewResultType | BudgetRevision | BudgetRevisionImpact |
| BudgetRole | BudgetScenario | BudgetScenarioApplication | BudgetScenarioRule |
| BudgetStatus | BudgetType | BudgetTypeAttr | CheckAccount |
| CreditCard | Deduction | DeductionType | EftAccount |
| InvItemAndOrdItem | Invoice | InvoiceAndApplAndPayment | InvoiceAndRole |
| InvoiceAndType | InvoiceAttribute | InvoiceContactMech | InvoiceContent |
| InvoiceContentAndInfo | InvoiceContentType | InvoiceItem | InvoiceItemAndAssocProduct |
| InvoiceItemAndShipmentView | InvoiceItemAssoc | InvoiceItemAssocType | InvoiceItemAttribute |
| InvoiceItemType | InvoiceItemTypeAttr | InvoiceItemTypeGlAccount | InvoiceItemTypeMap |
| InvoiceNote | InvoiceRole | InvoiceStatus | InvoiceTerm |
| InvoiceTermAttribute | InvoiceType | InvoiceTypeAttr | PartyAcctgPreference |
| PartyFixedAssetAssignAndRole | PartyFixedAssetAssignment | PartyGlAccount | PartyPrefDocTypeTpl |
| PartyPrefDocTypeTplAndCustomScreen | PartyRate | PartyTaxAuthInfo | PartyTaxAuthInfoAndDetail |
| PayPalPaymentMethod | Payment | PaymentAndApplication | PaymentAndType |
| PaymentAndTypeAndCreditCard | PaymentAndTypePartyNameView | PaymentApplication | PaymentAttribute |
| PaymentBudgetAllocation | PaymentContent | PaymentContentAndInfo | PaymentContentType |
| PaymentGatewayConfig | PaymentGatewayConfigType | PaymentGatewayRespMsg | PaymentGatewayResponse |
| PaymentGlAccountTypeMap | PaymentGroup | PaymentGroupMember | PaymentGroupType |
| PaymentMethod | PaymentMethodAndCreditCard | PaymentMethodAndEftAccount | PaymentMethodAndFinAccount |
| PaymentMethodAndGiftCard | PaymentMethodType | PaymentMethodTypeGlAccount | PaymentType |
| PaymentTypeAttr | PmtGrpMembrPaymentAndFinAcctTrans | ProductAverageCost | ProductAverageCostType |
| RateAmount | RateAmountAndRelations | RateType |
