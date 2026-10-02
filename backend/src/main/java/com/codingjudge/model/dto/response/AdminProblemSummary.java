package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.Problem;

public class AdminProblemSummary {

    private Long id;
    private String slug;
    private String title;
    private String difficulty;
    private String weekLabel;
    private int testCaseCount;
    private int sampleCount;

    public static AdminProblemSummary from(Problem problem, int testCaseCount, int sampleCount) {
        AdminProblemSummary dto = new AdminProblemSummary();
        dto.id = problem.getId();
        dto.slug = problem.getSlug();
        dto.title = problem.getTitle();
        dto.difficulty = problem.getDifficulty().name();
        dto.weekLabel = problem.getWeekLabel();
        dto.testCaseCount = testCaseCount;
        dto.sampleCount = sampleCount;
        return dto;
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

    public int getTestCaseCount() {
        return testCaseCount;
    }

    public int getSampleCount() {
        return sampleCount;
    }
}
