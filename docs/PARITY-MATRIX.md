# PARITY-MATRIX — Monolith UI vs Microservices SPA

Generated: 2026-10-26. Enumeration of every monolith screen definition
(widget XML `<screen name=...>`) per application, mapped to the owning
microservice, with current REST surface and SPA coverage. Execution
authority for sequencing: IMPLEMENTATION.md Phase 9.

Regenerate (screen counts + per-app lists):
```
for app in monolith/applications/*/; do echo "## $(basename $app)"; grep -rh '<screen name=' $app --include='*Screens.xml' | sed -E "s/.*name=.([A-Za-z0-9_-]+).*/\1/" | sort | uniq | paste -sd, - | fold -w 100; done
```

## Summary

| Monolith app | Screens | Owning service(s) | REST mappings (2026-10-26) | SPA coverage today |
|---|---|---|---|---|
| accounting | 311 | billing-svc + finance-svc | 15 + 12 | none |
| product | 284 | product-svc (+ inventory-svc for facilities) | 15 (+20) | catalog journey-slice |
| order | 222 | order-svc | 16 | order-to-cash + returns journey-slices |
| party | 146 | party-svc | 4 | parties journey-slice |
| content | 142 | content-svc | 9 | content + calendar journey-slices |
| humanres | 121 | humanres-svc | 9 | hire-to-retire journey-slice |
| marketing | 108 | marketing-svc | 15 | campaign journey-slice |
| manufacturing | 80 | manufacturing-svc | 10 | p6 + plan-to-ship journey-slices |
| workeffort | 65 | workeffort-svc | 16 | calendar journey-slice |
| commonext | 18 | platform (gateway/SPA) | — | partial (nav/shell) |
| securityext | 1 | identity-svc | 3 | login (OIDC) |
| **Total** | **1708** | **16 services** | **146** | **10 screens, 12 e2e journeys** |

Forms: 1318 `<form name=>` defs; menus: 27 files; FTL templates: 526.
SPA baseline: platform/spa — 865 lines TS/TSX, 10 screens.

## Per-app screen inventory

### accounting

    AcctgTransDetailReportPdf,AcctgTransEntriesSearchResultsCsv 
    AcctgTransEntriesSearchResultsPdf,AcctgTransSearchResultPdf 
    AcctgTransSearchResultsCsv,AddCompany AddGlAccount,ApInvoicesDueSoon 
    ApPastDueInvoices,ArInvoicesDueSoon ArPastDueInvoices,AssignGlAccount 
    AuthorizeTransaction,BalanceSheet BalanceSheetCsv,BalanceSheetPdf 
    BankReconciliation,BatchPayments BillingAccountInvoices,BillingAccountOrders 
    BillingAccountPayments,BudgetOverview BudgetReviews,BudgetRoles BudgetSearchResults,Calendar 
    CaptureTransaction,CashFlowStatement CashFlowStatementListCsv,CashFlowStatementListPdf 
    CommissionReport,CommissionReportPdf CommissionRun,CommonAccountDecorator 
    CommonAdminChecksDecorator,CommonAgreementDecorator 
    CommonApPaymentDecorator,CommonApReportDecorator 
    CommonArPaymentDecorator,CommonBillingAccountDecorator 
    CommonBudgetDecorator,CommonEmbeddedDecorator 
    CommonFinAccountDecorator,CommonFixedAssetDecorator 
    CommonFixedAssetMaintDecorator,CommonGlSetupDecorator 
    CommonInvoiceDecorator,CommonOrganizationAccountingReportsDecorator 
    CommonPartyDecorator,CommonPartyGlDecorator CommonPaymentDecorator,CommonPaymentGroupDecorator 
    CommonTaxAuthorityDecorator,ComparativeBalanceSheet 
    ComparativeBalanceSheetCsv,ComparativeBalanceSheetPdf 
    ComparativeCashFlowStatement,ComparativeCashFlowStatementCsv 
    ComparativeCashFlowStatementPdf,ComparativeIncomeStatement 
    ComparativeIncomeStatementsCsv,ComparativeIncomeStatementsPdf 
    CostCenters,CreateAcctgTransAndEntries creditCardFields,DefaultInvoicePDF 
    DepositSlipPdf,EditAcctgTrans EditAgreement,EditAgreementContents 
    EditAgreementGeographicalApplic,EditAgreementItem 
    EditAgreementItemFacility,EditAgreementItemParty 
    EditAgreementItemProduct,EditAgreementItemSupplierProduct 
    EditAgreementItemTerm,EditAgreementPromoAppl EditAgreementRoles,EditAgreementTerms 
    EditAgreementWorkEffortApplics,EditBillingAccount 
    EditBillingAccountRoles,EditBillingAccountTerms EditBudget,EditBudgetItems 
    EditCostCalcs,EditCreditCardTypeGlAccounts EditCustomTimePeriod,EditDepositSlipAndMembers 
    EditFinAccount,EditFinAccountAuths EditFinAccountReconciliations,EditFinAccountRoles 
    EditFinAccountTrans,EditFinAccountTypeGlAccounts EditFixedAsset,EditFixedAssetIdents 
    EditFixedAssetMaint,EditFixedAssetMaintOrders EditFixedAssetMeters,EditFixedAssetRegistrations 
    EditFixedAssetStdCosts,EditGlAccountCategory EditGlAccountCategoryMember,EditGlobalGlAccount 
    EditGlReconciliation,EditInvoice EditInvoiceApplications,EditInvoiceItems 
    EditInvoiceItemType,EditInvoiceTimeEntries 
    EditOrganizationTaxAuthorityGlAccounts,EditPartyFixedAssetAssignments 
    EditPartyGlAccount,EditPayment EditPaymentApplications,EditPaymentGatewayConfig 
    EditPaymentGatewayConfigType,EditPaymentGroup EditPaymentGroupMember,EditPaymentMethodType 
    EditProductCategoryGlAccounts,EditProductGlAccounts EditTaxAuthority,EditTaxAuthorityAssocs 
    EditTaxAuthorityCategories,EditTaxAuthorityGlAccounts 
    EditTaxAuthorityPartyInfo,EditTaxAuthorityRateProducts EditVarianceReasonGlAccounts,EditVendor 
    ExportInvoiceCsv,FinAccountMain FinAccountPortlets,FinancialSummaryDataPrep 
    FinancialSummaryReportOptions,FindAcctgTrans FindAcctgTransEntries,FindAgreement 
    FindApInvoices,FindApPaymentGroups FindApPayments,FindArInvoices 
    FindArPaymentGroups,FindBillingAccount FindDepositSlips,FindFinAccount 
    FindFinAccountReconciliations,FindFinAccountTrans FindGatewayResponses,FindGlAccountCategory 
    FindGlAccountReconciliation,FindGlAccountReconciliations FindInvoices,FindPaymentGatewayConfig 
    FindPaymentGatewayConfigTypes,FindPaymentGroup FindPayments,FindPurchaseInvoices 
    FindPurchaseInvoicesByDueDate,FindSalesInvoicesByDueDate 
    FindTaxAuthority,FindTaxAuthorityReport FindVendors,FixedAssetChildren 
    FixedAssetGeoLocation,FixedAssetSearchResults FixedAssetTypeGlAccounts,GenericDecorator 
    GenericPaymentGatewayConfigDecorator,GenericTransactionDecorator 
    GlAccountBalanceByCostCenter,GlAccountBalanceByCostCenterPdf GlAccountDetail,GlAccountNavigate 
    GlAccountNrPaymentMethod,GlAccountPurInvoice GlAccountSalInvoice,GlAccountTrialBalance 
    GlAccountTrialBalanceReportPdf,GlAccountTypeDefaults 
    GlAccountTypePaymentType,GlobalGLSettingsDecorator ImportExport,IncomeStatement 
    IncomeStatementListCsv,IncomeStatementListPdf InventoryValuation,InventoryValuationCsv 
    InventoryValuationPdf,InvoiceAcctgTransEntry InvoiceAttributes,InvoiceContactMech 
    InvoiceContent,InvoiceNotes InvoicePDF,InvoiceRoles InvoicesApDueSoon,InvoicesApPastDue 
    InvoicesArDueSoon,InvoicesArPastDue InvoiceStatus,InvoiceTerms 
    ListAcctgTransEntries,ListAgreementGeographicalApplic 
    ListAgreementItemFacilities,ListAgreementItemFacilitiesReport 
    ListAgreementItemParties,ListAgreementItemProducts 
    ListAgreementItemProductsReport,ListAgreementItems 
    ListAgreementItemSupplierProducts,ListAgreementItemSupplierProductsReport 
    ListAgreementItemTerms,ListAgreementPromoAppls ListBankAccount,ListBudgets 
    ListChecksToPrint,ListChecksToSend ListCompanies,ListCustomerInvoices 
    ListFixedAssetMaints,ListFixedAssetProducts ListFixedAssets,ListGlAccountEntries 
    ListGlAccountOrganization,ListGlAccountOrgCsv ListGlAccountOrgPdf,ListGlAccounts 
    ListGlAccountsReport,ListPartyPrefDocTypeTpl ListPayments,ListReports 
    ListSupplierInvoices,ListTaxAuthorityParties ListUnpostedAcctgTrans,LookupAgreement 
    LookupAgreementItem,LookupBillingAccount LookupCustomTimePeriod,LookupFixedAsset 
    LookupGlAccount,LookupGlReconciliation LookupInvoice,LookupPayment 
    LookupPaymentGroupMember,main main-decorator,manualCCTx manualGCTx,ManualTransaction 
    NewAcctgTrans,NewDepositPayment NewDepositSlip,NewIncomingPayment NewInvoice,NewOutgoingPayment 
    NewPayment,NewPurchaseInvoice NewSalesInvoice,NewWithdrawalPayment 
    PartyAccountsSummary,PartyAcctgPreference PaymentAttributes,PaymentGroupOverview 
    PaymentOverview,PaymentsDepositWithdraw PrintCheckPDF,PrintChecks 
    PrintInvoices,SalesInvoiceByProductCategorySummary SendPerEmail,sendPerEmailBody 
    SetupGlJournals,ShortcutApp ShowFixedAssetDepreciation,TimePeriods 
    TransactionTotals,TransactionTotalsCsv TransactionTotalsPdf,TrialBalance 
    TrialBalanceSearchResultsCsv,TrialBalanceSearchResultsPdf ViewFXConversions,ViewGatewayResponse 
    ViewGlReconciliationWithTransaction,ViewInvoice ViewRateAmounts,WorkEffortSummary

### commonext

    ApplicationDecorator,CommonFirstProductDecorator CommonPartyDecorator,CommonSetupDecorator 
    EditCategory,EditFacility EditProdCatalog,EditProduct EditProductStore,EditWebSite 
    EntityExportAll,FirstCustomer InitialSetup,main-decorator 
    nopartyAcctgPreference,SystemInfoNotes SystemInfoStatus,viewprofile

### content

    AddDataResource,AddDataResourceFromContent AddDataResourceText,AddDataResourceUpload 
    AddDataResourceUrl,AddForumMessage AddForumThreadMessage,AddLayout addSubSite,blogArtDecorator 
    BlogContent,blogDecorator BlogMain,BrowseDataResource CMSContentEdit,CMSContentFind 
    CMSSites,commonBlogDecorator commonCmsDecorator,commonContentDecorator 
    commonContentSetupDecorator,commonDataResourceDecorator 
    commonDataResourceSetupDecorator,commonForumDecorator 
    commonLayoutDecorator,CommonSurveyDecorator CommonWebAnalyticsDecorator,CommonWebSiteDecorator 
    CommonWebSitePathAliasDecorator,contentDecorator ContentSearchOptions,ContentSearchResults 
    DataResourceViewLink,EditAddContent EditAddSubContent,EditArticle EditBlog,EditCharacterSet 
    EditContent,EditContentAssoc EditContentAssocPredicate,EditContentAssocType 
    EditContentAttribute,EditContentKeywords EditContentMetaData,EditContentOperation 
    EditContentPurpose,EditContentPurposeOperation EditContentPurposeType,EditContentRole 
    EditContentType,EditContentTypeAttr EditContentWorkEfforts,EditDataCategory 
    EditDataResource,EditDataResourceAttribute EditDataResourceProductFeatures,EditDataResourceRole 
    EditDataResourceText,EditDataResourceType EditDataResourceTypeAttr,EditDataResourceUpload 
    EditDataResourceUrl,EditDocument EditDocumentTree,EditElectronicText 
    EditFileExtension,EditForumMessage EditHtmlText,EditLayout EditLayoutHtml,EditLayoutImage 
    EditLayoutSubContent,EditLayoutText EditLayoutUrl,EditMetaDataPredicate 
    EditMimeType,EditMimeTypeHtmlTemplate EditSurvey,EditSurveyMultiResps 
    EditSurveyQuestions,EditSurveyResponse EditWebAnalyticsConfig,EditWebSite 
    EditWebSiteParties,EditWebSitePathAlias FindContent,findContentSearchResults 
    FindDataResource,findDataResourceSearchResults FindForumGroups,FindForumMessages 
    FindForums,FindForumThreads FindLayout,FindSurvey FindSurveyResponse,FindWebAnalyticsConfigs 
    FindWebSite,fonts ForumGroupPurposes,ForumGroupRoles ListContentTree,listDataResources 
    ListDocument,ListFindSurveySearchResults ListLayout,ListWebSite LookupContent,LookupContentTree 
    LookupDataResource,LookupDetailContentTree LookupListLayout,LookupSubContent 
    LookupSurvey,LookupSurveyResponse main,main-decorator navigateContent,navigateDataResource 
    navigateMenu,responseTreeLine ShortcutApp,ShowContent ShowContentPortlet,UploadImage 
    UserPermissions,ViewArticle ViewContentDetail,ViewSurveyResponses 
    WebSiteAliases,WebSiteAliasesSearchResults WebSiteCMS,WebSiteCMSContent 
    WebSiteCMSEditor,WebSiteCMSMetaInfo WebSiteCMSNav,WebSiteCMSPathAlias 
    WebSiteContactList,WebSiteContent WebSiteSEO

### datamodel


### humanres

    CommonEmploymentDecorator,CommonEmplPositionDecorator 
    CommonMyCommunicationEventDecorator,CommonPartyDecorator 
    CommonPerfReviewDecorator,CommonRecruitmentDecorator 
    CommonTrainingDecorator,CurrentEmploymentData EditAgreementEmploymentAppls,EditApprovalStatus 
    EditEmplLeave,EditEmplLeaveReasonTypes EditEmplLeaveStatus,EditEmplLeaveTypes 
    EditEmployeeEmploymentApps,EditEmployeeLeaves EditEmployeePerformanceNotes,EditEmployeeQuals 
    EditEmployeeResumes,EditEmployeeSkills EditEmployeeTrainings,EditEmployment 
    EditEmploymentApps,EditEmplPosition 
    EditEmplPositionFulfillments,EditEmplPositionReportingStructs 
    EditEmplPositionResponsibilities,EditEmplPositionTypeRates 
    EditEmplPositionTypes,EditInternalJobPosting EditInternalOrgFtl,EditInternalOrgOnlyForm 
    EditJobInterview,EditJobInterviewType EditJobRequisition,EditPartyBenefits 
    EditPartyQuals,EditPartyResume EditPartySkills,EditPayGrade 
    EditPayHistory,EditPayrollPreferences EditPerformanceNotes,EditPerfReviewItems 
    EditPerfReviews,EditResponsibilityTypes EditSalarySteps,EditSkillTypes 
    EditTerminationReasons,EditTerminationTypes EditTrainingApprovals,EditTrainingTypes 
    EditUnemploymentClaims,EditValidResponsibility EmployeeDecorator,EmployeeProfile 
    EmplPositionFulfilmentView,EmplPositionReportedToView 
    EmplPositionReportsToView,EmplPositionResponsibilityView EmplPositionView,FindApprovals 
    FindEmplLeaves,FindEmployee FindEmploymentApps,FindEmployments 
    FindEmplPositions,FindEmplPositionTypes FindInternalJobPosting,FindJobInterview 
    FindJobRequisitions,FindLeaveApprovals FindPartyQuals,FindPartyResumes 
    FindPartySkills,FindPayGrades FindPerfReviews,FindRelocation 
    FindTrainingApprovals,FindTrainingStatus GlobalHRSettingsDecorator,ListEmployments 
    ListEmplPositionsParty,ListPayHistories ListTrainingStatus,ListValidResponsibilities 
    LookupAgreementEmploymentAppl,LookupBudget LookupBudgetItem,LookupEmploymentApp 
    LookupEmplPosition,LookupJobInterview LookupJobRequisition,LookupPartyResume 
    LookupPayGrade,LookupPayRollPreference LookupPerfReview,LookupSalaryStep 
    LookupTerminationReason,LookupTraining LookupUnemploymentClaim,Main main-decorator,MyLeaveList 
    MyTrainings,NewEmployee NewEmploymentApp,NewPartyQual NewPartySkill,none 
    OrgTree,PartyGroupTreeLine PartyPersonTreeLine,PayrollHistory 
    PublicHoliday,RemoveInternalOrgFtl ShortcutApp,TrainingCalendar 
    trainingCalendarDetail,TrainingCalendarWithDecorator trainingsList

### manufacturing

    AddCalendarExceptionDay,AddCalendarExceptionWeek BomSimulation,CommonBomDecorator 
    CommonCalendarDecorator,CommonJobshopDecorator CommonManufacturingDecorator,CommonMrpDecorator 
    CommonRoutingDecorator,CommonRoutingTaskDecorator ConfirmForceIssueModal,CreateProductionRun 
    CreateProductionRunParty,CuttingListReport EditCalendar,EditCalendarExceptionDay 
    EditCalendarExceptionWeek,EditCalendarWeek EditCostCalcs,EditProductBom 
    EditProductionRun,EditProductManufacturingRules EditRouting,EditRoutingProductLink 
    EditRoutingTask,EditRoutingTaskAssoc EditRoutingTaskCosts,EditRoutingTaskFixedAssets 
    EditRoutingTaskParties,EditRoutingTaskProduct EditRoutingTaskSkills,EditWorkEffortInvRes 
    FindBom,FindCalendar FindMrpPlannedEvents,FindProductionRun FindRouting,FindRoutingTask 
    LinkProductionRun,ListCalendarExceptionDay ListCalendarExceptionWeek,ListCalendarWeek 
    ListRoutingTaskProducts,ListRoutingTaskRoutings LookupInventoryItem,LookupRouting 
    LookupRoutingTask,main-decorator ManufacturingReports,MrpExecution 
    MRPPRunsComponentsByFeature,MRPPRunsProductsByFeature 
    PackageContentsAndOrder,ProductionRunActualComponents 
    ProductionRunAssocs,ProductionRunComponents ProductionRunContent,ProductionRunCosts 
    ProductionRunDeclaration,ProductionRunFixedAssets ProductionRunParties,ProductionRunPartyAssocs 
    ProductionRunPdf,ProductionRunTaskCosts ProductionRunTaskPartyAssocs,ProductionRunTasks 
    ProductionRunTasksInfo,PRunsInfoAndOrder PRunsProductsAndOrder,PRunsProductsStacks 
    RunMrpGo,ShipmentLabel ShipmentPlanStockReport,ShipmentWorkEffortTasks 
    ShortcutApp,SPPRunsComponentsByFeature SPPRunsProductsByFeature,UpdateCalendarExceptionDay 
    UpdateCalendarExceptionWeek,WorkWithShipmentPlans

### marketing

        <screen name= "EmailStatusReport">,    <screen name= "PartyStatusReport"> 
    AddRelatedCompany,Campaigns CloneLead,CommEvents 
    CommonAccountDecorator,CommonCommunicationEventDecorator 
    CommonContactDecorator,CommonContactListDecorator 
    CommonDataSourceDecorator,CommonEventDecorator 
    CommonLeadDecorator,CommonMarketingCampaignDecorator 
    CommonMarketReportDecorator,CommonOpportunityDecorator 
    CommonPartyDecorator,CommonSalesForecastDecorator 
    CommonSegmentGroupDecorator,CommonTrackingCodeDecorator 
    CommonWorkEffortDecorator,ContactMechTypeOnly ConvertLead,DefaultOptOutScreen 
    EditContactList,EditContactListCommEvent EditContactListParty,EditDataSource 
    EditDataSourceType,EditEvent EditMarketingCampaign,EditMarketingCampaignRoles 
    EditSalesForecast,EditSalesForecastDetail EditSalesOpportunity,EditSegmentGroup 
    EditTrackingCode,EditTrackingCodeOrder EditTrackingCodeType,EditTrackingCodeVisit 
    FindAccounts,FindContactListCommEvents FindContactListParties,FindContactLists 
    FindContacts,FindImportContactListParties FindLeads,FindSalesForecast 
    FindSalesOpportunity,FindSegmentGroup FindTrackingCode,FindTrackingCodeOrders 
    FindTrackingCodeVisits,LeadPartyDataSource LeadProfile,leftbar 
    ListContactListCommEvents,ListContactListParties ListContactLists,ListDataSource 
    ListDataSourceType,ListMarketingCampaign ListMarketingCampaignRoles,listSegmentGroupClass 
    listSegmentGroupGeo,listSegmentGroupRole ListTrackingCode,ListTrackingCodeOrders 
    ListTrackingCodeType,ListTrackingCodeVisits LookupAccountLeads,LookupAccounts 
    LookupContactList,LookupLeads LookupPreferredContactMech,LookupSalesForecast 
    LookupSegmentGroup,LookupTrackingCode LookupTrackingCodeType,LookupVisit main,Main 
    main-decorator,MarketingCampaignReport MarketingReportList,MergeContacts MergeLeads,NewAccount 
    NewContact,NewContactFromVCard NewLead,NewLeadFromVCard OpportunityCommEvent,OptOutResponse 
    PreviewContactListCommEvent,ShortcutApp TrackingCodeReport,ViewAccountLeadProfile 
    ViewAccountProfile,ViewContactProfile ViewLeadProfile,ViewPartiesCreatedByVCard 
    ViewProfile,ViewSalesOpportunity WebSiteContactList

### order

    AddFromShoppingList,AddGiftCertificate AddNoteCustRequestNotification,AddOrderAttachments 
    AddQuoteWorkEffort,advancedsearch 
    ApprovedProductRequirements,ApprovedProductRequirementsByVendor 
    ApprovedProductRequirementsByVendorReport,ApprovedProductRequirementsReport 
    ApproveRequirements,BillSettings breadcrumbs,category category-include,categorydetail 
    CheckInits,checkoutpayment checkoutshippingaddress,choosecatalog 
    CommonAllocationPlanDecorator,CommonOrderCatalogDecorator 
    CommonOrderCheckoutDecorator,CommonOrderEntryDecorator 
    CommonOrderHeaderDecorator,CommonOrderReturnDecorator 
    CommonOrderSetupDecorator,CommonOrderTaskDecorator 
    CommonOrderViewDecorator,CommonPartyDecorator CommonPopUpDecorator,CommonQuoteDecorator 
    CommonRequestDecorator,CommonRequirementDecorator CommonRequirementsDecorator,CompanyLogo 
    compareProducts,compareproductslist CompletedCustRequestNotification,configproductdetail 
    ConfirmOrder,CreateAllocationPlan CreateCustRequestNotification,CreateNewOrderMessage 
    CreateQuoteAndQuoteItemForRequest,customertaxinfo CustSettings,DefaultOrderPDF 
    DefaultQuotePDF,EditAllocationPlan EditCustRequestContent,EditOrderHeader 
    EditQuote,EditQuoteAdjustment EditQuoteAttribute,EditQuoteCoefficient 
    EditQuoteItem,EditQuoteItemForRequest EditQuoteNote,EditQuoteReportMail 
    EditQuoteRole,EditQuoteTerm EditQuoteTermItem,EditQuoteWorkEffort 
    EditRequest,EditRequestCustomer EditRequestItem,EditRequestItemWorkEfforts 
    EditRequirement,EditRequirementRole EditShipAddress,FindAllocationPlan 
    FindQuote,FindQuoteForCart FindRequest,FindRequirements 
    IncomingCustRequests,inlineProductDetail keywordsearch,keywordsearchbox leftbar,leftbarCatalog 
    ListCustomerOrders,ListCustRequests ListCustReturns,ListOrderHeaders 
    ListOrderTerms,ListPurchaseOrders ListQuoteAdjustments,ListQuoteAttributes 
    ListQuoteCoefficients,ListQuoteInfo ListQuoteItems,ListQuoteNoteInfo 
    ListQuoteNotes,ListQuoteRoles ListQuoteTerms,ListQuoteWorkEfforts 
    ListRequirementCustRequests,ListRequirementOrders ListRequirementRoles,ListSalesOrders 
    LookupAssociatedProducts,LookupBulkAddProducts 
    LookupBulkAddSupplierProducts,LookupBulkAddSupplierProductsInApprovedOrder 
    LookupCustRequest,LookupCustRequestItem LookupOrderHeader,LookupOrderHeaderAndShipInfo 
    LookupPurchaseOrderHeaderAndShipInfo,LookupQuote LookupQuoteItem,LookupRequirement 
    LookupReturnHeader,LookupShoppingList Main,main-decorator ManageQuotePrices,minicart 
    OpenOrderItemsReport,OptionSettings OrderAgreements,ordercontactinfo 
    OrderConversations,OrderDeliveryScheduleInfo OrderFindOrder,OrderFindReturn 
    orderHeader,orderHeaderInfo OrderHeaderListView,OrderHeaderView OrderHistory,orderinfo 
    OrderItemEdit,OrderNewNote orderpaymentinfo,OrderPaymentSetup 
    OrderPDF,OrderPurchaseReportOptions OrderPurchaseReportPayment,OrderPurchaseReportProduct 
    OrderQuantityHistory,OrderQuickReturn OrderReceivePayment,OrderReportOpenOrderItems 
    OrderReportPurchasesByOrganization,OrderReportPurchasesByPaymentMethod 
    OrderReportPurchasesByProduct,OrderReportSalesByStore OrderReturnHeader,OrderReturnHistory 
    OrderReturnItems,OrderReturnList OrderRoleInfo,OrderSalesReps 
    OrderShipmentMethodHistory,ordershippinginfo orderShortcuts,OrderStats OrderTaskList,orderterms 
    OrderTerms,OrderUnitPriceHistory product,productdetail productsummary,ProductUomDropDownOnly 
    productvariantjs,projectAssoOrder promotion,promoUseDetailsInline 
    PurchasesByOrganizationReport,quickadd quickaddsummary,quickFinalizeOrder quoteDate,quoteInfo 
    QuoteNewNote,QuoteReport quoteRoles,ReceivedQuantityHistory RequestItemNotes,RequestItemQuotes 
    RequestItemRequirements,RequestItems RequestRoles,RequirementsForSupplier 
    ReturnPDF,ReturnPriceHistory ReturnQuantityHistory,ReturnReasonHistory 
    ReturnStatusHistory,ReturnTypeHistory SalesByStoreReport,SendOrderCompletion 
    SendOrderConfirmation,SendPOEmail SetAdditionalParty,SetItemShipGroups 
    ShipGroupsPDF,ShipSettings ShortcutApp,showAllPromotions ShowCart,showPromotionDetails 
    sidedeepcategory,splitship survey,ViewAllocationPlan ViewCustRequest,ViewImage 
    ViewQuote,ViewQuoteInfo ViewQuoteItemInfo,ViewQuoteProfit ViewQuoteSimple,ViewQuoteTemplate 
    ViewRequest,ViewRequestItemInfo ViewShoppingLists

### party

    AccountActivatedNotification,AddCheckAccount AddCommContent,AddCommEventWorkEffort 
    AddPartyNote,AddPartySecondaryRoles AddressMatchMap,Attributes AvsSettings,Cart 
    ChangePersonalInfoNotification,commEvent 
    CommonCommunicationEventDecorator,CommonMyCommunicationEventDecorator 
    CommonOpportunityDecorator,CommonPartyDecorator 
    CommonPartyInvitationDecorator,CommonRequestDecorator commOverview,Contact 
    contactsAndAccounts,Content ContentList,ConversationThread 
    CreateAccountRelatedContact,CreatePartyNotification CreatePartyRelatedAccount,CreateUserLogin 
    CreateUserNotification,editavsoverride EditBillingAccount,editCarrierAccount 
    EditCommContent,editCommEvent EditCommEventWorkEffort,EditCommunicationEvent 
    editcontactmech,editcreditcard editeftaccount,EditGeoLocation editgiftcard,EditPartyAttribute 
    EditPartyClassificationGroup,EditPartyClassificationGroupParties 
    EditPartyClassifications,EditPartyContent EditPartyContents,EditPartyGroup 
    EditPartyInvitation,EditPartyInvitationsGroupAssocs 
    EditPartyInvitationsRoleAssocs,EditPartyRates EditPartyRelationships,EditPartyResumes 
    EditPartyTaxAuthInfos,EditPerson EditRequestFromCommEvent,editShoppingList 
    EditUserLogin,EditUserLoginSecurityGroups FinAccounts,FindAddressMatch 
    FindCommunicationByOrder,FindCommunicationByReturn FindCommunicationEvents,findparty 
    FindPartyClassificationGroups,FindPartyInvitations FindVisits,GetPartyGeoLocation 
    ImportExport,linkparty ListCommContent,ListCommWorkEfforts 
    ListLoggedInUsers,ListLookupContactList listMyCommEvents,ListPartyCommEvents 
    ListPartyContactLists,ListPartyIdentifications ListPartyMaritalStatus,ListUnknownPartyComms 
    LoggedInUsersScreen,LookupAccount LookupCommEvent,LookupContact 
    LookupContactMech,LookupCustomerName LookupCustomerNameForSalesRep,LookupEmployee 
    LookupGovernmentAgency,LookupInternalOrganization LookupLead,LookupPartyAndUserLoginAndPerson 
    LookupPartyClassificationGroup,LookupPartyEmail LookupPartyGroup,LookupPartyName 
    LookupPerson,LookupSupplier LookupTaxAuthority,LookupUserLoginAndPartyDetails 
    LoyaltyPoints,main-decorator MyCommunicationEvents,mytasks NewCustomer,NewEmployee 
    NewProspect,Notes Party,PartyCommunicationEvents PartyExportCsv,PartyFinancialHistory 
    PartyGeoLocation,PartyIdentifications PartyInvitations,partyRelAccounts 
    partyRelContacts,PartySalesOpportunities PaymentMethodDecorator,PaymentMethods 
    PendingCommunications,postalAddressHtmlFormatter postalAddressPdfFormatter,Preferences 
    ProductStores,SerializedInventory ShipperAccount,ShortcutApp Subscriptions,UpdateCommOrders 
    UpdateCommProducts,UpdateCommPurposes UpdateCommReturns,UpdateCommRoles 
    UserLogin,VerifyEmailAddressNotification ViewCommunicationEvent,ViewProductStoreRoles 
    viewprofile,viewroles ViewSegmentRoles,viewvendor visitdetail,Visits

### product

    AddItemsFromInventory,AddItemsFromOrder AddProductConfigItemContentAssoc,advancedsearch 
    ApplyFeaturesFromCategory,BestSellingProducts categorytree,ChooseTopCategory 
    CommonCatalogDecorator,CommonCategoryDecorator CommonConfigDecorator,CommonFacilityDecorator 
    CommonFacilityGroupDecorator,CommonFeatureDecorator 
    CommonFindDecorator,CommonInventoryItemLabelDecorator 
    CommonInventoryItemLabelsDecorator,CommonPriceDecorator 
    CommonProductDecorator,CommonProductStoreDecorator 
    CommonProductStoreGroupDecorator,CommonPromoDecorator 
    CommonReviewDecorator,CommonShipmentDecorator 
    CommonShipmentMainDecorator,CommonShippingDecorator 
    CommonSubscriptionDecorator,CommonSubscriptionResourceDecorator 
    CommonThesaurusDecorator,configComponent configOptions,CreateFeature 
    CreateFeaturePrice,CreateProductFeature 
    CreateProductInCategoryCheckExisting,createProductInCategoryStart 
    CreateSalesAgreement,CreateVirtualWithVariantsForm 
    CreateVirtualWithVariantsFormInclude,EditCategory EditCategoryAttributes,EditCategoryContent 
    EditCategoryContentContent,EditCategoryFeatureCats EditCategoryParties,EditCategoryProdCatalogs 
    EditCategoryProducts,EditCategoryRollup EditCategorySection,EditCommunicationEvent 
    EditContactMech,EditFacility EditFacilityAgreements,EditFacilityContent 
    EditFacilityGeoPoint,EditFacilityGroup EditFacilityGroupMembers,EditFacilityGroupRoles 
    EditFacilityGroupRollup,EditFacilityGroups EditFacilityInventoryItems,EditFacilityLocation 
    EditFacilityParties,EditFeature EditFeatureCategories,EditFeatureCategoryFeatures 
    EditFeatureGroupAppls,EditFeatureGroups EditFeatureInterAction,EditFeatureInterActions 
    EditFeatureType,EditFeatureTypes EditInventoryItem,EditInventoryItemLabel 
    EditInventoryItemLabelAppls,EditInventoryItemLabels 
    EditInventoryItemLabelTypes,EditKeywordThesaurus EditProdCatalog,EditProdCatalogCategories 
    EditProdCatalogParties,EditProdCatalogSection EditProdCatalogStores,EditProduct 
    EditProductAssetUsage,EditProductAssoc EditProductAttributes,EditProductCategories 
    EditProductCategoryLinks,EditProductCommunicationEvents 
    EditProductConfigItem,EditProductConfigItemContent 
    EditProductConfigItemContentContent,EditProductConfigOptions 
    EditProductConfigs,EditProductContent EditProductContentContent,EditProductCosts 
    EditProductFacilities,EditProductFacilityLocations EditProductFeatures,EditProductGeos 
    EditProductGlAccounts,EditProductGoodIdentifications 
    EditProductGroupOrder,EditProductInventoryItems EditProductKeyword,EditProductMaints 
    EditProductMeters,EditProductParties EditProductPaymentMethodTypes,EditProductPriceRules 
    EditProductPrices,EditProductPromo EditProductPromoCode,EditProductPromoContent 
    EditProductPromoRules,EditProductPromoStores EditProductQuickAdmin,EditProductStore 
    EditProductStoreCatalogs,EditProductStoreEmails 
    EditProductStoreFacility,EditProductStoreFinAccountSettings 
    EditProductStoreGroup,EditProductStoreGroupAndAssoc 
    EditProductStoreKeywordOvrd,EditProductStorePaySetup 
    EditProductStorePromos,EditProductStoreShipmentCostEstimates 
    EditProductStoreShipSetup,EditProductStoreSurveys 
    EditProductStoreVendorPayments,EditProductStoreVendorShipments 
    EditProductStoreWebSites,EditProductSubscriptionResources EditProductTag,EditProductWorkEfforts 
    EditShipment,EditShipmentGatewayConfig EditShipmentGatewayConfigType,EditShipmentItems 
    EditShipmentPackages,EditShipmentPlan EditShipmentRouteSegments,EditSubscription 
    EditSubscriptionAttributes,EditSubscriptionCommEvent 
    EditSubscriptionResource,EditSubscriptionResourceProducts EditSupplierProduct,EditVendorProduct 
    exportproducts,FacilityChildren FacilityLocationGeoLocation,FacilitySearchResults 
    FastLoadCache,FindCatalog FindCategory,FindFacility FindFacilityGroup,FindFacilityLocation 
    FindFacilityPhysicalInventory,FindFacilityTransfers FindInventoryItemLabels,FindProduct 
    FindProductById,FindProductConfigItems FindProductPriceRule,FindProductPromo 
    FindProductPromoCode,FindProductStore FindProductStoreRoles,FindReviews 
    FindShipment,FindShipmentGatewayConfig FindShipmentGatewayConfigTypes,FindSubscription 
    FindSubscriptionResource,GenericShipmentGatewayConfigDecorator ImageApprove,ImageCropping 
    ImageFrames,ImageGallery ImageManage,Imagemanagement 
    ImageManagementDecorator,ImageRecentlyApproved ImageRejected,ImageReplace 
    ImageResize,ImageRotating ImageShare,ImageUpload InventoryAverageCosts,InventoryItemGrandTotals 
    InventoryItemTotals,InventoryItemTotalsExport InventoryReports,keywordsearch 
    keywordsearchbox,leftbar ListCarrierShipmentMethods,ListFeaturePrice 
    ListImageGallery,ListImageManage ListImageRecentlyApproved,ListImageReplace 
    listMiniproduct,ListParentProductStoreGroup ListPeopleApproved,ListPeopleRejected 
    ListProductStoreFacility,ListQuantityBreaks ListShipmentMethodTypes,ListShipmentTimeEstimates 
    LookupCostComponentCalc,LookupFacility LookupFacilityLocation,LookupImage 
    LookupImageFrame,LookupInventoryItem LookupProduct,LookupProductAndPrice 
    LookupProductCategory,LookupProductFeature LookupProductInventoryLocation,LookupProductStore 
    LookupShipment,LookupSupplierProduct LookupVariantProduct,LookupVirtualProduct 
    main,main-decorator miniproductlist,newFixedAssetProduct PackingSlip,PackOrder 
    PicklistManage,PicklistOptions PicklistReport,PickMoveStock 
    PickMoveStockSimple,PrepareAddProductConfigItemContentAssoc PrintPickSheets,ProductBarCode 
    ProductConfigItemArticle,ProductPriceHistory ProductStoreFacilities,ProductStoreGroupTree 
    QuickAddProductFeatures,QuickAddVariants QuickShipOrder,ReceiveInventory 
    ReceiveInventoryAgainstPurchaseOrder,ReceiveReturn 
    ReviewOrdersNotPickedOrPacked,SearchInventoryItemsByLabels ShipmentBarCode,ShipmentManifest 
    ShortcutApp,showFixedAssetProduct TransferInventoryItem,UpdatedInventoryItemStatus 
    VerifyPick,ViewContactMechs ViewFacilityInventoryByProduct,ViewFacilityInventoryByProductReport 
    ViewFacilityInventoryByProductSimple,ViewFacilityInventoryItemsDetails 
    ViewImage,ViewInventoryItemDetail ViewPreviewImage,ViewProductAgreements 
    ViewProductGroupOrder,ViewProductManufacturing ViewProductOrder,ViewProductStoreSegments 
    ViewShipment,ViewShipmentReceipts WeightPackageOnly

### securityext

    PasswordEmail

### workeffort

    AddWorkEffort,AddWorkEffortAndAssoc AddWorkEffortAssoc,Calendar 
    calendarEventContent,CalendarOnly CalendarWithDecorator,ChildWorkEfforts 
    CommonCalendarDecorator,CommonEmbeddedDecorator 
    CommonTimesheetDecorator,CommonWorkEffortDecorator EditAgreementWorkEffortApplics,EditICalendar 
    EditICalendarData,EditTimesheet EditTimesheetEntries,EditTimesheetRoles 
    EditWorkEffort,EditWorkEffortAndAssoc EditWorkEffortAssoc,EditWorkEffortContactMechs 
    EditWorkEffortContents,EditWorkEffortGoodStandards EditWorkEffortKeywords,EditWorkEffortNotes 
    EditWorkEffortRates,EditWorkEffortReviews EditWorkEffortTimeEntries,eventDetail 
    FilteredListDecorator,FindICalendars FindTimesheet,FindWorkEffort 
    ICalendarChildren,iCalendarDecorator ICalendarFixedAssets,ICalendarHelp 
    ICalendarParties,ListWorkEffortAttributes ListWorkEffortCommEvents,ListWorkEffortEventReminders 
    ListWorkEffortFixedAssetAssigns,ListWorkEffortOrderHeaders 
    ListWorkEffortPartyAssigns,ListWorkEffortQuotes 
    ListWorkEffortRequests,ListWorkEffortRequirements ListWorkEffortShopLists,login-decorator 
    LookupTimesheet,LookupWorkEffort LookupWorkEffortShort,main main-decorator,mytasks 
    MyTimesheets,RequestList ShortcutApp,UserJobs 
    WorkEffortEventReminderEmail,WorkEffortSearchOptions WorkEffortSearchResults,WorkEffortSummary 
    WorkEffortSummaryEmbedded

