#!/usr/bin/env python3
"""P6-GATE: migrate the seeded monolith long-tail dataset (48-seed-p6.sh)
into the five P6 service databases. Deterministic + idempotent (upsert /
on-conflict-do-nothing). Replaces the shell version — kubectl exec arg
arrays avoid the quoting hell of nested shell+SQL strings.

Usage: python3 platform/cdc/49-import-p6.py
"""
import json
import subprocess
import sys

NS = "ofbiz"


def psql(pod: str, deploy: bool, db: str, sql: str, tuples: bool = True) -> str:
    cmd = ["kubectl", "-n", NS, "exec"]
    cmd += ["deploy/" + pod] if deploy else [pod]
    cmd += ["--", "psql", "-U", "ofbiz" if deploy else "admin", "-d", db]
    if tuples:
        cmd += ["-Atc", sql]
    else:
        cmd += ["-q", "-c", sql]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"psql failed: {sql[:120]}\n{r.stderr.strip()[:300]}")
    return r.stdout


def M(sql: str) -> list[list[str]]:
    out = psql("monolith-db", True, "ofbiz", sql)
    return [line.split("|") for line in out.splitlines() if line.strip()]


def S(db: str, sql: str) -> None:
    psql("postgres-0", False, db, sql, tuples=False)


def esc(s: str) -> str:
    return s.replace("'", "''")


def q(s: str) -> str:
    return f"'{esc(s)}'"


def nul(v: str) -> str:
    """Empty string from the pipe-separated export means SQL NULL."""
    return "NULL" if v == "" else q(v)


def date(v: str) -> str:
    return "NULL" if v == "" else q(v) + "::date"


def num(v: str) -> str:
    return "NULL" if v == "" else v + "::numeric"


def bigint(v: str) -> str:
    return "NULL" if v == "" else v + "::bigint"


def main() -> int:
    print("==> [1/5] manufacturing")
    comps: dict[str, list[dict]] = {}
    for pid, cid, qty, scrap in M(
        'SELECT "PRODUCT_ID","COMPONENT_PRODUCT_ID","QUANTITY","SCRAP_PCT" FROM "PRODUCT_BOM" ORDER BY 1,2'
    ):
        comps.setdefault(pid, []).append(
            {"productId": cid, "quantity": qty, "scrapPct": scrap})
    for pid, items in comps.items():
        S("manufacturing", "INSERT INTO boms (product_id, components) VALUES "
          f"({q(pid)}, {q(json.dumps(items))}) "
          "ON CONFLICT (product_id) DO UPDATE SET components = EXCLUDED.components")
    for rid, pid in M('SELECT "ROUTING_ID","PRODUCT_ID" FROM "ROUTING" ORDER BY 1'):
        tasks = [{"seq": int(seq), "name": name, "estimatedDuration": int(dur)}
                 for seq, name, dur in M(
                     f'SELECT "SEQ_ID","TASK_NAME","ESTIMATED_DURATION" FROM "ROUTING_TASK" '
                     f'WHERE "ROUTING_ID"={q(rid)} ORDER BY "SEQ_ID"')]
        S("manufacturing", "INSERT INTO routings (routing_id, product_id, tasks) VALUES "
          f"({q(rid)}, {q(pid)}, {q(json.dumps(tasks))}) "
          "ON CONFLICT (routing_id) DO UPDATE SET tasks = EXCLUDED.tasks")
    for mid, pid, etype, qty, when in M(
        'SELECT "MRP_ID","PRODUCT_ID","EVENT_TYPE","QUANTITY",("EVENT_DATE"::date)::text '
        'FROM "MRP_EVENT" ORDER BY 1'
    ):
        S("manufacturing",
          "INSERT INTO mrp_events (event_id, product_id, event_type, quantity, occurred_at) "
          f"VALUES ({q(f'{mid}-{pid}-{etype}')}, {q(pid)}, {q(etype)}, {num(qty)}, {q(when)}) "
          "ON CONFLICT (event_id) DO NOTHING")

    print("==> [2/5] workeffort")
    for wid, etype, name, parent, status, start, end in M(
        'SELECT "WORK_EFFORT_ID","WORK_EFFORT_TYPE_ID","WORK_EFFORT_NAME",'
        'coalesce("PARENT_WORK_EFFORT_ID",\'\'),"STATUS_ID",'
        'coalesce(("ESTIMATED_START_DATE")::text,\'\'),'
        'coalesce(("ESTIMATED_COMPLETION_DATE")::text,\'\') '
        'FROM "WORK_EFFORT" ORDER BY 1'
    ):
        S("workeffort",
          "INSERT INTO work_efforts (work_effort_id, work_effort_type_id, work_effort_name, "
          "parent_work_effort_id, status_id, estimated_start_date, estimated_completion_date) "
          f"VALUES ({q(wid)}, {q(etype)}, {q(name)}, {nul(parent)}, {q(status)}, "
          f"{date(start)}, {date(end)}) ON CONFLICT (work_effort_id) DO NOTHING")
    for tsid, party, from_d, thru_d, status in M(
        'SELECT "TIMESHEET_ID","PARTY_ID",("FROM_DATE")::text,("THRU_DATE")::text,"STATUS_ID" '
        'FROM "TIMESHEET" ORDER BY 1'
    ):
        entries = [{"workEffortId": we, "hours": hrs, "rateTypeId": rate}
                   for we, hrs, rate in M(
                       f'SELECT "WORK_EFFORT_ID","HOURS","RATE_TYPE_ID" FROM "TIME_ENTRY" '
                       f'WHERE "TIMESHEET_ID"={q(tsid)} ORDER BY "TIME_ENTRY_ID"')]
        S("workeffort",
          "INSERT INTO timesheets (timesheet_id, party_id, from_date, thru_date, status_id, entries) "
          f"VALUES ({q(tsid)}, {q(party)}, {date(from_d)}, {date(thru_d)}, {q(status)}, "
          f"{q(json.dumps(entries))}) ON CONFLICT (timesheet_id) DO NOTHING")

    print("==> [3/5] humanres")
    for pid, ptype, reports, status in M(
        'SELECT "EMPL_POSITION_ID","EMPL_POSITION_TYPE_ID",'
        'coalesce("REPORTS_TO_EMPL_POSITION_ID",\'\'),"STATUS_ID" FROM "EMPL_POSITION" ORDER BY 1'
    ):
        S("humanres", f"INSERT INTO empl_positions VALUES "
          f"({q(pid)}, {q(ptype)}, {nul(reports)}, {q(status)}) "
          "ON CONFLICT (empl_position_id) DO NOTHING")
    for eid, party, pid, from_d, thru_d in M(
        'SELECT "EMPLOYMENT_ID","PARTY_ID","EMPL_POSITION_ID",("FROM_DATE")::text,'
        'coalesce(("THRU_DATE")::text,\'\') FROM "EMPLOYMENT" ORDER BY 1'
    ):
        S("humanres", f"INSERT INTO employments VALUES "
          f"({q(eid)}, {q(party)}, {q(pid)}, {date(from_d)}, {date(thru_d)}) "
          "ON CONFLICT (employment_id) DO NOTHING")

    print("==> [4/5] content (metadata only — object bytes stay in S3 per ADR-008)")
    for cid, ctype, name, parent, status, mime, size in M(
        'SELECT "CONTENT_ID","CONTENT_TYPE_ID","CONTENT_NAME",'
        'coalesce("PARENT_CONTENT_ID",\'\'),"STATUS_ID",'
        'coalesce("MIME_TYPE",\'\'),coalesce(("SIZE_BYTES")::text,\'\') '
        'FROM "CONTENT" ORDER BY 1'
    ):
        S("content",
          "INSERT INTO contents (content_id, content_type_id, content_name, parent_content_id, "
          "status_id, content_type, size_bytes) "
          f"VALUES ({q(cid)}, {q(ctype)}, {q(name)}, {nul(parent)}, {q(status)}, "
          f"{nul(mime)}, {bigint(size)}) ON CONFLICT (content_id) DO NOTHING")

    print("==> [5/5] marketing")
    for cid, name, status, from_d, thru_d in M(
        'SELECT "MARKETING_CAMPAIGN_ID","CAMPAIGN_NAME","STATUS_ID",'
        '("FROM_DATE")::text,("THRU_DATE")::text FROM "MARKETING_CAMPAIGN" ORDER BY 1'
    ):
        S("marketing", f"INSERT INTO marketing_campaigns VALUES "
          f"({q(cid)}, {q(name)}, {q(status)}, {date(from_d)}, {date(thru_d)}) "
          "ON CONFLICT (marketing_campaign_id) DO NOTHING")
    for tcode, camp, desc in M(
        'SELECT "TRACKING_CODE_ID",coalesce("MARKETING_CAMPAIGN_ID",\'\'),'
        'coalesce("DESCRIPTION",\'\') FROM "TRACKING_CODE" ORDER BY 1'
    ):
        S("marketing",
          "INSERT INTO tracking_codes (tracking_code_id, marketing_campaign_id, description, "
          "from_date, thru_date) "
          f"VALUES ({q(tcode)}, {nul(camp)}, {nul(desc)}, '2026-01-01', '2030-12-31') "
          "ON CONFLICT (tracking_code_id) DO NOTHING")
    for oid, desc, stage, prob in M(
        'SELECT "SALES_OPPORTUNITY_ID","DESCRIPTION","OPPORTUNITY_STAGE_ID",'
        '("CLOSE_PROBABILITY")::text FROM "SALES_OPPORTUNITY" ORDER BY 1'
    ):
        S("marketing", f"INSERT INTO sales_opportunities VALUES "
          f"({q(oid)}, {q(desc)}, {q(stage)}, {prob}) "
          "ON CONFLICT (sales_opportunity_id) DO NOTHING")

    print("P6 import done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
