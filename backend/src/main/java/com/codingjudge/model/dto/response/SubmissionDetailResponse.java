package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.Submission;

import java.util.List;

public class SubmissionDetailResponse {

    private Long id;
    private ProblemSummary problem;
    private String language;
    private String status;
    private Integer runtimeMs;
    private Integer memoryUsedKb;
    private String submittedAt;
    private String judgedAt;
    private String sourceCode;
    private List<SubmissionTestResultResponse> testResults;
    /**
     * 1-based place in the judging queue, present only while PENDING.
     * Null once judging starts or finishes — and always null from older
     * backends, which the frontend tolerates.
     */
    private Integer queuePosition;

    public static SubmissionDetailResponse from(Submission submission,
                                                List<SubmissionTestResultResponse> results) {
        return from(submission, results, null);
    }

    public static SubmissionDetailResponse from(Submission submission,
                                                List<SubmissionTestResultResponse> results,
                                                Integer queuePosition) {
        SubmissionDetailResponse response = new SubmissionDetailResponse();
        response.id = submission.getId();
        response.problem = new ProblemSummary(
                submission.getProblem().getId(),
                submission.getProblem().getSlug(),
                submission.getProblem().getTitle());
        response.language = submission.getLanguage().name();
        response.status = submission.getStatus().name();
        response.runtimeMs = submission.getRuntimeMs();
        response.memoryUsedKb = submission.getMemoryUsedKb();
        response.submittedAt = submission.getSubmittedAt().toString();
        response.judgedAt = submission.getJudgedAt() != null
                ? submission.getJudgedAt().toString() : null;
        response.sourceCode = submission.getSourceCode();
        response.testResults = results;
        response.queuePosition = queuePosition;
        return response;
    }

    public Long getId() {
        return id;
    }

    public ProblemSummary getProblem() {
        return problem;
    }

    public String getLanguage() {
        return language;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRuntimeMs() {
        return runtimeMs;
    }

    public Integer getMemoryUsedKb() {
        return memoryUsedKb;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public String getJudgedAt() {
        return judgedAt;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public List<SubmissionTestResultResponse> getTestResults() {
        return testResults;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }

    public record ProblemSummary(Long id, String slug, String title) {
    }
}
