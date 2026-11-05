package com.careconnect.auth.repository;

import java.util.List;
import java.util.Optional;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.domain.UserStatus;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    boolean existsByIdAndRole(Long id, Role role);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Every user except those with the given role, for the staff account list. */
    Page<AppUser> findByRoleNot(Role role, Pageable pageable);

    /** Loads one user and locks the row until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findWithLockById(Long id);

    /**
     * Locks every user with the role and status, in id order. Changes that could remove the last
     * active administrator take this lock first, so two of them cannot both succeed at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppUser> findByRoleAndStatusOrderById(Role role, UserStatus status);
}
