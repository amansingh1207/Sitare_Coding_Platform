package com.codingjudge.repository;

import com.codingjudge.model.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Id-only, sort-ordered listing for streaming judges: lets the worker
     * walk a huge suite one row at a time without ever materializing the
     * multi-megabyte TEXT columns of the whole suite.
     */
    @Query("SELECT t.id FROM TestCase t WHERE t.problem.id = :problemId ORDER BY t.sortOrder")
    List<Long> findIdsByProblemIdOrderBySortOrderAsc(@Param("problemId") Long problemId);
}
