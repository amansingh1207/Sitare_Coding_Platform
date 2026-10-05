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
 * Server-free unit tests for {@link WandboxExecutionService}'s
 * Wandbox-to-{@link ExecutionResult} translation.
 */
@ExtendWith(MockitoExtension.class)
class WandboxExecutionServiceTest {

    @Mock private WandboxClient client;
    @Mock private JavaExecutor javaExecutor;
    @Mock private CppExecutor cppExecutor;
    @Mock private PythonExecutor pythonExecutor;

    private WandboxExecutionService service;

    private static WandboxClient.RunResult ok(String output) {
        return new WandboxClient.RunResult("0", "", "", output, "", 0);
    }

    @BeforeEach
    void setUp() {
        service = new WandboxExecutionService(client);
    }

    @Test
    void javaMapsToPinnedCompiler() {
        when(client.compile(eq(WandboxExecutionService.JAVA_COMPILER),
                anyString(), anyString(), anyString()))
                .thenReturn(ok("7\n"));

        ExecutionResult result = service.execute("class Main{}", "", javaExecutor);

        assertThat(result.output()).isEqualTo("7\n");
        verify(client).compile(
                eq(WandboxExecutionService.JAVA_COMPILER), anyString(), anyString(), eq(""));
    }

    @Test
    void cppSendsRawOptions() {
        when(client.compile(eq(WandboxExecutionService.CPP_COMPILER),
                anyString(), anyString(), eq(WandboxExecutionService.CPP_RAW_OPTIONS)))
                .thenReturn(ok("ok"));

        ExecutionResult result = service.execute("int main(){}", "", cppExecutor);

        assertThat(result.output()).isEqualTo("ok");
        verify(client).compile(eq(WandboxExecutionService.CPP_COMPILER),
                anyString(), anyString(), eq(WandboxExecutionService.CPP_RAW_OPTIONS));
    }

    @Test
    void pythonMapsToPinnedCompiler() {
        when(client.compile(eq(WandboxExecutionService.PYTHON_COMPILER),
                anyString(), anyString(), anyString()))
                .thenReturn(ok("hi"));

        ExecutionResult result = service.execute("print('hi')", "", pythonExecutor);

        assertThat(result.output()).isEqualTo("hi");
    }

    @Test
    void compilerErrorTranslatesToCompilationError() {
        when(client.compile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new WandboxClient.RunResult("1", "", "prog.cc:1:1: error", "", "", -1));

        ExecutionResult result = service.execute("bad", "", cppExecutor);

        assertThat(result.isCompilationError()).isTrue();
        assertThat(result.error()).contains("prog.cc:1:1: error");
    }

    @Test
    void runtimeErrorTranslatesToError() {
        when(client.compile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new WandboxClient.RunResult("0", "", "", "", "NameError: x", 1));

        ExecutionResult result = service.execute("print(x)", "", pythonExecutor);

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.error()).contains("NameError");
    }

    @Test
    void clientExceptionsBubbleUp() {
        when(client.compile(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new WandboxClient.WandboxException("down"));

        assertThrows(WandboxClient.WandboxException.class,
                () -> service.execute("class Main{}", "", javaExecutor));
    }
}
