package com.codingjudge.repository;

import com.codingjudge.model.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderBySortOrderAsc(Long problemId);

    List<TestCase> findByProblemIdAndSampleTrueOrderBySortOrderAsc(Long problemId);
}
