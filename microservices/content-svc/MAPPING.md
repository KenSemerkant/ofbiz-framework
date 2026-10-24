# content-svc schema mapping

Split of `monolith/applications/datamodel/entitydef/content-entitymodel.xml`
(64 entities defined there) per FUTURESTATE-LOWLEVEL.md. content-svc owns
the **content tree + object storage** slice (P6-T4); object bytes live in
S3-API storage per ADR-008 (Garage locally), **never** in DataResource
BLOB columns.

## Live slice (migrated in P6)

| OFBiz entity | content-svc home | Notes |
|---|---|---|
| Content | `content.domain.Content` (contents) | tree node; contentId, contentName, statusId (DRAFT→PUBLISHED) |
| ContentAssoc (PARENT_CHILD) | `contents.parent_content_id` | the tree is parent pointers — the only assoc type the live slice uses |
| DataResource (object payload) | S3 key `contents/{id}` in `content.s3.bucket` | ADR-008: bytes in the object store; contents row keeps contentType + sizeBytes, not the BLOB |
| ElectronicText / ImageDataResource / OtherDataResource / VideoDataResource / AudioDataResource | S3 object | superseded — one storage path for every media type |
| ContentType / DataResourceType | `content.domain.ContentType` enum (FOLDER\|DOCUMENT) | vocabulary collapsed to the two kinds the slice needs |
| StatusItem (content statuses) | `content.domain.ContentStatus` enum (DRAFT\|PUBLISHED) | forward-only publish replaces the monolith status-flow machinery |

## Deferred

Not migrated; remain owned by the monolith until Phase 7 (ADR-012
archive; nothing dropped before then).

| OFBiz entity family | Notes |
|---|---|
| ContentAssoc (non-tree types), ContentAssocType/Predicate | peer/alternate assocs — add as API/relation when a consumer appears |
| ContentRevision, ContentRevisionItem, ContentApproval | versioning/approval workflow |
| ContentRole, ContentPurpose(+Operation/Type), ContentAttribute, ContentMetaData, ContentKeyword | metadata/permission overlay |
| ContentSearchConstraint, ContentSearchResult | query machinery — superseded by search-svc |
| DataResource metadata (DataResourceAttribute/Role/Purpose/MetaData, DataSource, DataCategory, DataTemplateType) | object metadata overlay |
| MimeType, CharacterSet, FileExtension, MimeTypeHtmlTemplate | vocabularies |
| Document(+Type/Attribute), Survey family, WebSite/CMS family (WebSite*, WebPreference*, WebAnalytics*) | CMS/survey domains beyond the P6 slice |

## Full definition list (64, content-entitymodel.xml)

AudioDataResource CharacterSet Content ContentApproval ContentAssoc
ContentAssocPredicate ContentAssocType ContentAttribute ContentKeyword
ContentMetaData ContentOperation ContentPurpose ContentPurposeOperation
ContentPurposeType ContentRevision ContentRevisionItem ContentRole
ContentSearchConstraint ContentSearchResult ContentType ContentTypeAttr
DataCategory DataResource DataResourceAttribute DataResourceMetaData
DataResourcePurpose DataResourceRole DataResourceType DataResourceTypeAttr
DataTemplateType Document DocumentAttribute DocumentType DocumentTypeAttr
ElectronicText FileExtension ImageDataResource MetaDataPredicate MimeType
MimeTypeHtmlTemplate OtherDataResource Survey SurveyApplType
SurveyMultiResp SurveyMultiRespColumn SurveyPage SurveyQuestion
SurveyQuestionAppl SurveyQuestionCategory SurveyQuestionOption
SurveyQuestionType SurveyResponse SurveyResponseAnswer SurveyTrigger
VideoDataResource WebAnalyticsConfig WebAnalyticsType WebPreferenceType
WebSiteContent WebSiteContentType WebSitePathAlias WebSitePublishPoint
WebSiteRole WebUserPreference
