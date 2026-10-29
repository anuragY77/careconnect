package com.careconnect.auth.service;

import java.util.Optional;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.repository.AppUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Looks up the signed-in user from the subject of a verified access token. */
@Service
public class CurrentUserService {

    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    /**
     * Returns the user only while the account exists and is active, so a deactivated user is
     * refused here at once instead of after the access token expires.
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> findActive(String subject) {
        long id;
        try {
            id = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return users.findById(id).filter(AppUser::isActive).map(AuthenticatedUser::from);
    }
}
