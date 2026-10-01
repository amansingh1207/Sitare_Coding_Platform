package com.codingjudge.repository;

import com.codingjudge.model.entity.SubmissionTestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, Long> {

    List<SubmissionTestResult> findBySubmissionId(Long submissionId);
}
