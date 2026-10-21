# party-svc schema mapping (CG-2)

133 entities in `monolith/applications/datamodel/entitydef/party-entitymodel.xml`.

## Live slice (owned by party-svc)

| OFBiz entity | party-svc home | Notes |
|---|---|---|
| Party | `party.domain.Party` (parties table) | kind = PERSON \| PARTY_GROUP |
| Person | Person names on the aggregate | first/last name |
| PartyGroup | Group name on the aggregate | |
| PartyRole | roles collection (party_roles) | role events on delta |
| PartyRelationship | relationships collection | domain-only (no table yet; persisted when a consumer needs it) |
| ContactMech / PartyContactMech(+Purpose) | contact_mechs JSON on parties | purposes validated per mech type |

## Historical archive

The remaining ~125 entities (agreements, communication events, mail, surveys,
party attributes/profiles, etc.) are **not migrated**. They remain owned by
the monolith until their owning phase pulls them (per PROJECT-PLAN slicing —
most are cross-context archives, not live party state). At Phase 7 (monolith
retirement) untouched party tables are archived per ADR-012 deletion plan;
nothing is dropped before then. CDC streams only the live slice
(`platform/cdc/party-connector.json`).
