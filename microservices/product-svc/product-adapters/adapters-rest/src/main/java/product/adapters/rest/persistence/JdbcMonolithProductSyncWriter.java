package product.adapters.rest.persistence;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import product.application.MonolithSyncWriter;
import product.domain.ProductAssoc;
import product.domain.ProductPrice;

/**
 * P3-GATE: reverse-sync into the monolith Postgres (own datasource — no
 * cross-DB joins, this is the ADR-012 anti-corruption write-back).
 * Conflict policy: new-service wins — unconditional upserts; memberships,
 * prices and assocs are replace-all so rows dropped in the new service
 * disappear from the monolith.
 */
public class JdbcMonolithProductSyncWriter implements MonolithSyncWriter {

    private final JdbcTemplate monolith;

    public JdbcMonolithProductSyncWriter(@Qualifier("monolithJdbc") JdbcTemplate monolith) {
        this.monolith = monolith;
    }

    @Override
    public void syncProduct(String productId, String productTypeId, String internalName,
            String productName, Set<String> categoryIds, List<ProductPrice> prices,
            List<ProductAssoc> assocs) {
        monolith.update("""
            INSERT INTO "PRODUCT" ("PRODUCT_ID","PRODUCT_TYPE_ID","INTERNAL_NAME",
                                   "PRODUCT_NAME","LAST_UPDATED_STAMP")
            VALUES (?,?,?,?,now())
            ON CONFLICT ("PRODUCT_ID") DO UPDATE
              SET "PRODUCT_TYPE_ID" = EXCLUDED."PRODUCT_TYPE_ID",
                  "INTERNAL_NAME"   = EXCLUDED."INTERNAL_NAME",
                  "PRODUCT_NAME"    = EXCLUDED."PRODUCT_NAME",
                  "LAST_UPDATED_STAMP" = now()
            """, productId, productTypeId, internalName, productName);

        setSyncMembers(productId, categoryIds);
        setSyncPrices(productId, prices);
        setSyncAssocs(productId, assocs);
    }

    @Override
    public void syncCategory(String productCategoryId, String categoryName) {
        monolith.update("""
            INSERT INTO "PRODUCT_CATEGORY" ("PRODUCT_CATEGORY_ID","CATEGORY_NAME")
            VALUES (?,?)
            ON CONFLICT ("PRODUCT_CATEGORY_ID") DO UPDATE
              SET "CATEGORY_NAME" = EXCLUDED."CATEGORY_NAME"
            """, productCategoryId, categoryName);
    }

    @Override
    public void deleteProduct(String productId) {
        monolith.update("DELETE FROM \"PRODUCT_CATEGORY_MEMBER\" WHERE \"PRODUCT_ID\" = ?",
            productId);
        monolith.update("DELETE FROM \"PRODUCT_PRICE\" WHERE \"PRODUCT_ID\" = ?", productId);
        monolith.update("DELETE FROM \"PRODUCT_ASSOC\" WHERE \"PRODUCT_ID\" = ?", productId);
        monolith.update("DELETE FROM \"PRODUCT\" WHERE \"PRODUCT_ID\" = ?", productId);
    }

    private void setSyncMembers(String productId, Set<String> categoryIds) {
        monolith.update("DELETE FROM \"PRODUCT_CATEGORY_MEMBER\" WHERE \"PRODUCT_ID\" = ?",
            productId);
        for (String categoryId : categoryIds) {
            monolith.update("""
                INSERT INTO "PRODUCT_CATEGORY_MEMBER"
                    ("PRODUCT_ID","PRODUCT_CATEGORY_ID","FROM_DATE")
                VALUES (?,?, '0')
                ON CONFLICT ("PRODUCT_ID","PRODUCT_CATEGORY_ID") DO NOTHING
                """, productId, categoryId);
        }
    }

    private void setSyncPrices(String productId, List<ProductPrice> prices) {
        monolith.update("DELETE FROM \"PRODUCT_PRICE\" WHERE \"PRODUCT_ID\" = ?", productId);
        for (ProductPrice price : prices) {
            monolith.update("""
                INSERT INTO "PRODUCT_PRICE" ("PRODUCT_ID","PRODUCT_PRICE_TYPE_ID",
                                             "CURRENCY_UOM_ID","FROM_DATE","PRICE")
                VALUES (?,?,?,?,?)
                ON CONFLICT ("PRODUCT_ID","PRODUCT_PRICE_TYPE_ID","CURRENCY_UOM_ID","FROM_DATE")
                  DO UPDATE SET "PRICE" = EXCLUDED."PRICE"
                """, productId, price.productPriceTypeId(), price.currencyUomId(),
                String.valueOf(price.fromDate() == null ? 0 : price.fromDate().toEpochMilli()),
                price.price().toPlainString());
        }
    }

    private void setSyncAssocs(String productId, List<ProductAssoc> assocs) {
        monolith.update("DELETE FROM \"PRODUCT_ASSOC\" WHERE \"PRODUCT_ID\" = ?", productId);
        for (ProductAssoc assoc : assocs) {
            monolith.update("""
                INSERT INTO "PRODUCT_ASSOC" ("PRODUCT_ID","PRODUCT_ID_TO",
                                             "PRODUCT_ASSOC_TYPE_ID","FROM_DATE")
                VALUES (?,?,?,?)
                ON CONFLICT ("PRODUCT_ID","PRODUCT_ID_TO","PRODUCT_ASSOC_TYPE_ID","FROM_DATE")
                  DO NOTHING
                """, productId, assoc.productIdTo(), assoc.productAssocTypeId(),
                String.valueOf(assoc.fromDate() == null ? 0 : assoc.fromDate().toEpochMilli()));
        }
    }
}
