package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Server-free unit tests for {@link TioExecutionService}'s
 * TIO-to-{@link ExecutionResult} translation.
 */
@ExtendWith(MockitoExtension.class)
class TioExecutionServiceTest {

    @Mock private TioClient client;
    @Mock private JavaExecutor javaExecutor;
    @Mock private CppExecutor cppExecutor;
    @Mock private PythonExecutor pythonExecutor;

    private TioExecutionService service;

    private static TioClient.RunResult ok(String output) {
        return new TioClient.RunResult(output, "", 0);
    }

    @BeforeEach
    void setUp() {
        service = new TioExecutionService(client);
    }

    @Test
    void javaMapsToJavaJdk() {
        when(client.execute(eq(TioExecutionService.JAVA_LANG),
                anyString(), anyString(), anyList()))
                .thenReturn(ok("42\n"));

        ExecutionResult result = service.execute("class Main{}", "21", javaExecutor);

        assertThat(result.output()).isEqualTo("42\n");
        verify(client).execute(eq(TioExecutionService.JAVA_LANG),
                anyString(), eq("21"), eq(List.of()));
    }

    @Test
    void cppSendsCflags() {
        when(client.execute(eq(TioExecutionService.CPP_LANG),
                anyString(), anyString(), eq(TioExecutionService.CPP_CFLAGS)))
                .thenReturn(ok("7"));

        ExecutionResult result = service.execute("int main(){}", "", cppExecutor);

        assertThat(result.output()).isEqualTo("7");
        verify(client).execute(eq(TioExecutionService.CPP_LANG),
                anyString(), anyString(), eq(TioExecutionService.CPP_CFLAGS));
    }

    @Test
    void pythonMapsToPython3() {
        when(client.execute(eq(TioExecutionService.PYTHON_LANG),
                anyString(), anyString(), anyList()))
                .thenReturn(ok("hi\n"));

        ExecutionResult result = service.execute("print('hi')", "", pythonExecutor);

        assertThat(result.output()).isEqualTo("hi\n");
    }

    @Test
    void compilerErrorTranslatesToCompilationError() {
        when(client.execute(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new TioClient.RunResult("",
                        ".code.tio.cpp:1:19: error: expected ';'", 1));

        ExecutionResult result = service.execute("bad", "", cppExecutor);

        assertThat(result.isCompilationError()).isTrue();
        assertThat(result.error()).contains("error: expected");
    }

    @Test
    void pythonTracebackTranslatesToRuntimeError() {
        when(client.execute(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new TioClient.RunResult("",
                        "Traceback (most recent call last):\nZeroDivisionError: division by zero", 1));

        ExecutionResult result = service.execute("print(1/0)", "", pythonExecutor);

        assertThat(result.isCompilationError()).isFalse();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.error()).contains("ZeroDivisionError");
    }

    @Test
    void killedExitCodeBecomesTimeout() {
        when(client.execute(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(new TioClient.RunResult("", "", 137));

        ExecutionResult result = service.execute("while True: pass", "", pythonExecutor);

        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void clientExceptionsBubbleUp() {
        when(client.execute(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new TioClient.TioException("down"));

        assertThrows(TioClient.TioException.class,
                () -> service.execute("class Main{}", "", javaExecutor));
    }
}
