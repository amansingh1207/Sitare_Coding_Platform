package com.codingjudge.model.dto.response;

import java.util.List;

public class ImportPackResponse {

    private List<ImportedProblemSummary> problems;
    private int totalProblems;
    private int totalSamples;
    private int totalHidden;

    public ImportPackResponse(List<ImportedProblemSummary> problems) {
        this.problems = problems;
        this.totalProblems = problems.size();
        this.totalSamples = problems.stream().mapToInt(ImportedProblemSummary::getSampleCount).sum();
        this.totalHidden = problems.stream().mapToInt(ImportedProblemSummary::getHiddenCount).sum();
    }

    public List<ImportedProblemSummary> getProblems() {
        return problems;
    }

    public int getTotalProblems() {
        return totalProblems;
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    public int getTotalHidden() {
        return totalHidden;
    }
}
