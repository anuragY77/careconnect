package com.careconnect.auth.repository;

import java.util.Optional;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for users. Only the auth module's services use it (ArchUnit enforces this). */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);
}
