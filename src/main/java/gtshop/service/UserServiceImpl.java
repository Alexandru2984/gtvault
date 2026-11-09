package gtshop.service;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.security.CurrentUser;
import gtshop.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserServiceImpl(UserRepository userRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getCurrentUser() {
        String id = currentUser.requireId();
        return userRepository.findById(id).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getUserById(String userId) {
        return this.userRepository.findById(userId).orElse(null);
    }
}
