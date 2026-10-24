package humanres.adapters.rest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import humanres.application.OnboardingSagaStore;
import humanres.application.OnboardingSagaStore.SagaRow;
import tools.jackson.databind.ObjectMapper;

/**
 * Saga state as guarded SQL transitions (order-svc JdbcSagaStore pattern):
 * every marker update carries a WHERE status = <expected> guard, so
 * concurrent retries and the restart rescan cannot double-advance a saga.
 * The steps column is the completed-step log (JSONB array of names).
 */
@Repository
public class JdbcOnboardingSagaStore implements OnboardingSagaStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate db;

    public JdbcOnboardingSagaStore(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void create(String onboardingId, String partyId, String emplPositionId) {
        db.update("insert into onboarding_saga (onboarding_id, party_id, empl_position_id, "
            + "status, steps) values (?, ?, ?, 'PENDING', '[]'::jsonb)",
            onboardingId, partyId, emplPositionId);
    }

    @Override
    public Optional<SagaRow> find(String onboardingId) {
        List<SagaRow> found = db.query(
            "select onboarding_id, party_id, empl_position_id, status, steps, failed_reason "
                + "from onboarding_saga where onboarding_id = ?",
            (rs, i) -> new SagaRow(rs.getString(1), rs.getString(2), rs.getString(3),
                Status.valueOf(rs.getString(4)), deserializeSteps(rs.getString(5)),
                rs.getString(6)),
            onboardingId);
        return found.stream().findFirst();
    }

    @Override
    public boolean markRoleAdded(String onboardingId) {
        return db.update("update onboarding_saga set status = 'ROLE_ADDED', "
            + "steps = steps || '\"ADD_ROLE\"'::jsonb, failed_reason = null, "
            + "updated_at = now() where onboarding_id = ? "
            + "and status in ('PENDING', 'FAILED')", onboardingId) == 1;
    }

    @Override
    public boolean markCompleted(String onboardingId) {
        return db.update("update onboarding_saga set status = 'COMPLETED', "
            + "steps = steps || '\"CREATE_EMPLOYMENT\"'::jsonb, failed_reason = null, "
            + "updated_at = now() where onboarding_id = ? and status = 'ROLE_ADDED'",
            onboardingId) == 1;
    }

    @Override
    public void markFailed(String onboardingId, String reason) {
        db.update("update onboarding_saga set status = 'FAILED', failed_reason = ?, "
            + "updated_at = now() where onboarding_id = ? and status <> 'COMPLETED'",
            reason, onboardingId);
    }

    @Override
    public List<String> findIncompleteIds() {
        return db.queryForList("select onboarding_id from onboarding_saga "
            + "where status in ('PENDING', 'ROLE_ADDED') order by onboarding_id",
            String.class);
    }

    private static List<String> deserializeSteps(String json) {
        return JSON.readValue(json, JSON.getTypeFactory()
            .constructCollectionType(java.util.ArrayList.class, String.class));
    }
}
