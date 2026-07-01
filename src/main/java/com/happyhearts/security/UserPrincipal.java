package com.happyhearts.security;

import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import com.happyhearts.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String password;
    private final Role role;
    private final Language preferredLanguage;
    private final UUID branchId;
    private final boolean active;
    private final boolean passwordChangeRequired;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.password = user.getPassword();
        this.role = user.getRole();
        this.preferredLanguage = user.getPreferredLanguage();
        this.branchId = user.getBranch() != null ? user.getBranch().getId() : null;
        this.active = user.isActive();
        this.passwordChangeRequired = user.isPasswordChangeRequired();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
