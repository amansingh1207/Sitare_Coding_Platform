package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Server-free unit tests for {@link PistonExecutionService}'s
 * Piston-to-{@link ExecutionResult} translation.
 */
@ExtendWith(MockitoExtension.class)
class PistonExecutionServiceTest {

    @Mock private PistonClient client;
    @Mock private JavaExecutor javaExecutor;
    @Mock private CppExecutor cppExecutor;
    @Mock private PythonExecutor pythonExecutor;

    private PistonExecutionService service;

    @BeforeEach
    void setUp() {
        service = new PistonExecutionService(client);
    }

    @Test
    void javaMapsToJavaLanguageAndVersion() {
        when(javaExecutor.getSourceFileName()).thenReturn("Main.java");
        when(client.execute(eq("java"), eq(PistonExecutionService.JAVA_VERSION),
                eq("Main.java"), anyString(), anyString(), anyLong()))
                .thenReturn(new PistonClient.RunResult("7\n", "", 0, null, null));

        ExecutionResult result = service.execute(
                "class Main{public static void main(String[]a){}}", "", javaExecutor, 2000);

        assertThat(result.output()).isEqualTo("7\n");
        verify(client).execute(eq("java"), eq(PistonExecutionService.JAVA_VERSION), eq("Main.java"),
                anyString(), anyString(), eq(2000L));
    }

    @Test
    void cppMapsToCppLanguageAndVersion() {
        when(cppExecutor.getSourceFileName()).thenReturn("main.cpp");
        when(client.execute(eq("c++"), eq(PistonExecutionService.CPP_VERSION),
                eq("main.cpp"), anyString(), anyString(), anyLong()))
                .thenReturn(new PistonClient.RunResult("ok", "", 0, null, null));

        ExecutionResult result = service.execute(
                "#include<iostream>\nint main(){std::cout<<\"ok\";return 0;}",
                "", cppExecutor, 2000);

        assertThat(result.output()).isEqualTo("ok");
        verify(client).execute(eq("c++"), eq(PistonExecutionService.CPP_VERSION), eq("main.cpp"),
                anyString(), anyString(), eq(2000L));
    }

    @Test
    void pythonMapsToPythonLanguageAndVersion() {
        when(pythonExecutor.getSourceFileName()).thenReturn("main.py");
        when(client.execute(eq("python"), eq(PistonExecutionService.PYTHON_VERSION),
                eq("main.py"), anyString(), anyString(), anyLong()))
                .thenReturn(new PistonClient.RunResult("hi", "", 0, null, null));

        ExecutionResult result = service.execute("print('hi')", "", pythonExecutor, 2000);

        assertThat(result.output()).isEqualTo("hi");
    }

    @Test
    void compileErrorTranslatesToCompilationError() {
        when(javaExecutor.getSourceFileName()).thenReturn("Main.java");
        PistonClient.CompileResult compile =
                new PistonClient.CompileResult("", "Main.java:1: error", 1, null);
        when(client.execute(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(new PistonClient.RunResult("", "", 0, null, compile));

        ExecutionResult result = service.execute("bad", "", javaExecutor, 2000);

        assertThat(result.isCompilationError()).isTrue();
        assertThat(result.error()).contains("Main.java:1: error");
    }

    @Test
    void signalBecomesTimeout() {
        when(pythonExecutor.getSourceFileName()).thenReturn("main.py");
        when(client.execute(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(new PistonClient.RunResult("", "", 137, "SIGKILL", null));

        ExecutionResult result = service.execute("while True: pass", "", pythonExecutor, 2000);

        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void runtimeErrorTranslatesToError() {
        when(pythonExecutor.getSourceFileName()).thenReturn("main.py");
        when(client.execute(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenReturn(new PistonClient.RunResult("", "NameError: x", 1, null, null));

        ExecutionResult result = service.execute("print(x)", "", pythonExecutor, 2000);

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.error()).contains("NameError");
    }

    @Test
    void clientExceptionsBubbleUp() {
        when(javaExecutor.getSourceFileName()).thenReturn("Main.java");
        when(client.execute(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong()))
                .thenThrow(new PistonClient.PistonException("down"));

        assertThrows(PistonClient.PistonException.class,
                () -> service.execute("class Main{}", "", javaExecutor, 2000));
    }
}
