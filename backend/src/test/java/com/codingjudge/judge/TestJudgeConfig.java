package com.codingjudge.judge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestJudgeConfig {

    @Bean
    @Primary
    public DockerSandbox dockerSandbox() {
        // Create a mock DockerSandbox that doesn't require Docker
        return new DockerSandbox() {
            @Override
            public CompilationResult compile(String sourceCode, LanguageExecutor executor) {
                // Simple mock: compilation always succeeds for valid Java syntax
                if (sourceCode != null && sourceCode.contains("public class Main")) {
                    return CompilationResult.success("");
                }
                return CompilationResult.failure("Compilation error", 1);
            }

            @Override
            public ExecutionResult execute(String sourceCode, String input,
                                           LanguageExecutor executor,
                                           long timeoutMs, int memoryLimitMb) {
                // Simple mock: return success with input as output
                return ExecutionResult.success(input != null ? input : "", 100, 10240);
            }
        };
    }
}