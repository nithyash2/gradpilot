package com.gradpilot.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void emailIsTrimmedAndLowerCased() {
        User user = new User("  Nithya@Example.COM ", "hash");

        assertThat(user.getEmail()).isEqualTo("nithya@example.com");
    }

    @Test
    void timezoneDefaultsToMelbourne() {
        User user = new User("a@example.com", "hash");

        assertThat(user.getTimezone()).isEqualTo("Australia/Melbourne");
    }
}
