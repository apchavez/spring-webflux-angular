package com.apchavez.products.infrastructure.auth;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DemoUserStoreTest {

    private final DemoUserStore store = new DemoUserStore();

    @Test
    void authenticate_shouldReturnRoles_whenAdminCredentialsAreCorrect() {
        assertThat(store.authenticate("admin", "admin123")).contains(Set.of("ADMIN", "USER"));
    }

    @Test
    void authenticate_shouldReturnRoles_whenUserCredentialsAreCorrect() {
        assertThat(store.authenticate("user", "user123")).contains(Set.of("USER"));
    }

    @Test
    void authenticate_shouldReturnEmpty_whenPasswordIsWrong() {
        assertThat(store.authenticate("admin", "wrong-password")).isEqualTo(Optional.empty());
    }

    @Test
    void authenticate_shouldReturnEmpty_whenUsernameDoesNotExist() {
        assertThat(store.authenticate("nobody", "whatever")).isEqualTo(Optional.empty());
    }
}
