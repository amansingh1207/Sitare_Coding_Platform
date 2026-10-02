package com.codingjudge.controller;

import com.codingjudge.model.dto.ApiResponse;
import com.codingjudge.model.dto.request.CustomRunRequest;
import com.codingjudge.model.dto.request.SubmitRequest;
import com.codingjudge.model.dto.response.CustomRunResponse;
import com.codingjudge.model.dto.response.RunResultResponse;
import com.codingjudge.model.dto.response.SubmissionDetailResponse;
import com.codingjudge.model.dto.response.SubmissionRefResponse;
import com.codingjudge.model.dto.response.SubmissionSummaryResponse;
import com.codingjudge.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubmissionRefResponse>> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitRequest request) {
        SubmissionRefResponse ref =
                submissionService.submit(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(ref));
    }

    @PostMapping("/run")
    public ResponseEntity<ApiResponse<RunResultResponse>> runCode(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitRequest request) {
        RunResultResponse result = submissionService.run(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/run-custom")
    public ResponseEntity<ApiResponse<CustomRunResponse>> runCustom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CustomRunRequest request) {
        CustomRunResponse result = submissionService.runCustom(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/solved-ids")
    public ResponseEntity<ApiResponse<List<Long>>> solvedIds(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<Long> ids = submissionService.solvedProblemIds(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(ids));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubmissionDetailResponse>> getSubmission(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        SubmissionDetailResponse submission =
                submissionService.getForUser(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.ok(submission));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listSubmissions(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long problemId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<SubmissionSummaryResponse> result = submissionService.listForUser(
                userDetails.getUsername(), problemId, status, language, pageable);

        Map<String, Object> body = new HashMap<>();
        body.put("content", result.getContent());
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }
}
