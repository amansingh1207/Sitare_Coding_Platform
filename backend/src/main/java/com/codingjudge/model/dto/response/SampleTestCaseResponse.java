package com.codingjudge.model.dto.response;

import com.codingjudge.model.entity.TestCase;

public class SampleTestCaseResponse {

    private Long id;
    private String inputData;
    private String expectedOutput;

    public static SampleTestCaseResponse from(TestCase testCase) {
        SampleTestCaseResponse response = new SampleTestCaseResponse();
        response.id = testCase.getId();
        response.inputData = testCase.getInputData();
        response.expectedOutput = testCase.getExpectedOutput();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getInputData() {
        return inputData;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }
}
