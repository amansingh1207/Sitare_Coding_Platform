package com.codingjudge.model.dto.response;

import com.codingjudge.judge.JudgeEngine.TestRunSummary;

import java.util.List;

/**
 * Result of running code against a problem's sample test cases.
 *
 * No Submission record is created for a run; this is a scratch execution used
 * while a student is still iterating on their solution.
 */
public class RunResultResponse {

    private String status;
    private List<RunTestResultResponse> testResults;
    private Integer totalRuntimeMs;
    private Integer totalMemoryUsedKb;

    public static RunResultResponse from(TestRunSummary summary) {
        RunResultResponse response = new RunResultResponse();
        response.status = summary.status().name();
        response.testResults = summary.outcomes().stream()
                .map(outcome -> {
                    RunTestResultResponse result = new RunTestResultResponse();
                    result.setTestCaseId(outcome.testCase().getId());
                    result.setStatus(outcome.status().name());
                    result.setActualOutput(outcome.actualOutput());
                    result.setExpectedOutput(outcome.testCase().getExpectedOutput());
                    result.setRuntimeMs((int) Math.min(outcome.runtimeMs(), Integer.MAX_VALUE));
                    result.setMemoryUsedKb((int) Math.min(outcome.memoryUsedKb(), Integer.MAX_VALUE));
                    return result;
                })
                .toList();
        response.totalRuntimeMs = (int) Math.min(summary.maxRuntimeMs(), Integer.MAX_VALUE);
        response.totalMemoryUsedKb = (int) Math.min(summary.maxMemoryKb(), Integer.MAX_VALUE);
        return response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<RunTestResultResponse> getTestResults() {
        return testResults;
    }

    public void setTestResults(List<RunTestResultResponse> testResults) {
        this.testResults = testResults;
    }

    public Integer getTotalRuntimeMs() {
        return totalRuntimeMs;
    }

    public void setTotalRuntimeMs(Integer totalRuntimeMs) {
        this.totalRuntimeMs = totalRuntimeMs;
    }

    public Integer getTotalMemoryUsedKb() {
        return totalMemoryUsedKb;
    }

    public void setTotalMemoryUsedKb(Integer totalMemoryUsedKb) {
        this.totalMemoryUsedKb = totalMemoryUsedKb;
    }
}