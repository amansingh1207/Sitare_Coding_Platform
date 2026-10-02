package com.codingjudge.service;

import com.codingjudge.exception.ResourceNotFoundException;
import com.codingjudge.model.dto.response.ProblemDetailResponse;
import com.codingjudge.model.dto.response.ProblemListResponse;
import com.codingjudge.model.dto.response.SampleTestCaseResponse;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.TestCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;

    public ProblemService(ProblemRepository problemRepository,
                          TestCaseRepository testCaseRepository) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Transactional(readOnly = true)
    public Page<ProblemListResponse> listProblems(String search, String week,
                                                  String difficulty, Pageable pageable) {
        Difficulty difficultyEnum = parseDifficulty(difficulty);
        // Empty strings (not NULL) so Postgres infers varchar for CONCAT/LOWER params.
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : "";
        String normalizedWeek = StringUtils.hasText(week) ? week.trim() : "";
        return problemRepository.search(normalizedSearch, normalizedWeek, difficultyEnum, pageable)
                .map(ProblemListResponse::from);
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblemBySlug(String slug) {
        Problem problem = problemRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found: " + slug));
        List<SampleTestCaseResponse> samples = testCaseRepository
                .findByProblemIdAndSampleTrueOrderBySortOrderAsc(problem.getId())
                .stream()
                .map(SampleTestCaseResponse::from)
                .toList();
        return ProblemDetailResponse.from(problem, samples);
    }

    private Difficulty parseDifficulty(String difficulty) {
        if (!StringUtils.hasText(difficulty)) {
            return null;
        }
        try {
            return Difficulty.valueOf(difficulty.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid difficulty: " + difficulty
                    + ". Allowed values: EASY, MEDIUM, HARD");
        }
    }
}
