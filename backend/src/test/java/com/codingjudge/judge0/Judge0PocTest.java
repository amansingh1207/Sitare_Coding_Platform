package com.codingjudge.judge0;

import com.codingjudge.judge.Judge0Client;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Phase 1 Judge0 proof-of-concept: LIVE tests against a real Judge0 instance.
 *
 * <p>These tests are isolated from the Docker judge, the submission flow, and
 * every controller. They only exercise {@link Judge0Client}.
 *
 * <p>Requires Judge0 running locally (see {@code docker-compose.judge0.yml}):
 * <pre>
 * docker compose -f docker-compose.judge0.yml up -d
 * </pre>
 * If Judge0 is not reachable, every test is SKIPPED (not failed) so the
 * normal suite stays green without a Judge0 dependency.
 *
 * <p>Configuration (env, never hardcoded):
 * <ul>
 *   <li>{@code JUDGE0_BASE_URL} - default {@code http://localhost:2358}</li>
 *   <li>{@code JUDGE0_API_KEY} - optional, sent as {@code X-Auth-Token}</li>
 * </ul>
 */
class Judge0PocTest {

    private static final long POLL_MS = 250;
    private static final long MAX_WAIT_MS = 120_000;

    private static Judge0Client client;
    private static int pythonId;
    private static int javaId;
    private static int cppId;

    @BeforeAll
    static void connect() {
        String baseUrl = System.getenv().getOrDefault("JUDGE0_BASE_URL", "http://localhost:2358");
        String apiKey = System.getenv("JUDGE0_API_KEY");
        String apiHost = System.getenv("JUDGE0_API_HOST");
        Judge0Client probe = new Judge0Client(baseUrl, apiKey, apiHost);
        List<Judge0Client.LanguageInfo> languages;
        try {
            languages = probe.listLanguages();
        } catch (Judge0Client.Judge0Exception e) {
            assumeTrue(false, "Judge0 not reachable at " + baseUrl + " - skipping POC tests");
            return;
        }
        client = probe;
        pythonId = pick(languages, "Python (3");
        javaId = pick(languages, "Java (OpenJDK");
        cppId = pick(languages, "C++ (GCC");
        System.out.println("[Judge0Poc] baseUrl=" + baseUrl
                + " python=" + pythonId + " java=" + javaId + " cpp=" + cppId);
    }

    private static int pick(List<Judge0Client.LanguageInfo> languages, String prefix) {
        return languages.stream()
                .filter(l -> !l.archived() && l.name().startsWith(prefix))
                .mapToInt(Judge0Client.LanguageInfo::id)
                .max()
                .orElseThrow(() -> new IllegalStateException("No language starting with " + prefix));
    }

    private Judge0Client.Judge0Result run(String source, int languageId, String stdin) {
        // Compiled languages get a roomy address-space limit: in this POC env
        // limits are enforced per-process with `-m` (address space, not RSS)
        // and a JVM needs gigabytes of virtual headroom just to start.
        // (Server cap raised locally via MAX_MEMORY_LIMIT; stock default is 512 MB.)
        double memoryKb = languageId == pythonId ? 256_000.0 : 6_000_000.0;
        String token = client.submit(source, languageId, stdin, null, 2.0, memoryKb, true);
        assertNotNull(token);
        assertTrue(!token.isBlank(), "token must be non-blank");
        return client.waitForTerminal(token, POLL_MS, MAX_WAIT_MS);
    }

    @Test
    void pythonHelloWithStdinIsAccepted() {
        Judge0Client.Judge0Result r =
                run("print('hello, ' + input())", pythonId, "world");
        assertEquals(Judge0Client.STATUS_ACCEPTED, r.statusId());
        assertEquals("hello, world\n", r.stdout());
    }

    @Test
    void javaHelloIsAccepted() {
        String source = "public class Main {\n"
                + "  public static void main(String[] args) {\n"
                + "    System.out.println(\"hello java\");\n"
                + "  }\n"
                + "}\n";
        Judge0Client.Judge0Result r = run(source, javaId, null);
        assertEquals(Judge0Client.STATUS_ACCEPTED, r.statusId());
        assertEquals("hello java\n", r.stdout());
    }

    @Test
    void cppSumWithStdinIsAccepted() {
        String source = "#include <bits/stdc++.h>\n"
                + "using namespace std;\n"
                + "int main() {\n"
                + "  long long a, b;\n"
                + "  if (!(cin >> a >> b)) return 0;\n"
                + "  cout << a + b;\n"
                + "  return 0;\n"
                + "}\n";
        Judge0Client.Judge0Result r = run(source, cppId, "40 2");
        assertEquals(Judge0Client.STATUS_ACCEPTED, r.statusId());
        assertEquals("42", r.stdout());
    }

    @Test
    void tokenPollingReachesTerminalState() {
        String token = client.submit("print(1)", pythonId, null, null, 2.0, 256_000.0, true);
        assertTrue(token != null && !token.isBlank());

        // Poll manually to observe intermediate queue states.
        List<Integer> seen = new ArrayList<>();
        Judge0Client.Judge0Result current = null;
        for (int i = 0; i < 200; i++) {
            current = client.get(token);
            if (seen.isEmpty() || seen.get(seen.size() - 1) != current.statusId()) {
                seen.add(current.statusId());
            }
            if (Judge0Client.isTerminal(current.statusId())) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertNotNull(current);
        System.out.println("[Judge0Poc] observed status ids: " + seen);
        assertTrue(Judge0Client.isTerminal(current.statusId()));
        assertEquals(Judge0Client.STATUS_ACCEPTED, current.statusId());
    }

    @Test
    void javaCompilationErrorIsDetected() {
        String source = "public class Main {\n"
                + "  public static void main(String[] args) {\n"
                + "    System.out.println(\"oops\")\n"
                + "  }\n"
                + "}\n";
        Judge0Client.Judge0Result r = run(source, javaId, null);
        assertEquals(Judge0Client.STATUS_COMPILATION_ERROR, r.statusId());
        assertNotNull(r.compileOutput());
        assertTrue(r.compileOutput().toLowerCase().contains("error"),
                "compile_output should describe the error, was: " + r.compileOutput());
    }

    @Test
    void pythonRuntimeErrorIsDetected() {
        Judge0Client.Judge0Result r = run("print(1 // 0)", pythonId, null);
        assertTrue(Judge0Client.isRuntimeError(r.statusId()),
                "expected a runtime-error status (7-12), was: " + r.statusId());
        System.out.println("[Judge0Poc] ZeroDivisionExit status=" + r.statusId()
                + " stderr=" + r.stderr());
    }

    @Test
    void pythonInfiniteLoopHitsTimeLimit() {
        String token = client.submit("while True:\n  pass\n", pythonId, null, null, 1.0, 256_000.0, true);
        Judge0Client.Judge0Result r = client.waitForTerminal(token, POLL_MS, MAX_WAIT_MS);
        assertEquals(Judge0Client.STATUS_TIME_LIMIT_EXCEEDED, r.statusId());
    }

    @Test
    void mismatchedExpectedOutputIsWrongAnswer() {
        String token = client.submit("print('hello')", pythonId, null,
                "something else", 2.0, 256_000.0, true);
        Judge0Client.Judge0Result r = client.waitForTerminal(token, POLL_MS, MAX_WAIT_MS);
        assertEquals(Judge0Client.STATUS_WRONG_ANSWER, r.statusId());
    }

    @Test
    void resultCarriesStdoutStderrTimeAndMemory() {
        Judge0Client.Judge0Result r = run("print('hi')", pythonId, null);
        assertEquals(Judge0Client.STATUS_ACCEPTED, r.statusId());
        assertEquals("hi\n", r.stdout());
        assertNotNull(r.timeSeconds(), "time must be reported");
        assertNotNull(r.memoryKilobytes(), "memory must be reported");
        System.out.println("[Judge0Poc] time=" + r.timeSeconds()
                + "s memory=" + r.memoryKilobytes() + "KB");
    }

    @Test
    void invalidLanguageIdIsAValidationError() {
        try {
            client.submit("print(1)", 999_999, null, null, 2.0, 256_000.0);
        } catch (Judge0Client.Judge0ValidationException e) {
            assertTrue(e.getMessage().contains("999999") || e.getMessage().contains("language"),
                    "unexpected message: " + e.getMessage());
            return;
        }
        throw new AssertionError("expected Judge0ValidationException for language 999999");
    }

    @Test
    void pollingTimeoutRaisesInsteadOfHanging() {
        // NOTE: Judge0 caps cpu_time_limit at 15 s (server default), so the
        // sleeper stays under it; the 3 s client budget still expires first.
        String token = client.submit(
                "import time\ntime.sleep(60)\nprint('done')", pythonId, null, null, 10.0, 256_000.0, true);
        try {
            client.waitForTerminal(token, 200, 3_000);
        } catch (Judge0Client.Judge0TimeoutException e) {
            assertTrue(e.getMessage().contains(token));
            return;
        }
        throw new AssertionError("expected Judge0TimeoutException within 3s");
    }

    @Test
    void unreachableHostRaisesConnectionError() {
        Judge0Client broken = new Judge0Client("http://127.0.0.1:9", null);
        try {
            broken.submit("print(1)", pythonId, null, null, 2.0, 256_000.0);
        } catch (Judge0Client.Judge0ConnectionException e) {
            return;
        }
        throw new AssertionError("expected Judge0ConnectionException for a closed port");
    }
}
