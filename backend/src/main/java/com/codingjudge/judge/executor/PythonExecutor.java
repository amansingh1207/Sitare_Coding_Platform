package com.codingjudge.judge.executor;

import com.codingjudge.judge.CompilationResult;
import com.codingjudge.judge.ExecutionResult;
import com.codingjudge.judge.LanguageExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class PythonExecutor implements LanguageExecutor {

    private static final String SOURCE_FILE = "main.py";
    private static final List<String> EXECUTE_CMD = List.of("python3", SOURCE_FILE);

    @Override
    public String getLanguageName() {
        return "PYTHON";
    }

    @Override
    public String getSourceFileName() {
        return SOURCE_FILE;
    }

    @Override
    public String getCompileCommand(String sourceFile) {
        // Python is interpreted, no compilation step
        return "";
    }

    @Override
    public boolean requiresCompilation() {
        return false;
    }

    @Override
    public String getExecuteCommand(String className) {
        return "python3 " + SOURCE_FILE;
    }

    @Override
    public CompilationResult compile(String sourceCode, String workDir) {
        // Python is interpreted - write source file and return success
        Path sourcePath = Path.of(workDir, SOURCE_FILE);
        try {
            Files.writeString(sourcePath, sourceCode);
            return CompilationResult.success("Python source written");
        } catch (IOException e) {
            return CompilationResult.failure("Failed to write source file: " + e.getMessage(), -1);
        }
    }

    @Override
    public ExecutionResult execute(String className, String input, String workDir, long timeoutMs, int memoryLimitMb) {
        try {
            ProcessBuilder pb = new ProcessBuilder(EXECUTE_CMD);
            pb.directory(Path.of(workDir).toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            if (input != null && !input.isEmpty()) {
                process.getOutputStream().write(input.getBytes());
            }
            process.getOutputStream().close();

            boolean finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ExecutionResult.timeout();
            }

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return ExecutionResult.error(output, exitCode);
            }

            return ExecutionResult.success(output, 0, 0);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionResult.error("Execution failed: " + e.getMessage(), -1);
        }
    }
}