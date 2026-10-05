package com.codingjudge.repository;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.enums.Difficulty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Optional<Problem> findBySlug(String slug);

    /**
     * Sample runs execute outside any transaction (so a slow judge never
     * holds a pool connection), therefore the lazy test-case collection
     * must already be loaded when the entity leaves this call.
     */
    @EntityGraph(attributePaths = "testCases")
    @Query("SELECT p FROM Problem p WHERE p.id = :id")
    Optional<Problem> findWithTestCasesById(@Param("id") Long id);

    boolean existsBySlug(String slug);

    List<Problem> findByWeekLabel(String weekLabel);

    List<Problem> findByDifficulty(Difficulty difficulty);

    List<Problem> findByTitleContainingIgnoreCase(String title);

    @Query("SELECT p FROM Problem p "
            + "WHERE (:search = '' OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))) "
            + "AND (:week = '' OR p.weekLabel = :week) "
            + "AND (:difficulty IS NULL OR p.difficulty = :difficulty)")
    Page<Problem> search(@Param("search") String search,
                         @Param("week") String week,
                         @Param("difficulty") Difficulty difficulty,
                         Pageable pageable);
}
