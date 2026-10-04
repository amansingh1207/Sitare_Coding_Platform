package com.codingjudge.model.dto.response;

import com.codingjudge.judge.ExecutionResult;
import com.codingjudge.model.enums.SubmissionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code unsupported} flag is the contract the editor's custom-input tab
 * relies on to show guidance instead of a crash verdict.
 */
class CustomRunResponseTest {

    @Test
    void unsupportedResultSetsFlag() {
        CustomRunResponse response = CustomRunResponse.from(
                ExecutionResult.unsupported("Custom input runs are not supported."),
                SubmissionStatus.RUNTIME_ERROR);

        assertThat(response.isUnsupported()).isTrue();
        assertThat(response.getStatus()).isEqualTo("RUNTIME_ERROR");
        assertThat(response.getError()).contains("Custom input");
    }

    @Test
    void ordinaryResultsLeaveFlagClear() {
        CustomRunResponse response = CustomRunResponse.from(
                ExecutionResult.success("7", 42, 1024),
                SubmissionStatus.ACCEPTED);

        assertThat(response.isUnsupported()).isFalse();
        assertThat(response.getOutput()).isEqualTo("7");
    }
}
