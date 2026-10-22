package com.careconnect.auth.repository;

import java.util.Optional;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** Persistence for users. Only the auth module's services use it (ArchUnit enforces this). */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    /**
     * Loads the user and locks the row until the transaction ends. Used by login so two
     * parallel wrong-password requests cannot both read the same failed-attempt count.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findWithLockByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);
}
