package dev.knalis.trajectaapi.config;

import dev.knalis.trajectaapi.config.props.BaseSeedProperties;
import dev.knalis.trajectaapi.model.user.Role;
import dev.knalis.trajectaapi.model.user.User;
import dev.knalis.trajectaapi.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Order(2)
@Component
@Slf4j
@ConditionalOnProperty(name = "application.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BaseSeedProperties baseSeedProperties;

    @Override
    public void run(String... args) {
        if (!baseSeedProperties.isEnabled()) return;

        seedOwner();
        seedAdmin();
    }

    private void seedOwner() {

        final var username = baseSeedProperties.getUsername();
        final var password = baseSeedProperties.getPassword();
        final var mail = baseSeedProperties.getMail();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("Seed is enabled but username/password is empty. Skipping owner creation.");
            return;
        }

        if (userRepository.existsByUsername(username)) {
            log.info("Seed owner already exists for username '{}'. Skipping.", username);
            return;
        }

        if (userRepository.existsByEmail(mail)) {
            log.warn("Cannot create seed owner: email '{}' is already in use.", mail);
            return;
        }

        User owner = new User();
        owner.setUsername(username);
        owner.setPassword(passwordEncoder.encode(password));
        owner.setName("System Owner");
        owner.setEmail(mail);
        owner.setRole(Role.OWNER);

        userRepository.save(owner);
        log.info("Seed owner user created successfully for username '{}'.", username);
    }

    private void seedAdmin() {
        if (!baseSeedProperties.isAdminEnabled()) {
            return;
        }

        final var username = baseSeedProperties.getAdminUsername();
        final var password = baseSeedProperties.getAdminPassword();
        final var mail = baseSeedProperties.getAdminMail();

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("Admin seed is enabled but username/password is empty. Skipping admin creation.");
            return;
        }

        if (userRepository.existsByUsername(username)) {
            log.info("Seed admin already exists for username '{}'. Skipping.", username);
            return;
        }

        if (userRepository.existsByEmail(mail)) {
            log.warn("Cannot create seed admin: email '{}' is already in use.", mail);
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setName("System Admin");
        admin.setEmail(mail);
        admin.setRole(Role.ADMIN);

        userRepository.save(admin);
        log.info("Seed admin user created successfully for username '{}'.", username);
    }
}
