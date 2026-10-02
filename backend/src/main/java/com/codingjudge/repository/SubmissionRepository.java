package com.codingjudge.repository;

import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
