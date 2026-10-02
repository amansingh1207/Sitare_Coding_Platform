package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.Submission;

public class SubmissionRefResponse {

    private Long id;
    private String status;
    private String submittedAt;

    public static SubmissionRefResponse from(Submission submission) {
        SubmissionRefResponse response = new SubmissionRefResponse();
        response.id = submission.getId();
        response.status = submission.getStatus().name();
        response.submittedAt = submission.getSubmittedAt().toString();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }
}
