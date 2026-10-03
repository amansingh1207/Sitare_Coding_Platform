package com.codingjudge.repository;

import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUserIdOrderBySubmittedAtDesc(Long userId);

    List<Submission> findByUserIdAndProblemIdOrderBySubmittedAtDesc(Long userId, Long problemId);

    List<Submission> findByProblemId(Long problemId);

    List<Submission> findByUserIdAndStatus(Long userId, SubmissionStatus status);

    /** IDs of problems the user has solved (at least one ACCEPTED submission). */
    @Query("SELECT DISTINCT s.problem.id FROM Submission s "
            + "WHERE s.user.id = :userId AND s.status = :status")
    List<Long> findSolvedProblemIds(@Param("userId") Long userId,
                                    @Param("status") SubmissionStatus status);

    List<Submission> findByUserIdAndLanguage(Long userId, Language language);

    @EntityGraph(attributePaths = "problem")
    @Query("SELECT s FROM Submission s "
            + "WHERE s.user.id = :userId "
            + "AND (:problemId IS NULL OR s.problem.id = :problemId) "
            + "AND (:status IS NULL OR s.status = :status) "
            + "AND (:language IS NULL OR s.language = :language) "
            + "ORDER BY s.submittedAt DESC")
    Page<Submission> searchForUser(@Param("userId") Long userId,
                                   @Param("problemId") Long problemId,
                                   @Param("status") SubmissionStatus status,
                                   @Param("language") Language language,
                                   Pageable pageable);

    // ---- Durable submission queue (Phase 2): PENDING rows are the queue. ----

    /** Oldest queued submission ids, for the worker poller. Portable JPQL. */
    @Query("SELECT s.id FROM Submission s WHERE s.status = :status ORDER BY s.submittedAt ASC")
    List<Long> findIdsByStatusOrderBySubmittedAtAsc(@Param("status") SubmissionStatus status,
                                                   Pageable pageable);

    /**
     * Atomically claim one queued row. Returns 1 when this worker won the
     * row, 0 when another worker (or restart recovery) got there first.
     * Single-statement atomicity is what prevents duplicate processing.
     */
    @Modifying
    @Query("UPDATE Submission s SET s.status = :claimed "
            + "WHERE s.id = :id AND s.status = :expected")
    int claimQueued(@Param("id") Long id,
                    @Param("expected") SubmissionStatus expected,
                    @Param("claimed") SubmissionStatus claimed);

    /** Crash recovery: rows left JUDGING by a dead instance go back to PENDING. */
    @Modifying
    @Query("UPDATE Submission s SET s.status = :pending WHERE s.status = :judging")
    int resetJudgingToPending(@Param("judging") SubmissionStatus judging,
                             @Param("pending") SubmissionStatus pending);

    long countByStatus(SubmissionStatus status);
}
