package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.Problem;

import java.util.List;

public class ProblemDetailResponse {

    private Long id;
    private String slug;
    private String title;
    private String statement;
    private String inputFormat;
    private String outputFormat;
    private String constraints;
    private String difficulty;
    private String weekLabel;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
    private List<SampleTestCaseResponse> sampleTestCases;

    public static ProblemDetailResponse from(Problem problem, List<SampleTestCaseResponse> samples) {
        ProblemDetailResponse response = new ProblemDetailResponse();
        response.id = problem.getId();
        response.slug = problem.getSlug();
        response.title = problem.getTitle();
        response.statement = problem.getStatement();
        response.inputFormat = problem.getInputFormat();
        response.outputFormat = problem.getOutputFormat();
        response.constraints = problem.getConstraints();
        response.difficulty = problem.getDifficulty().name();
        response.weekLabel = problem.getWeekLabel();
        response.timeLimitMs = problem.getTimeLimitMs();
        response.memoryLimitMb = problem.getMemoryLimitMb();
        response.sampleTestCases = samples;
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getStatement() {
        return statement;
    }

    public String getInputFormat() {
        return inputFormat;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public String getConstraints() {
        return constraints;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getWeekLabel() {
        return weekLabel;
    }

    public Integer getTimeLimitMs() {
        return timeLimitMs;
    }

    public Integer getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public List<SampleTestCaseResponse> getSampleTestCases() {
        return sampleTestCases;
    }
}
