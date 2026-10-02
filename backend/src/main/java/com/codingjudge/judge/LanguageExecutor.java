package com.codingjudge.judge;

public interface LanguageExecutor {
    String getLanguageName();
    String getSourceFileName();
    String getCompileCommand(String sourceFile);
    String getExecuteCommand(String className);
    CompilationResult compile(String sourceCode, String workDir);
    ExecutionResult execute(String className, String input, String workDir, long timeoutMs, int memoryLimitMb);
}