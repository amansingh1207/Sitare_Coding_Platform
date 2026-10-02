package com.codingjudge.model.dto.response;

import com.codingjudge.judge.ExecutionResult;
import com.codingjudge.model.enums.SubmissionStatus;

/**
 * Result of running code against a caller-supplied stdin.
 *
 * There is no expected output to compare with, so {@code output} is whatever
 * the program printed and {@code status} only reflects that it ran cleanly
 * (ACCEPTED) or how it failed (CE/RE/TLE/MLE).
 */
public class CustomRunResponse {

    private String status;
    private String output;
    private String error;
    private Integer exitCode;
    private Integer runtimeMs;
    private Integer memoryUsedKb;

    public static CustomRunResponse from(ExecutionResult result, SubmissionStatus status) {
        CustomRunResponse response = new CustomRunResponse();
        response.status = status.name();
        response.output = result.output();
        response.error = result.error();
        response.exitCode = result.exitCode();
        response.runtimeMs = (int) Math.min(result.runtimeMs(), Integer.MAX_VALUE);
        response.memoryUsedKb = (int) Math.min(result.memoryUsedKb(), Integer.MAX_VALUE);
        return response;
    }

    public String getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public Integer getRuntimeMs() {
        return runtimeMs;
    }

    public Integer getMemoryUsedKb() {
        return memoryUsedKb;
    }
}
