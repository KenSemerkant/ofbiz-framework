# product-svc schema mapping

169 entities in `monolith/applications/datamodel/entitydef/product-entitymodel.xml`
(the story's ≈192 also counts shipment/facility entities now owned by
inventory-svc, P3-F2).

## Live slice (owned by product-svc)

| OFBiz entity | product-svc home | Notes |
|---|---|---|
| Product | `product.domain.Product` (products table) | type + names + memberships |
| ProductCategory | `product.domain.Category` (product_categories) | primary-parent tree |
| ProductCategoryRollup | `primaryParentCategoryId` on Category | single primary parent per node; dated multi-parent rollups not modeled until a consumer needs them |
| ProductCategoryMember | categories JSON on products | undated membership set (fromDate/thruDate dropped); normalize if a dated-membership query appears |
| ProductAssoc | assocs JSON on products | productIdTo + assocTypeId + from/thru |

## Historical archive

The remaining ~164 entities (features, subscriptions, configs, supplier/product
attributes, good identifications, etc.) are **not migrated**. They remain owned
by the monolith until their owning phase pulls them. At Phase 7 (monolith
retirement) untouched product tables are archived per ADR-012 deletion plan;
nothing is dropped before then.
