package com.happyhearts.bootstrap;

import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import com.happyhearts.model.User;
import com.happyhearts.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init-data:true}")
    private boolean initData;

    @Value("${app.seed.super-admin-email:dididavid129@gmail.com}")
    private String superAdminEmail;

    @Value("${app.seed.super-admin-password:PrinceJocos9}")
    private String superAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (!initData) {
            return;
        }
        String email = superAdminEmail.trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(User::new);
        boolean isNew = user.getId() == null;
        user.setEmail(email);
        // Never overwrite an existing user's password on restart — that made logins fail
        // after the admin changed their password (looked like the password "changed by itself").
        if (isNew) {
            user.setPassword(passwordEncoder.encode(superAdminPassword));
        }
        user.setRole(Role.SUPER_ADMIN);
        user.setPreferredLanguage(Language.EN);
        user.setBranch(null);
        user.setActive(true);
        user.setPasswordChangeRequired(false);
        user.setInitialSetupToken(null);
        user.setInitialSetupTokenExpiresAt(null);
        userRepository.save(user);
        log.info("Super admin seeded/updated: {}", email);
    }
}
