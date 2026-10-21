package party.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MonolithPartyRepository extends JpaRepository<MonolithPartyEntity, String> {
}
