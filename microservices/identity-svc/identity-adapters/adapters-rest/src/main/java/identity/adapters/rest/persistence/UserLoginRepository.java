package identity.adapters.rest.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLoginRepository extends JpaRepository<UserLoginEntity, String> {}
