package com.gradpilot.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.gradpilot.TestcontainersConfiguration;

/**
 * Runs against a real PostgreSQL database with the Flyway migrations applied.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Test
    void savesUserWithGeneratedIdAndTimestamps() {
        User saved = users.saveAndFlush(new User("nithya@example.com", "hash"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        users.saveAndFlush(new User("nithya@example.com", "hash"));

        assertThat(users.findByEmailIgnoreCase("NITHYA@example.com")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("Nithya@Example.com")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("someone@example.com")).isFalse();
    }

    @Test
    void rejectsDuplicateEmail() {
        users.saveAndFlush(new User("nithya@example.com", "hash"));

        assertThatThrownBy(() -> users.saveAndFlush(new User("Nithya@Example.com", "other")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
