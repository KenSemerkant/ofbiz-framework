package humanres.application;

/**
 * East-west port for the onboarding saga's first step: grant the EMPLOYEE
 * role on the party (party-svc owns roles). Idempotent — party roles are a
 * set, so a retried grant after a crash is a no-op.
 */
public interface PartyRoleClient {

    /**
     * @throws PartyNotFoundException  party-svc does not know the party
     * @throws IllegalStateException   party-svc is unreachable or refused
     */
    void addEmployeeRole(String partyId);
}
