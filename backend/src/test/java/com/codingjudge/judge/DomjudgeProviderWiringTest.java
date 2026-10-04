package com.codingjudge.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code execution.provider=domjudge} swaps the active
 * {@link CodeExecutionService} to DOMjudge and deactivates Docker, without
 * touching any existing wiring. Dummy credentials: construction performs no
 * network I/O (languages resolve lazily on first execution).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DomjudgeExecutionService.class, DockerSandbox.class})
@TestPropertySource(properties = {
        "execution.provider=domjudge",
        "domjudge.base-url=http://domjudge.invalid",
        "domjudge.contest=demo",
        "domjudge.user=worker",
        "domjudge.password=secret"
})
class DomjudgeProviderWiringTest {

    @Autowired
    private CodeExecutionService executionService;

    @Autowired(required = false)
    private DockerSandbox dockerSandbox;

    @Test
    void domjudgeIsTheActiveProvider() {
        assertThat(executionService).isInstanceOf(DomjudgeExecutionService.class);
    }

    @Test
    void dockerIsDeactivated() {
        assertThat(dockerSandbox).isNull();
    }
}
