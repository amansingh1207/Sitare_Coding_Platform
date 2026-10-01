package com.codingjudge.repository;

import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByUserIdOrderBySubmittedAtDesc(Long userId);

    List<Submission> findByUserIdAndProblemIdOrderBySubmittedAtDesc(Long userId, Long problemId);

    List<Submission> findByProblemId(Long problemId);

    List<Submission> findByUserIdAndStatus(Long userId, SubmissionStatus status);

    List<Submission> findByUserIdAndLanguage(Long userId, Language language);
}
