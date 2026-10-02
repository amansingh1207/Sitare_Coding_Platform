package com.codingjudge.judge;

public interface LanguageExecutor {
    String getLanguageName();
    String getSourceFileName();
    String getCompileCommand(String sourceFile);
    String getExecuteCommand(String className);
    CompilationResult compile(String sourceCode, String workDir);
    ExecutionResult execute(String className, String input, String workDir, long timeoutMs, int memoryLimitMb);

    /**
     * Whether this language needs a compilation step. Interpreted languages
     * (Python) return false so the judge skips the compile exec entirely;
     * an empty compile command would otherwise produce an invalid exec.
     */
    default boolean requiresCompilation() {
        return true;
    }
}