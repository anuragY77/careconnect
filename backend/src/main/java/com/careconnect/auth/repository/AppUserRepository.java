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

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findWithLockByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    boolean existsByIdAndRole(Long id, Role role);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Page<AppUser> findByRoleNot(Role role, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppUser> findWithLockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AppUser> findByRoleAndStatusOrderById(Role role, UserStatus status);

    List<AppUser> findByRoleAndStatusOrderByFullNameAsc(Role role, UserStatus status);
}
