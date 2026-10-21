package party.adapters.rest.persistence;

import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import party.application.MonolithSyncWriter;

/**
 * P2-F2-S3: reverse-sync into the monolith Postgres (own datasource — no
 * cross-DB joins, this is the ADR-012 anti-corruption write-back).
 * Conflict policy: new-service wins — unconditional upserts; the role set
 * is replace-all so revoked roles disappear from the monolith.
 */
public class JdbcMonolithSyncWriter implements MonolithSyncWriter {

    private final JdbcTemplate monolith;

    public JdbcMonolithSyncWriter(@Qualifier("monolithJdbc") JdbcTemplate monolith) {
        this.monolith = monolith;
    }

    @Override
    public void syncParty(String partyId, String partyTypeId, String firstName,
            String lastName, String groupName, Set<String> roles) {
        monolith.update("""
            INSERT INTO "PARTY" ("PARTY_ID","PARTY_TYPE_ID","LAST_UPDATED_STAMP")
            VALUES (?,?,now())
            ON CONFLICT ("PARTY_ID") DO UPDATE
              SET "PARTY_TYPE_ID" = EXCLUDED."PARTY_TYPE_ID",
                  "LAST_UPDATED_STAMP" = now()
            """, partyId, partyTypeId);

        if ("PERSON".equals(partyTypeId)) {
            monolith.update("""
                INSERT INTO "PERSON" ("PARTY_ID","FIRST_NAME","LAST_NAME")
                VALUES (?,?,?)
                ON CONFLICT ("PARTY_ID") DO UPDATE
                  SET "FIRST_NAME" = EXCLUDED."FIRST_NAME",
                      "LAST_NAME" = EXCLUDED."LAST_NAME"
                """, partyId, firstName, lastName);
        } else {
            monolith.update("""
                INSERT INTO "PARTY_GROUP" ("PARTY_ID","GROUP_NAME")
                VALUES (?,?)
                ON CONFLICT ("PARTY_ID") DO UPDATE
                  SET "GROUP_NAME" = EXCLUDED."GROUP_NAME"
                """, partyId, groupName);
        }

        monolith.update("DELETE FROM \"PARTY_ROLE\" WHERE \"PARTY_ID\" = ?", partyId);
        for (String role : roles) {
            monolith.update("""
                INSERT INTO "PARTY_ROLE" ("PARTY_ID","ROLE_TYPE_ID") VALUES (?,?)
                ON CONFLICT DO NOTHING
                """, partyId, role);
        }
    }
}
