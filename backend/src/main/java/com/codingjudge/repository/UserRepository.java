package com.codingjudge.repository;

import com.codingjudge.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Presence heartbeat: single-statement touch, no entity load, so the
     * per-minute ping from every active tab stays a millisecond write.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :now WHERE u.id = :id")
    int touchLastSeen(@Param("id") Long id, @Param("now") Instant now);

    long countByLastSeenAtAfter(Instant cutoff);

    long countByCreatedAtAfter(Instant cutoff);
}
