package party.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<PartyEntity, String> {
}
