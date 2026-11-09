package gtshop.service;

import gtshop.model.UserProfile;

public interface UserService {
    /** Utilizatorul autentificat; arunca 401 daca cererea este anonima. Nu exista utilizator implicit. */
    UserProfile getCurrentUser();

    UserProfile getUserById(String id);

}
