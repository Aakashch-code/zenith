package com.example.zenith.config;

import com.example.zenith.authentication.infrastructure.persistence.UserRepository;
import com.example.zenith.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAccountSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.setup.admin.username}")
    private String adminUsername;

    @Value("${app.setup.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        log.info("Checking and verifying system accounts and Admin...");

        if (!userRepository.existsByUsername(adminUsername)) {
            User admin = new User(
                    null,
                    adminUsername,
                    adminUsername + "@zenith.com",
                    passwordEncoder.encode(adminPassword),
                    "ADMIN"
            );
            userRepository.save(admin);
            log.info("Default Admin account created (Username: {})", adminUsername);
        }

        String insertUsers = """
            INSERT INTO users (id, username, email, password, role) 
            VALUES 
                (1, 'system_fee', 'fee@zenith.com', 'NO_LOGIN', 'SYSTEM'),
                (2, 'system_tax', 'tax@zenith.com', 'NO_LOGIN', 'SYSTEM'),
                (4, 'system_vault', 'vault@zenith.com', 'NO_LOGIN', 'SYSTEM')
            ON CONFLICT (id) DO NOTHING;
            """;

        String insertAccounts = """
            INSERT INTO accounts (id, user_id, balance, currency, status, version, created_at, updated_at) 
            VALUES 
                (1, 1, 0.00, 'INR', 'ACTIVE', 0, NOW(), NOW()),
                (2, 2, 0.00, 'INR', 'ACTIVE', 0, NOW(), NOW()),
                (4, 4, 10000000.00, 'INR', 'ACTIVE', 0, NOW(), NOW()) 
            ON CONFLICT (id) DO NOTHING;
            """;

        String resetUserSeq = "SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 4));";
        String resetAccountSeq = "SELECT setval(pg_get_serial_sequence('accounts', 'id'), GREATEST((SELECT MAX(id) FROM accounts), 4));";

        try {
            jdbcTemplate.execute(insertUsers);
            jdbcTemplate.execute(insertAccounts);
            jdbcTemplate.execute(resetUserSeq);
            jdbcTemplate.execute(resetAccountSeq);
            log.info("System accounts successfully verified and seeded.");
        } catch (Exception e) {
            log.error("Failed to seed system accounts.", e);
        }
    }
}