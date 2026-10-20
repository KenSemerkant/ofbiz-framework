package identity.application;

import identity.domain.UserLogin;
import java.util.Optional;

/** Persistence port for credential principals (implemented by adapters). */
public interface UserLoginStore {

    void save(UserLogin userLogin);

    Optional<UserLogin> find(String userLoginId);

    boolean exists(String userLoginId);
}
