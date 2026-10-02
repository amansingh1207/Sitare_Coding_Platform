package com.codingjudge.model.dto.response;

public class ImportedProblemSummary {

    private String slug;
    private String title;
    private String status;
    private int sampleCount;
    private int hiddenCount;
    private String message;

    public ImportedProblemSummary(String slug, String title, String status,
                                  int sampleCount, int hiddenCount, String message) {
        this.slug = slug;
        this.title = title;
        this.status = status;
        this.sampleCount = sampleCount;
        this.hiddenCount = hiddenCount;
        this.message = message;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public int getHiddenCount() {
        return hiddenCount;
    }

    public String getMessage() {
        return message;
    }
}
