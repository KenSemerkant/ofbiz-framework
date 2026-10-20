package identity.adapters.rest.security;

import identity.adapters.rest.persistence.UserLoginRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads login credentials and roles from the identity database (P1-F1-S2). */
@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UserLoginRepository userLogins;

    public JpaUserDetailsService(UserLoginRepository userLogins) {
        this.userLogins = userLogins;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userLogins.findById(username)
            .map(u -> User.withUsername(u.getUserLoginId())
                .password(u.getCurrentPassword())
                .roles(u.getRoles().toArray(String[]::new))
                .disabled(!u.isEnabled())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("unknown userLoginId: " + username));
    }
}
