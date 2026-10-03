package com.codingjudge.judge;

import com.codingjudge.config.DockerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the default wiring selects Docker without any configuration:
 * {@code execution.provider} defaults to {@code docker} and the Docker
 * sandbox is the active {@link CodeExecutionService}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DockerSandbox.class, DockerConfig.class})
class DockerProviderWiringTest {

    @Autowired
    private CodeExecutionService executionService;

    @Test
    void dockerIsTheDefaultProvider() {
        assertThat(executionService).isInstanceOf(DockerSandbox.class);
    }
}
