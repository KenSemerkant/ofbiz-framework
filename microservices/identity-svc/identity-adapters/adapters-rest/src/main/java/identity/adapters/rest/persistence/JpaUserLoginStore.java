package identity.adapters.rest.persistence;

import identity.application.UserLoginStore;
import identity.domain.UserLogin;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserLoginStore implements UserLoginStore {

    private final UserLoginRepository repository;

    public JpaUserLoginStore(UserLoginRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(UserLogin userLogin) {
        repository.save(new UserLoginEntity(userLogin.userLoginId(), userLogin.partyId(),
            userLogin.currentPassword(), userLogin.enabled(), userLogin.roles()));
    }

    @Override
    public Optional<UserLogin> find(String userLoginId) {
        return repository.findById(userLoginId)
            .map(e -> new UserLogin(e.getUserLoginId(), e.getPartyId(), e.getCurrentPassword(),
                e.isEnabled(), e.getRoles()));
    }

    @Override
    public boolean exists(String userLoginId) {
        return repository.existsById(userLoginId);
    }
}
