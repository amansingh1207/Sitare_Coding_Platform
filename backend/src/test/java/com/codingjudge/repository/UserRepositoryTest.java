package com.codingjudge.repository;

import com.codingjudge.model.entity.User;
import com.codingjudge.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User newUser(String email, String username) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash("$2a$12$hashedpasswordplaceholder");
        user.setFullName("Test User");
        user.setRole(Role.STUDENT);
        return user;
    }

    @Test
    void saveAndFindById() {
        User saved = userRepository.save(newUser("a@uni.edu", "user_a"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void findByEmail() {
        userRepository.save(newUser("b@uni.edu", "user_b"));

        Optional<User> found = userRepository.findByEmail("b@uni.edu");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("user_b");
    }

    @Test
    void findByUsername() {
        userRepository.save(newUser("c@uni.edu", "user_c"));

        Optional<User> found = userRepository.findByUsername("user_c");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("c@uni.edu");
    }

    @Test
    void existsChecks() {
        userRepository.save(newUser("d@uni.edu", "user_d"));

        assertThat(userRepository.existsByEmail("d@uni.edu")).isTrue();
        assertThat(userRepository.existsByUsername("user_d")).isTrue();
        assertThat(userRepository.existsByEmail("missing@uni.edu")).isFalse();
    }

    @Test
    void duplicateEmailRejected() {
        userRepository.save(newUser("e@uni.edu", "user_e1"));

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(newUser("e@uni.edu", "user_e2"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateUsernameRejected() {
        userRepository.save(newUser("f1@uni.edu", "user_f"));

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(newUser("f2@uni.edu", "user_f"));
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
