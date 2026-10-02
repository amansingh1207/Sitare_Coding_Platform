package com.codingjudge.judge.executor;

import com.codingjudge.judge.LanguageExecutor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Language adapter contracts from docs/JUDGE_DESIGN.md section 7:
 * source filename convention, compile command, execute command.
 * These guard the abstraction so new languages follow the same shape.
 */
class LanguageExecutorContractTest {

    private final JavaExecutor javaExecutor = new JavaExecutor();
    private final CppExecutor cppExecutor = new CppExecutor();
    private final PythonExecutor pythonExecutor = new PythonExecutor();

    @Test
    void java_followsContract() {
        assertLanguage(javaExecutor, "JAVA", "Main.java");
        assertThat(javaExecutor.getCompileCommand("Main.java")).isEqualTo("javac Main.java");
        assertThat(javaExecutor.getExecuteCommand("Main")).isEqualTo("java Main");
    }

    @Test
    void cpp_followsContract() {
        assertLanguage(cppExecutor, "CPP", "main.cpp");
        assertThat(cppExecutor.getCompileCommand("main.cpp"))
                .isEqualTo("g++ -std=c++17 -O2 -o main main.cpp");
        assertThat(cppExecutor.getExecuteCommand("Main")).isEqualTo("./main");
    }

    @Test
    void python_followsContract() {
        assertLanguage(pythonExecutor, "PYTHON", "main.py");
        // Python is interpreted: no compile step.
        assertThat(pythonExecutor.getCompileCommand("main.py")).isEmpty();
        assertThat(pythonExecutor.getExecuteCommand("Main")).isEqualTo("python3 main.py");
    }

    @Test
    void sourceFileNames_areUniquePerLanguage() {
        assertThat(javaExecutor.getSourceFileName())
                .isNotEqualTo(cppExecutor.getSourceFileName());
        assertThat(cppExecutor.getSourceFileName())
                .isNotEqualTo(pythonExecutor.getSourceFileName());
    }

    private void assertLanguage(LanguageExecutor executor, String name, String sourceFile) {
        assertThat(executor.getLanguageName()).isEqualTo(name);
        assertThat(executor.getSourceFileName()).isEqualTo(sourceFile);
    }
}