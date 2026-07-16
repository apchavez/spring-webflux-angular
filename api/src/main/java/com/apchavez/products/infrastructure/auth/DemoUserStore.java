package com.apchavez.products.infrastructure.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Usuarios demo hardcodeados para este proyecto de portafolio — no es un almacén de
 * credenciales real. Un sistema en producción respaldaría esto con un store persistido
 * y hasheado.
 */
@Component
public class DemoUserStore {

    private record DemoUser(String passwordHash, Set<String> roles) {}

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final Map<String, DemoUser> users = Map.of(
            "admin", new DemoUser(encoder.encode("admin123"), Set.of("ADMIN", "USER")),
            "user", new DemoUser(encoder.encode("user123"), Set.of("USER")));

    public Optional<Set<String>> authenticate(String username, String password) {
        DemoUser user = users.get(username);
        if (user == null || !encoder.matches(password, user.passwordHash())) {
            return Optional.empty();
        }
        return Optional.of(user.roles());
    }
}
