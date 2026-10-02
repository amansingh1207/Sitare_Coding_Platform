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
public class CppExecutor implements LanguageExecutor {

    private static final String SOURCE_FILE = "main.cpp";
    private static final String EXECUTABLE = "main";
    private static final List<String> COMPILE_CMD = List.of("g++", "-std=c++17", "-O2", "-o", EXECUTABLE, SOURCE_FILE);
    private static final List<String> EXECUTE_CMD = List.of("./" + EXECUTABLE);

    @Override
    public String getLanguageName() {
        return "CPP";
    }

    @Override
    public String getSourceFileName() {
        return SOURCE_FILE;
    }

    @Override
    public String getCompileCommand(String sourceFile) {
        return "g++ -std=c++17 -O2 -o " + EXECUTABLE + " " + sourceFile;
    }

    @Override
    public String getExecuteCommand(String className) {
        return "./" + EXECUTABLE;
    }

    @Override
    public CompilationResult compile(String sourceCode, String workDir) {
        Path sourcePath = Path.of(workDir, SOURCE_FILE);
        try {
            Files.writeString(sourcePath, sourceCode);
        } catch (IOException e) {
            return CompilationResult.failure("Failed to write source file: " + e.getMessage(), -1);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(COMPILE_CMD);
            pb.directory(Path.of(workDir).toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return CompilationResult.success(output);
            }
            return CompilationResult.failure(output, exitCode);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompilationResult.failure("Compilation process failed: " + e.getMessage(), -1);
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