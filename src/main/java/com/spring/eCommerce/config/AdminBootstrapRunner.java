package com.spring.eCommerce.config;

import com.spring.eCommerce.entity.AppUser;
import com.spring.eCommerce.entity.Role;
import com.spring.eCommerce.repository.RoleRepo;
import com.spring.eCommerce.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first administrator account from environment-based credentials.
 * Only runs when {@code lulumi.admin.username} and {@code lulumi.admin.password}
 * are both configured (e.g. {@code LULUMI_ADMIN_USERNAME}/{@code LULUMI_ADMIN_PASSWORD}
 * environment variables). Never logs credentials. Safe to keep enabled: if the
 * user already exists nothing happens.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserService userService;
    private final RoleRepo roleRepo;

    @Value("${lulumi.admin.username:}")
    private String adminUsername;

    @Value("${lulumi.admin.password:}")
    private String adminPassword;

    @Value("${lulumi.admin.full-name:Lulumi Admin}")
    private String adminFullName;

    @Override
    public void run(String... args) {
        if (adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (userService.findByUserName(adminUsername) != null) {
            log.info("Admin bootstrap skipped: user already exists.");
            return;
        }
        Role adminRole = roleRepo.findByName("admin")
                .orElseGet(() -> roleRepo.save(new Role(null, "admin")));
        AppUser admin = new AppUser(null, adminFullName, adminUsername, adminPassword, Set.of(adminRole));
        userService.save(admin);
        log.info("Admin bootstrap completed for configured username.");
    }
}
