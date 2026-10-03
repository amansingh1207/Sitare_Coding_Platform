package com.codingjudge.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code execution.provider=judge0} swaps the active
 * {@link CodeExecutionService} to Judge0 and deactivates Docker, without
 * touching any existing wiring.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {Judge0ExecutionService.class, DockerSandbox.class})
@TestPropertySource(properties = "execution.provider=judge0")
class Judge0ProviderWiringTest {

    @Autowired
    private CodeExecutionService executionService;

    @Autowired(required = false)
    private DockerSandbox dockerSandbox;

    @Test
    void judge0IsTheActiveProvider() {
        assertThat(executionService).isInstanceOf(Judge0ExecutionService.class);
    }

    @Test
    void dockerIsDeactivated() {
        assertThat(dockerSandbox).isNull();
    }
}
