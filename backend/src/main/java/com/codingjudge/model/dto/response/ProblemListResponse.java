package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.Problem;

public class ProblemListResponse {

    private Long id;
    private String slug;
    private String title;
    private String difficulty;
    private String weekLabel;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;

    public static ProblemListResponse from(Problem problem) {
        ProblemListResponse response = new ProblemListResponse();
        response.id = problem.getId();
        response.slug = problem.getSlug();
        response.title = problem.getTitle();
        response.difficulty = problem.getDifficulty().name();
        response.weekLabel = problem.getWeekLabel();
        response.timeLimitMs = problem.getTimeLimitMs();
        response.memoryLimitMb = problem.getMemoryLimitMb();
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
}
