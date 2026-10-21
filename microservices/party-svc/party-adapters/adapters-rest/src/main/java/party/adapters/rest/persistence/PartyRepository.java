package party.adapters.rest.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<PartyEntity, String> {

    @Query("select distinct p from PartyEntity p join p.roles r where r = :role")
    Page<PartyEntity> findByRole(@Param("role") String role, Pageable pageable);
}
