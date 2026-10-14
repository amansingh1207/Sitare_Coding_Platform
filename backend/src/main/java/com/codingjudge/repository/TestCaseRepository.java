package com.codingjudge.repository;

import com.codingjudge.model.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemIdOrderBySortOrderAsc(Long problemId);

    List<TestCase> findByProblemIdAndSampleTrueOrderBySortOrderAsc(Long problemId);

    /**
     * Count-only lookups for the admin problem list. Loading full rows here
     * would pull multi-megabyte TEXT columns into memory just to show a
     * number, which OOMs once big contest packs are imported.
     */
    long countByProblemId(Long problemId);

    long countByProblemIdAndSampleTrue(Long problemId);
}
