package com.careconnect.auth.service;

import java.util.List;
import java.util.Optional;

import com.careconnect.auth.domain.AppUser;
import com.careconnect.auth.domain.Role;
import com.careconnect.auth.domain.UserStatus;
import com.careconnect.auth.repository.AppUserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderDirectory {

    private final AppUserRepository users;

    public ProviderDirectory(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<ProviderView> find(long userId) {
        return users.findById(userId)
            .filter(user -> user.getRole() == Role.DOCTOR && user.getStatus() == UserStatus.ACTIVE)
            .map(ProviderDirectory::view);
    }

    @Transactional(readOnly = true)
    public List<ProviderView> activeProviders() {
        return users.findByRoleAndStatusOrderByFullNameAsc(Role.DOCTOR, UserStatus.ACTIVE).stream()
            .map(ProviderDirectory::view).toList();
    }

    private static ProviderView view(AppUser user) {
        return new ProviderView(user.getId(), user.getFullName());
    }
}
