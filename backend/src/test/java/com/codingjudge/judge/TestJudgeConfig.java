package com.codingjudge.judge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestJudgeConfig {

    /**
     * A DockerSandbox stand-in so tests never need a Docker daemon.
     *
     * Source with no marker is treated as a correct "add two integers" solution,
     * which is what the flow fixture's problem asks for, so ACCEPTED is reachable
     * without a real toolchain. A marker comment in the source forces a specific
     * failure so tests can drive every branch of the verdict mapping:
     * CE (compilation error), RE (runtime error), TLE, OOM and WRONG.
     */
    @Bean
    @Primary
    public DockerSandbox dockerSandbox() {
        return new DockerSandbox() {
            @Override
            public CompilationResult compile(String sourceCode, LanguageExecutor executor) {
                return CompilationResult.success("");
            }

            @Override
            public ExecutionResult execute(String sourceCode, String input,
                                           LanguageExecutor executor,
                                           long timeoutMs, int memoryLimitMb) {
                String source = sourceCode == null ? "" : sourceCode;
                if (source.contains("/*CE*/")) {
                    return ExecutionResult.compilationError(
                            "Main.java:3: error: ';' expected", 1);
                }
                if (source.contains("/*TLE*/")) {
                    return ExecutionResult.timeout();
                }
                if (source.contains("/*OOM*/")) {
                    return ExecutionResult.oomKilledResult();
                }
                if (source.contains("/*RE*/")) {
                    return ExecutionResult.error(
                            "Exception in thread \"main\" java.lang.NullPointerException", 1);
                }
                if (source.contains("/*WRONG*/")) {
                    return ExecutionResult.success("definitely not the answer", 100, 10240);
                }
                return ExecutionResult.success(solveSum(input), 100, 10240);
            }

            /** Stands in for the problem's reference solution: sum the integers on stdin. */
            private String solveSum(String input) {
                try {
                    long sum = 0;
                    for (String token : input.trim().split("\\s+")) {
                        if (!token.isEmpty()) {
                            sum += Long.parseLong(token);
                        }
                    }
                    return Long.toString(sum);
                } catch (NumberFormatException e) {
                    return input != null ? input : "";
                }
            }
        };
    }
}