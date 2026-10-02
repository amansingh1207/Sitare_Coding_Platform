package com.codingjudge.controller;

import com.codingjudge.model.dto.ApiResponse;
import com.codingjudge.model.dto.response.ProblemDetailResponse;
import com.codingjudge.model.dto.response.ProblemListResponse;
import com.codingjudge.service.ProblemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listProblems(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String week,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<ProblemListResponse> result =
                problemService.listProblems(search, week, difficulty, pageable);

        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<ProblemDetailResponse>> getProblem(
            @PathVariable String slug) {
        ProblemDetailResponse problem = problemService.getProblemBySlug(slug);
        return ResponseEntity.ok(ApiResponse.ok(problem));
    }
}
