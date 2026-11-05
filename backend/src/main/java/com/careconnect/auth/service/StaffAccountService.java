package com.careconnect.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.domain.UserStatus;
import com.careconnect.auth.repository.AppUserRepository;
import com.careconnect.common.error.ApiException;
import com.careconnect.common.error.ErrorCode;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator operations on staff accounts (FR-AUTH-02). Patient accounts are not handled here.
 *
 * <p>Rules: the system always keeps at least one active administrator, and an administrator cannot
 * deactivate or demote themselves. Every change that should end sessions (deactivate, role change,
 * password reset) revokes all refresh tokens of the user as its last step.
 */
@Service
public class StaffAccountService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswords temporaryPasswords;
    private final TokenService tokenService;
    private final Clock clock;

    public StaffAccountService(AppUserRepository users, PasswordEncoder passwordEncoder,
                               TemporaryPasswords temporaryPasswords, TokenService tokenService, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.temporaryPasswords = temporaryPasswords;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public StaffCredential create(String username, String fullName, String email, Role role) {
        requireStaffRole(role);
        String cleanName = username.trim();
        if (users.existsByUsernameIgnoreCase(cleanName)) {
            throw new ApiException(ErrorCode.CONFLICT, "This username is already taken.");
        }
        if (users.existsByEmailIgnoreCase(email.trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "This e-mail address is already in use.");
        }
        String temporary = temporaryPasswords.generate();
        AppUser saved = users.save(AppUser.create(cleanName, passwordEncoder.encode(temporary),
            fullName, email, role, null, true));
        return new StaffCredential(view(saved), temporary);
    }

    @Transactional(readOnly = true)
    public Page<StaffAccountView> list(Pageable pageable) {
        Instant now = clock.instant();
        return users.findByRoleNot(Role.PATIENT, pageable).map(u -> StaffAccountView.from(u, now));
    }

    @Transactional(readOnly = true)
    public StaffAccountView get(long userId) {
        return view(users.findById(userId).filter(u -> u.getRole().isStaff()).orElseThrow(StaffAccountService::notFound));
    }

    /** Changes name, e-mail and role. A role change ends all sessions of that user. */
    @Transactional
    public StaffAccountView update(long actingUserId, long userId, String fullName, String email, Role role) {
        requireStaffRole(role);
        AppUser user = loadStaff(userId);
        boolean roleChanges = user.getRole() != role;
        if (roleChanges) {
            if (userId == actingUserId) {
                throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "You cannot change your own role.");
            }
            if (user.getRole() == Role.ADMIN && user.isActive()) {
                requireAnotherActiveAdmin(userId);
            }
        }
        if (users.existsByEmailIgnoreCaseAndIdNot(email.trim(), userId)) {
            throw new ApiException(ErrorCode.CONFLICT, "This e-mail address is already in use.");
        }
        user.updateProfile(fullName, email);
        if (roleChanges) {
            user.changeStaffRole(role);
        }
        StaffAccountView result = view(user);
        if (roleChanges) {
            tokenService.revokeAllSessions(userId); // last: it clears the persistence context
        }
        return result;
    }

    @Transactional
    public StaffAccountView deactivate(long actingUserId, long userId) {
        if (userId == actingUserId) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION, "You cannot deactivate your own account.");
        }
        AppUser user = loadStaff(userId);
        if (user.isActive()) {
            if (user.getRole() == Role.ADMIN) {
                requireAnotherActiveAdmin(userId);
            }
            user.deactivate();
        }
        StaffAccountView result = view(user);
        tokenService.revokeAllSessions(userId);
        return result;
    }

    @Transactional
    public StaffAccountView activate(long userId) {
        AppUser user = loadStaff(userId);
        user.activate();
        return view(user);
    }

    /** Ends a lockout early, for a user who is locked out of their account. */
    @Transactional
    public StaffAccountView unlock(long userId) {
        AppUser user = loadStaff(userId);
        user.clearLock();
        return view(user);
    }

    /** Sets a new temporary password and ends all sessions. The user must change it at next login. */
    @Transactional
    public StaffCredential resetPassword(long userId) {
        AppUser user = loadStaff(userId);
        String temporary = temporaryPasswords.generate();
        user.replacePassword(passwordEncoder.encode(temporary), true);
        StaffAccountView result = view(user);
        tokenService.revokeAllSessions(userId);
        return new StaffCredential(result, temporary);
    }

    private StaffAccountView view(AppUser user) {
        return StaffAccountView.from(user, clock.instant());
    }

    /**
     * Loads and locks a staff user. When the user is an administrator, all active administrators
     * are locked first (in id order), so two changes cannot both remove the last administrator.
     */
    private AppUser loadStaff(long userId) {
        Optional<AppUser> peek = users.findById(userId);
        if (peek.isPresent() && peek.get().getRole() == Role.ADMIN) {
            users.findByRoleAndStatusOrderById(Role.ADMIN, UserStatus.ACTIVE);
        }
        return users.findWithLockById(userId).filter(u -> u.getRole().isStaff())
            .orElseThrow(StaffAccountService::notFound);
    }

    private void requireAnotherActiveAdmin(long excludedUserId) {
        List<AppUser> admins = users.findByRoleAndStatusOrderById(Role.ADMIN, UserStatus.ACTIVE);
        boolean another = admins.stream().anyMatch(a -> !a.getId().equals(excludedUserId));
        if (!another) {
            throw new ApiException(ErrorCode.BUSINESS_RULE_VIOLATION,
                "At least one active administrator must remain.");
        }
    }

    private static void requireStaffRole(Role role) {
        if (role == null || !role.isStaff()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Role must be a staff role.");
        }
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Staff account not found.");
    }
}
