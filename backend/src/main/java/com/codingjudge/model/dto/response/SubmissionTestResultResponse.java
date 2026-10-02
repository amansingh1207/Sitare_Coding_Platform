package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.SubmissionTestResult;

public class SubmissionTestResultResponse {

    private Long testCaseId;
    private String status;
    private String actualOutput;
    private Integer runtimeMs;
    private Integer memoryUsedKb;

    public static SubmissionTestResultResponse from(SubmissionTestResult result) {
        SubmissionTestResultResponse response = new SubmissionTestResultResponse();
        response.testCaseId = result.getTestCase().getId();
        response.status = result.getStatus().name();
        response.actualOutput = result.getActualOutput();
        response.runtimeMs = result.getRuntimeMs();
        response.memoryUsedKb = result.getMemoryUsedKb();
        return response;
    }

    public Long getTestCaseId() {
        return testCaseId;
    }

    public String getStatus() {
        return status;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public Integer getRuntimeMs() {
        return runtimeMs;
    }

    public Integer getMemoryUsedKb() {
        return memoryUsedKb;
    }
}
