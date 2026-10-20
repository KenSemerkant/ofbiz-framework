package audit.adapters.rest;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps monthly audit_events partitions alive without pg_partman: on startup
 * (and before each insert) it checks the pg catalog and creates the needed
 * monthly partition with native SQL if missing.
 */
@Component
public class PartitionMaintainer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public PartitionMaintainer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        ensure(current);
        ensure(current.plusMonths(1));
    }

    /** Make sure the partition covering the event's business time exists. */
    void ensureFor(Instant occurredAt) {
        ensure(YearMonth.from(occurredAt.atZone(ZoneOffset.UTC)));
    }

    private void ensure(YearMonth month) {
        String partition = partitionName(month);
        Integer existing = jdbc.queryForObject(
            "select count(*) from pg_inherits i"
                + " join pg_class c on c.oid = i.inhrelid"
                + " join pg_class p on p.oid = i.inhparent"
                + " where p.relname = 'audit_events' and c.relname = ?",
            Integer.class, partition);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.execute("create table if not exists " + partition
            + " partition of audit_events for values from ('" + month.atDay(1)
            + "') to ('" + month.plusMonths(1).atDay(1) + "')");
    }

    static String partitionName(YearMonth month) {
        return "audit_events_y" + month.getYear() + "m" + month.getMonthValue();
    }
}
