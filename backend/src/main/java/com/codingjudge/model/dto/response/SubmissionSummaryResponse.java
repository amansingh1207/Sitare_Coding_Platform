package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.Submission;

public class SubmissionSummaryResponse {

    private Long id;
    private SubmissionDetailResponse.ProblemSummary problem;
    private String language;
    private String status;
    private Integer runtimeMs;
    private Integer memoryUsedKb;
    private String submittedAt;

    public static SubmissionSummaryResponse from(Submission submission) {
        SubmissionSummaryResponse response = new SubmissionSummaryResponse();
        response.id = submission.getId();
        response.problem = new SubmissionDetailResponse.ProblemSummary(
                submission.getProblem().getId(),
                submission.getProblem().getSlug(),
                submission.getProblem().getTitle());
        response.language = submission.getLanguage().name();
        response.status = submission.getStatus().name();
        response.runtimeMs = submission.getRuntimeMs();
        response.memoryUsedKb = submission.getMemoryUsedKb();
        response.submittedAt = submission.getSubmittedAt().toString();
        return response;
    }

    public Long getId() {
        return id;
    }

    public SubmissionDetailResponse.ProblemSummary getProblem() {
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
}
